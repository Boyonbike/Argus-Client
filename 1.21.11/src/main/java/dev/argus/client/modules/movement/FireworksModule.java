package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.MultiPlayerGameModeUseItemEvent;
import dev.argus.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.argus.client.events.SoundEnginePlayEvent;
import dev.argus.client.managers.InventoryStateManager;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.combat.AutoArmorModule;
import dev.argus.client.modules.combat.AutoMaceModule;
import dev.argus.client.modules.exploit.RocketExtenderModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.KeyUtils;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.combat.RaycastUtils;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.entity.SwingAction;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.WorldUtils;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class FireworksModule extends Module {
   public static final AtomicBoolean temporaryElytraSwapActive = new AtomicBoolean(false);
   private static AutoArmorModule autoArmorModule;
   private static AutoMaceModule autoMaceModule;
   private static RocketExtenderModule rocketExtenderModule;
   private static ElytraFlyModule elytraFlyModule;
   public final Setting<Boolean> antiWasteSetting = this.add(new BooleanSetting("AntiWaste", true));
   public final Setting<Boolean> softLagSetting = this.add(new BooleanSetting("SoftLag", false));
   public final Setting<Boolean> fakeRocketSetting = this.add(new BooleanSetting("FakeRocket", false));
   public final Setting<Boolean> boostSetting = this.add(new BooleanSetting("Boost", false).markVisibilityDependency());
   public final Setting<Boolean> withElytraFlySetting = this.add(new BooleanSetting("WithElytraFly", true).dependsOn(this.boostSetting));
   public final Setting<Boolean> grimSetting = this.add(new BooleanSetting("Grim", false).dependsOn(this.boostSetting));
   public final Setting<Boolean> directionalSetting;
   public final Setting<Float> boostFactorSetting;
   public final Setting<Boolean> autoLaunchSetting;
   private final Setting<Boolean> onlyElytraFlySetting;
   private final Setting<Boolean> onlyFirstSetting;
   private final Setting<Boolean> awaitSetting;
   private final Setting<Float> delaySetting;
   public final Setting<Boolean> fastLaunchSetting;
   final Setting<FireworksModule.ModeChoice> modeSetting;
   final Setting<FireworksModule.SwapChoice> swapSetting;
   private final Setting<Integer> ticksSetting;
   private final Setting<Boolean> swapBackSetting;
   private final Setting<Boolean> autoPhaseSetting;
   public final ElapsedTimer rocketUseTimer;
   public final ElapsedTimer fakeRocketTimer;
   private boolean rocketLaunchedDuringFlight;
   private int fastLaunchTicksRemaining;
   private boolean swapBackPending;
   private boolean fastLaunchWasActive;
   public boolean resetRocketAge;
   private int ceilingFlightTicks;
   private int swappedElytraSlot;
   private int fakeRocketLifetimeTicks;
   private int fakeRocketEntityId;

   @Override
   public void onToggle() {
      this.removeFakeRocket();
      ClientContext.tickRateController.releaseMultiplier(this);
   }

   public boolean canFastLaunch(boolean requireAirborne) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      return !PlayerStateUtils.isWearingElytra(player)
         && (!requireAirborne || !player.onGround())
         && this.isToggled()
         && this.fastLaunchSetting.getValue()
         && InventoryUtils.findMenuSlot(Items.ELYTRA) != -1;
   }

   public float getAdditionalBoost(boolean fromRocketEntity) {
      if (!this.isToggled() || !this.boostSetting.getValue()) {
         return 0.0F;
      } else if (!fromRocketEntity && !this.withElytraFlySetting.getValue()) {
         return 0.0F;
      } else {
         return this.isRocketBoostActive() ? this.boostFactorSetting.getValue() : 0.0F;
      }
   }

   public boolean isFastLaunchActive() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (player.onGround()) {
         return false;
      }

      FireworksModule.ModeChoice mode = this.modeSetting.getValue();

      return switch (mode) {
         case MANUAL -> this.fastLaunchTicksRemaining > 0;
         case TOGGLE -> this.fastLaunchSetting.getValue();
      };
   }

   public boolean isGrimBoostEnabled() {
      return this.isToggled() && this.boostSetting.getValue() && this.grimSetting.getValue();
   }

   @EventHandler(priority = 200)
   private void onLocalPlayerPreTickEvent(LocalPlayerPreTickEvent event) {
      long fakeRocketLifetimeMillis = this.fakeRocketLifetimeTicks * 50L;
      if (rocketExtenderModule.isToggled()) {
         fakeRocketLifetimeMillis = (long)(rocketExtenderModule.getTimeoutSeconds() * 1000.0F);
      }

      if (this.fakeRocketTimer.hasElapsed(fakeRocketLifetimeMillis)) {
         this.removeFakeRocket();
      }

      if (PlayerStateUtils.isFallFlying() && this.softLagSetting.getValue() && ClientContext.serverStateManager.hasElapsedSinceSetback(400L)) {
         ClientContext.tickRateController.requestMultiplier(this, 0.8F);
      } else {
         ClientContext.tickRateController.releaseMultiplier(this);
      }

      if (this.swapBackPending) {
         if (!InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 3)) {
            FireworksModule.SwapChoice swapMode = this.swapSetting.getValue();
            if (!swapMode.isSilent() && this.swappedElytraSlot != -1) {
               this.swapSetting.getValue().swapElytra(this.swappedElytraSlot, 6);
               autoArmorModule.skipNextArmorUpdate();
               this.swappedElytraSlot = -1;
            }

            this.swapBackPending = false;
         }

         if (!PlayerStateUtils.isWearingElytra(ClientAccess.argusclientMinecraft.player) && this.swapSetting.getValue().isSilent()) {
            ClientAccess.argusclientMinecraft.player.stopFallFlying();
            this.swapBackPending = false;
         }
      }

      if (this.fastLaunchSetting.getValue()
         && this.modeSetting.getValue() == FireworksModule.ModeChoice.TOGGLE
         && ClientAccess.argusclientMinecraft.player.onGround()
         && this.fastLaunchWasActive) {
         ClientContext.inputOverrides.requestJump(true);
      }

      if (this.isFastLaunchActive() && this.fastLaunchSetting.getValue()) {
         autoArmorModule.skipNextArmorUpdate();
         this.equipElytraAndStartFlight();
         if (this.modeSetting.getValue() == FireworksModule.ModeChoice.MANUAL) {
            this.fastLaunchTicksRemaining--;
         }

         this.fastLaunchWasActive = true;
      } else if (this.fastLaunchWasActive) {
         if (this.swapBackSetting.getValue()) {
            autoArmorModule.skipNextArmorUpdate();
            this.swapBackPending = true;
         }

         this.fastLaunchWasActive = false;
         if (this.modeSetting.getValue() == FireworksModule.ModeChoice.TOGGLE
            && this.fastLaunchSetting.getValue()
            && ClientAccess.argusclientMinecraft.player.onGround()) {
            autoArmorModule.skipNextArmorUpdate();
            this.swapBackPending = false;
         }
      }

      if (this.fastLaunchSetting.getValue() && this.autoPhaseSetting.getValue()) {
         double top = ClientAccess.argusclientMinecraft.player.getBoundingBox().maxY;
         if (Math.floor(top) == Math.floor(top + 0.01) && !ClientAccess.argusclientMinecraft.player.onGround()) {
            this.ceilingFlightTicks++;
         } else {
            this.ceilingFlightTicks = 0;
         }

         this.tryAutoPhase();
         if (!ClientAccess.argusclientMinecraft.player.isFallFlying()
            && EntityUtils.hasFirework(ClientAccess.argusclientMinecraft.player)
            && ClientAccess.argusclientMinecraft.player.getXRot() < 0.0F
            && KeyUtils.isPhysicallyDown(ClientAccess.argusclientMinecraft.options.keyJump)) {
            PacketSender.sendStartFallFlying();
            ClientAccess.argusclientMinecraft.player.startFallFlying();
         }
      }

      boolean toggleLaunchActive = this.modeSetting.getValue() == FireworksModule.ModeChoice.TOGGLE && this.isFastLaunchActive();
      boolean fallFlyingForLaunch = PlayerStateUtils.isFallFlying();
      if (elytraFlyModule.isToggled()
         && elytraFlyModule.isDurabilityBypassActive()
         && elytraFlyModule.modeSetting.getValue() == ElytraFlyModule.ModeChoice.NEWGRIM) {
         fallFlyingForLaunch = true;
      }

      if (!fallFlyingForLaunch || ClientAccess.argusclientMinecraft.player.onGround()) {
         this.rocketUseTimer.clear();
      }

      if (this.autoLaunchSetting.getValue()) {
         if (elytraFlyModule.isToggled() || !this.onlyElytraFlySetting.getValue() || toggleLaunchActive) {
            if (!elytraFlyModule.isToggled() || elytraFlyModule.modeSetting.getValue() != ElytraFlyModule.ModeChoice.BOUNCE) {
               double delaySeconds = this.delaySetting.getValue().floatValue();
               boolean awaitRocket = !this.rocketUseTimer.hasElapsed(delaySeconds, TimeUnit.SECONDS)
                  || rocketExtenderModule.isToggled() && rocketExtenderModule.trackedRocketId != -1 && rocketExtenderModule.rangeAnchor != null;
               if (this.isRocketBoostActive()) {
                  awaitRocket = true;
               }

               if (!awaitRocket) {
                  if (!PlayerStateUtils.isFallFlying()) {
                     this.rocketLaunchedDuringFlight = false;
                  }

                  if (!this.rocketLaunchedDuringFlight || !this.onlyFirstSetting.getValue()) {
                     if (!ClientAccess.argusclientMinecraft.player.onGround()) {
                        if (elytraFlyModule.isToggled()
                           && elytraFlyModule.modeSetting.getValue() == ElytraFlyModule.ModeChoice.NEWGRIM
                           && !elytraFlyModule.infDurabilitySetting.getValue().isDisabled()
                           && !RotationManager.is2b2tMode()) {
                           this.temporarilyEquipElytra();
                        }

                        this.tryUseRocket();
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onSoundEnginePlayEvent(SoundEnginePlayEvent event) {
      if (this.fastLaunchTicksRemaining > 0 && event.getSoundInstance() != null) {
         Identifier identifier = event.getSoundInstance().getIdentifier();
         if (identifier != null && identifier.toString().contains("item.armor.equip")) {
            event.cancel();
         }
      }
   }

   public static Vec3 getGrimBoost(float yaw, float pitch, float previousYaw, float previousPitch, Vec3 movement) {
      Vec3 lookDirection = Vec3.directionFromRotation(pitch, yaw);
      AABB movementBounds = getBoostMovementBounds(yaw, pitch, previousYaw, previousPitch, movement);
      return BoundingBoxUtils.clampToBounds(lookDirection.scale(0.8), movementBounds);
   }

   public static AABB getBoostMovementBounds(float yaw, float pitch, float previousYaw, float previousPitch, Vec3 movement) {
      Vec3 currentLook = Vec3.directionFromRotation(pitch, yaw);
      Vec3 previousLook = Vec3.directionFromRotation(previousPitch, previousYaw);
      float yawFraction = (yaw % 45.0F + 45.0F) % 45.0F / 45.0F;
      double predictionScale = 1.54;
      if (pitch < -35.0F) {
         predictionScale = 1.63;
      } else if (pitch < -15.0F) {
         predictionScale = 1.62;
      } else if (pitch < -10.0F) {
         predictionScale = 1.61;
      }

      if (yawFraction > 0.1 && yawFraction < 0.9) {
         predictionScale -= 0.05;
      }

      double minimumX = Math.max(-predictionScale, (Math.min(-0.0, currentLook.x) + Math.min(-0.0, previousLook.x)) * predictionScale);
      double minimumY = Math.max(-predictionScale, (Math.min(-0.0, currentLook.y) + Math.min(-0.0, previousLook.y)) * predictionScale);
      double minimumZ = Math.max(-predictionScale, (Math.min(-0.0, currentLook.z) + Math.min(-0.0, previousLook.z)) * predictionScale);
      double maximumX = Math.min(predictionScale, (Math.max(0.0, currentLook.x) + Math.max(0.0, previousLook.x)) * predictionScale);
      double maximumY = Math.min(predictionScale, (Math.max(0.0, currentLook.y) + Math.max(0.0, previousLook.y)) * predictionScale);
      double maximumZ = Math.min(predictionScale, (Math.max(0.0, currentLook.z) + Math.max(0.0, previousLook.z)) * predictionScale);
      AABB bounds = new AABB(
         Math.min(0.0, minimumX - movement.x),
         Math.min(0.0, minimumY - movement.y),
         Math.min(0.0, minimumZ - movement.z),
         Math.max(0.0, maximumX - movement.x),
         Math.max(0.0, maximumY - movement.y),
         Math.max(0.0, maximumZ - movement.z)
      );
      return bounds.contract(0.0, 0.0231, 0.0);
   }

   @EventHandler(priority = -100)
   private void onLocalPlayerMoveEvent(LocalPlayerMoveEvent event) {
      float yaw = ClientAccess.argusclientMinecraft.player.getYRot();
      float pitch = ClientAccess.argusclientMinecraft.player.getXRot();
      RotationManager rotationManager = ClientContext.rotationManager;
      if (rotationManager.getRequest() != null) {
         yaw = ClientContext.rotationManager.getRequest().angles()[0];
         pitch = ClientContext.rotationManager.getRequest().angles()[1];
      }

      if (!autoMaceModule.shouldSuppressGrimBoost()
         && PlayerStateUtils.isFallFlying()
         && this.boostSetting.getValue()
         && this.grimSetting.getValue()
         && EntityUtils.hasFirework(ClientAccess.argusclientMinecraft.player)) {
         float[] packetRotations = ClientContext.playerStateTracker.getPacketRotations();
         float previousYaw = packetRotations[0];
         float previousPitch = packetRotations[1];
         Vec3 movement = event.getOriginalVelocity();
         Vec3 boostMovement = getGrimBoost(yaw, pitch, previousYaw, previousPitch, movement);
         Vec3 currentMovement = event.getOriginalVelocity();
         Vec3 boostedMovement = currentMovement.add(boostMovement);
         event.setVelocity(boostedMovement);
      }

      if (PlayerStateUtils.isFallFlying() && this.awaitSetting.getValue() && this.autoLaunchSetting.getValue() && !this.isRocketBoostActive()) {
         Vec3 pausedMovement = new Vec3(0.0, 0.0, 0.0);
         event.setVelocity(pausedMovement);
      }
   }

   @EventHandler
   private void onMultiPlayerGameModeUseItemEvent(MultiPlayerGameModeUseItemEvent event) {
      if (ClientAccess.argusclientMinecraft.player.isHolding(Items.FIREWORK_ROCKET)) {
         Boolean fakeRocketEnabled = this.fakeRocketSetting.getValue();
         if (fakeRocketEnabled && PlayerStateUtils.isFallFlying()) {
            this.removeFakeRocket();
            this.fakeRocketEntityId = -84751;
            LocalPlayer playerForRocketStack = ClientAccess.argusclientMinecraft.player;
            InteractionHand hand = event.getHand();
            ItemStack rocketStack = playerForRocketStack.getItemInHand(hand);
            FireworkRocketEntity fakeRocket = new FireworkRocketEntity(
               ClientAccess.argusclientMinecraft.level, rocketStack, ClientAccess.argusclientMinecraft.player
            );
            int entityId = this.fakeRocketEntityId;
            fakeRocket.setId(entityId);
            int flightDuration = 1;
            DataComponentType<Fireworks> fireworksType = DataComponents.FIREWORKS;
            Fireworks fireworksComponent = rocketStack.get(fireworksType);
            if (fireworksComponent != null) {
               flightDuration += fireworksComponent.flightDuration();
            }

            int lifetimeTicks = 10 * flightDuration;
            lifetimeTicks += ThreadLocalRandom.current().nextInt(6);
            this.fakeRocketLifetimeTicks = lifetimeTicks + ThreadLocalRandom.current().nextInt(7);
            ClientAccess.argusclientMinecraft.level.addEntity(fakeRocket);
            this.fakeRocketTimer.reset();
            event.cancel();
         }

         Boolean fastLaunchEnabled = this.fastLaunchSetting.getValue();
         if (fastLaunchEnabled
            && PlayerStateUtils.isLocalWearingElytra()
            && !PlayerStateUtils.isFallFlying()
            && !ClientAccess.argusclientMinecraft.player.onGround()) {
            PacketSender.sendStartFallFlying();
            ClientAccess.argusclientMinecraft.player.startFallFlying();
         }

         if (this.canFastLaunch(true)) {
            this.equipElytraAndStartFlight();
            Integer launchTicks = this.ticksSetting.getValue();
            this.fastLaunchTicksRemaining = launchTicks;
            this.resetRocketAge = true;
         } else if (this.fastLaunchTicksRemaining > 0) {
            Integer refreshedLaunchTicks = this.ticksSetting.getValue();
            this.fastLaunchTicksRemaining = refreshedLaunchTicks;
         }
      }
   }

   @EventHandler
   private void onMultiPlayerGameModeUseItemOnEvent(MultiPlayerGameModeUseItemOnEvent event) {
      if (this.antiWasteSetting.getValue() && ClientAccess.argusclientMinecraft.player.getItemInHand(event.getHand()).is(Items.FIREWORK_ROCKET)) {
         ClientAccess.argusclientMinecraft.gameMode.useItem(ClientAccess.argusclientMinecraft.player, event.getHand());
         ClientAccess.argusclientMinecraft.player.swing(event.getHand());
         event.cancel();
      }
   }

   private void removeFakeRocket() {
      if (this.fakeRocketEntityId != -1) {
         ClientLevel level = ClientAccess.argusclientMinecraft.level;
         if (level != null) {
            level.removeEntity(this.fakeRocketEntityId, RemovalReason.KILLED);
         }

         this.fakeRocketEntityId = -1;
      }
   }

   private void equipElytraAndStartFlight() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (!player.onGround()) {
         FireworksModule.SwapChoice swapMode = this.swapSetting.getValue();
         if (!swapMode.isSilent()) {
            if (PlayerStateUtils.isLocalWearingElytra()) {
               if (!PlayerStateUtils.isFallFlying()) {
                  PacketSender.sendStartFallFlying();
                  player.startFallFlying();
               }
            } else {
               int slot = InventoryUtils.findMenuSlot(Items.ELYTRA);
               if (slot != -1 && !InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 4)) {
                  autoArmorModule.skipNextArmorUpdate();
                  swapMode.swapElytra(slot, 6);
                  if (!PlayerStateUtils.isFallFlying()) {
                     PacketSender.sendStartFallFlying();
                     player.startFallFlying();
                  }

                  this.swappedElytraSlot = slot;
               }
            }
         } else {
            int slot = InventoryUtils.findHotbarSlot(Items.ELYTRA);
            boolean inventorySwapRequired = slot == -1;
            if (inventorySwapRequired) {
               slot = InventoryUtils.findMenuSlot(Items.ELYTRA);
            }

            if (slot != -1) {
               int originalMenuSlot = slot;
               if (inventorySwapRequired) {
                  slot = 0;
                  InventoryUtils.clickSlot(originalMenuSlot, 0, ClickType.SWAP);
               }

               InventoryUtils.clickSlot(6, slot, ClickType.SWAP);

               try {
                  PacketSender.sendStartFallFlying();
                  player.startFallFlying();
               } finally {
                  InventoryUtils.clickSlot(6, slot, ClickType.SWAP);
                  if (inventorySwapRequired) {
                     InventoryUtils.clickSlot(originalMenuSlot, 0, ClickType.SWAP);
                  }
               }
            }
         }
      }
   }

   private void tryAutoPhase() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (PlayerStateUtils.isFallFlying() && EntityUtils.hasFirework(player)) {
         if (!(RaycastUtils.raycastWithEntityTrace(player) instanceof EntityHitResult hit && hit.getEntity().getType() == EntityType.END_CRYSTAL)) {
            int selectedSlot = InventoryUtils.getSelectedSlot();
            InventoryUtils.SwapSlot pearlSlot = InventoryUtils.findSwapSlot(Items.ENDER_PEARL, true);
            Vec3 lookDirection = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
            AABB projectedBounds = player.getBoundingBox().move(lookDirection.scale(0.1));
            boolean blockedAhead = !ClientAccess.argusclientMinecraft.level.noBlockCollision(player, projectedBounds) && this.ceilingFlightTicks >= 2;
            boolean pearlTarget = !player.getCooldowns().isOnCooldown(Items.ENDER_PEARL.getDefaultInstance())
               && ClientAccess.argusclientMinecraft.hitResult instanceof BlockHitResult hit
               && WorldUtils.hasCollision(hit.getBlockPos());
            if (this.autoPhaseSetting.getValue() && pearlSlot.isFound() && blockedAhead && pearlTarget) {
               InventoryUtils.swapTo(pearlSlot);

               try {
                  ClientAccess.argusclientMinecraft.gameMode.useItem(player, pearlSlot.hand());
                  PacketSender.sendSwing(pearlSlot.hand());
               } finally {
                  InventoryUtils.swapBack(selectedSlot, pearlSlot);
               }
            }
         }
      }
   }

   public void tryUseRocket() {
      if (this.autoLaunchSetting.getValue()
         && (PlayerStateUtils.isFallFlying() || temporaryElytraSwapActive.get())
         && !ClientAccess.argusclientMinecraft.player.isUsingItem()) {
         int selectedSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
         InventoryUtils.SwapSlot rocketSlot = InventoryUtils.findSwapSlot(Items.FIREWORK_ROCKET, true);
         if (rocketSlot.isFound()) {
            InventoryUtils.swapTo(rocketSlot);

            try {
               ClientAccess.argusclientMinecraft.gameMode.useItem(ClientAccess.argusclientMinecraft.player, rocketSlot.hand());
               ClientContext.swingManager.swingPacket(InteractionHand.MAIN_HAND, SwingAction.INTERACT);
            } finally {
               InventoryUtils.swapBack(selectedSlot, rocketSlot);
            }

            rocketExtenderModule.launchPosition = ClientAccess.argusclientMinecraft.player.position();
            this.rocketUseTimer.reset();
            this.rocketLaunchedDuringFlight = true;
         }
      }
   }

   public boolean isRocketBoostActive() {
      return !this.rocketUseTimer.hasElapsed(500L) || EntityUtils.hasFirework(ClientAccess.argusclientMinecraft.player);
   }

   private void temporarilyEquipElytra() {
      int elytraSlot = InventoryUtils.findHotbarSlot(Items.ELYTRA);
      if (elytraSlot != -1 && temporaryElytraSwapActive.compareAndSet(false, true)) {
         InventoryUtils.clickSlot(6, elytraSlot, ClickType.SWAP);
         PacketSender.sendStartFallFlying();
         ClientAccess.argusclientMinecraft.player.startFallFlying();
         ClientContext.taskScheduler.scheduleRotationTask(() -> {
            try {
               InventoryUtils.clickSlot(6, elytraSlot, ClickType.SWAP);
            } finally {
               temporaryElytraSwapActive.set(false);
            }
         }, 2);
      }
   }

   public static void bindModules(ModuleRegistry modules) {
      autoArmorModule = (AutoArmorModule)modules.getModule(AutoArmorModule.class);
      autoMaceModule = (AutoMaceModule)modules.getModule(AutoMaceModule.class);
      rocketExtenderModule = (RocketExtenderModule)modules.getModule(RocketExtenderModule.class);
      elytraFlyModule = (ElytraFlyModule)modules.getModule(ElytraFlyModule.class);
   }

   public FireworksModule() {
      super("Fireworks", "Enhances the usage of firework rockets.", ModuleCategory.MOVEMENT, "rockets");
      Predicate<Object> directionalVisibility = ignored -> !this.grimSetting.getValue();
      this.directionalSetting = this.add(new BooleanSetting("Directional", false, directionalVisibility).dependsOn(this.boostSetting));
      Predicate<Object> boostFactorVisibility = ignored -> !this.grimSetting.getValue();
      this.boostFactorSetting = this.add(new NumberSetting<>("BoostFactor", 0.0F, 0.0F, 3.0F, boostFactorVisibility).dependsOn(this.boostSetting));
      this.autoLaunchSetting = this.add(new BooleanSetting("AutoLaunch", false).markVisibilityDependency());
      this.onlyElytraFlySetting = this.add(new BooleanSetting("OnlyElytraFly", true).dependsOn(this.autoLaunchSetting));
      this.onlyFirstSetting = this.add(new BooleanSetting("OnlyFirst", false).dependsOn(this.autoLaunchSetting));
      this.awaitSetting = this.add(new BooleanSetting("Await", false).dependsOn(this.autoLaunchSetting));
      NumberSetting<Float> delaySettingValue = new NumberSetting<>("Delay", 0.0F, 0.0F, 20.0F);
      delaySettingValue.withUnit("s");
      this.delaySetting = this.add(delaySettingValue.dependsOn(this.autoLaunchSetting));
      this.fastLaunchSetting = this.add(new BooleanSetting("FastLaunch", false).markVisibilityDependency());
      this.modeSetting = this.add(new EnumSetting<>("Mode", FireworksModule.ModeChoice.MANUAL).dependsOn(this.fastLaunchSetting));
      this.swapSetting = this.add(new EnumSetting<>("Swap", FireworksModule.SwapChoice.NORMAL).dependsOn(this.fastLaunchSetting));
      Predicate<Object> ticksVisibility = ignored -> this.modeSetting.getValue() == FireworksModule.ModeChoice.MANUAL;
      this.ticksSetting = this.add(new NumberSetting<>("Ticks", 10, 3, 40, ticksVisibility).dependsOn(this.fastLaunchSetting));
      Predicate<Object> swapBackVisibility = ignored -> this.swapSetting.getValue() != FireworksModule.SwapChoice.SILENT;
      this.swapBackSetting = this.add(new BooleanSetting("SwapBack", true, swapBackVisibility).dependsOn(this.fastLaunchSetting));
      this.autoPhaseSetting = this.add(new BooleanSetting("AutoPhase", false).dependsOn(this.fastLaunchSetting));
      this.rocketUseTimer = new ElapsedTimer();
      this.fakeRocketTimer = new ElapsedTimer();
      this.fakeRocketLifetimeTicks = 0;
      this.fakeRocketEntityId = -1;
      this.delaySetting.setBoundaryLabel("Auto", SettingLabelCondition.MIN);
      this.fastLaunchSetting.setChangeCallback(() -> {
         if (!this.isWorldUnavailable() && this.fastLaunchSetting.getValue() && this.modeSetting.getValue() == FireworksModule.ModeChoice.TOGGLE) {
            this.rocketUseTimer.clear();
            ClientContext.inputOverrides.requestJump(true);
         }
      });
   }

   public enum ModeChoice implements NamedEntry {
      MANUAL("Manual"),
      TOGGLE("Toggle");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }

   public enum SwapChoice implements NamedEntry {
      NORMAL("Normal"),
      ALT("Alt"),
      SILENT("Silent");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      SwapChoice(String displayName) {
         this.displayName = displayName;
      }

      public boolean isSilent() {
         return this == SILENT;
      }

      public void swapElytra(int elytraSlot, int chestMenuSlot) {
         if (this == NORMAL) {
            InventoryUtils.swapSlotsWithPickup(elytraSlot, chestMenuSlot);
         } else if (this == ALT) {
            InventoryUtils.swapMenuSlots(elytraSlot, chestMenuSlot);
         }
      }
   }
}
