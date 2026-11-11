package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.AuraAttackEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.SwingInteractionManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.movement.ElytraFlyModule;
import dev.argus.client.modules.movement.FireworksModule;
import dev.argus.client.modules.movement.NoSlowModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.settings.options.EntitySortChoice;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.DamageUtils;
import dev.argus.client.utils.combat.RaycastUtils;
import dev.argus.client.utils.combat.TargetSelector;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.entity.SwingAction;
import dev.argus.client.utils.inventory.ArmorSlot;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.inventory.ItemUtils;
import dev.argus.client.utils.math.Vec3Utils;
import dev.argus.client.utils.movement.ControlElytraFlyHandler;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AutoMaceModule extends Module {
   private static AuraModule aura = ClientContext.getModule(AuraModule.class);
   private static ElytraFlyModule elytraFly = ClientContext.getModule(ElytraFlyModule.class);
   private static AutoArmorModule autoArmor = ClientContext.getModule(AutoArmorModule.class);
   private static NoSlowModule noSlow = ClientContext.getModule(NoSlowModule.class);
   private static FireworksModule fireworks = ClientContext.getModule(FireworksModule.class);
   private final Setting<EntitySortChoice> sortingSetting = this.add(new EnumSetting<>("Sorting", EntitySortChoice.CROSSHAIR));
   private final Setting<Float> rangeSetting = this.add(new NumberSetting<>("Range", 64.0F, 16.0F, 256.0F).withUnit("m"));
   private final Setting<Float> heightSetting;
   private final Setting<Boolean> smartSetting = this.add(new BooleanSetting("Smart", true));
   private final Setting<Float> angleSetting;
   public final Setting<Boolean> nakedsSetting;
   private final Setting<Boolean> autoBoostSetting;
   private final Setting<Boolean> renderSetting;
   final Setting<Color> colorSetting;
   private final Setting<Boolean> targetsSetting;
   private final Setting<Boolean> playersSetting;
   private final Setting<Boolean> animalsSetting;
   private final Setting<Boolean> hostilesSetting;
   private final Setting<Boolean> neutralsSetting;
   private final AutoMaceTargetRenderer targetRenderer;
   private final AutoMaceStrikePlanner strikePlanner;
   private final ElapsedTimer boostTimer;
   private final ElapsedTimer strikeTimer;
   private final ElapsedTimer recentFlightTimer;
   private float verticalDisplacement;
   private int savedChestplateSlot;
   private int pendingChestSwapSlot;
   private boolean striking;
   private boolean maceAvailable;
   private Entity target;

   private void restoreChestSwap() {
      if (this.pendingChestSwapSlot != -1) {
         int swapSlot = this.pendingChestSwapSlot;
         ArmorSlot chestSlotType = ArmorSlot.CHESTPLATE;
         int chestContainerSlot = chestSlotType.getContainerSlot();
         InventoryUtils.swapMenuSlots(swapSlot, chestContainerSlot);
         if (PlayerStateUtils.isLocalWearingElytra() && !ClientAccess.argusclientMinecraft.player.onGround()) {
            this.startGliding();
         }

         this.pendingChestSwapSlot = -1;
      }
   }

   private boolean hasApproachPath(Entity candidate) {
      Vec3 eyePosition = ClientAccess.argusclientMinecraft.player.getEyePosition();
      Vec3 targetPosition = candidate.position();
      Float rangeValue = this.rangeSetting.getValue();
      if (!eyePosition.closerThan(targetPosition, rangeValue.floatValue())) {
         return false;
      }

      AABB targetBounds = candidate.getBoundingBox().inflate(0.5);
      int[] solidBlockCount = new int[]{0};
      Boolean blocked = BlockGetter.traverseBlocks(eyePosition, targetPosition, null, (ignoredEntity, position) -> {
         BlockPos statePosition = position;
         BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(statePosition);
         BlockPos intersectionPosition = position;
         if (targetBounds.intersects(intersectionPosition)) {
            return null;
         }

         VoxelShape collisionShape = state.getCollisionShape(ClientAccess.argusclientMinecraft.level, position);
         if (!collisionShape.isEmpty()) {
            if (++solidBlockCount[0] > 1) {
               return true;
            }

            BlockPos airSpacePosition = position;
            return this.hasNearbyAirSpace(airSpacePosition) ? null : true;
         } else {
            return null;
         }
      }, unused -> false);
      return !blocked;
   }

   private boolean isTargetInvalid(Entity candidate) {
      if (!candidate.isAlive()) {
         return true;
      }

      Vec3 eyePosition = ClientAccess.argusclientMinecraft.player.getEyePosition();
      Vec3 targetPosition = this.target.position();
      Float rangeValue = this.rangeSetting.getValue();
      if (!eyePosition.closerThan(targetPosition, rangeValue.floatValue())) {
         return true;
      }

      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      int entityId = candidate.getId();
      return level.getEntity(entityId) == null ? true : ClientContext.socialRegistry.isFriendEntity(candidate);
   }

   public float[] getFlightRotation() {
      if (this.target == null) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         return new float[]{player.getYRot(), player.getXRot()};
      }

      Vec3 targetPosition = this.target.position();
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Vec3 eyePosition = player.getEyePosition();
      float horizontalDistance = Vec3Utils.horizontalDistance(targetPosition, eyePosition);
      boolean reverseYaw = horizontalDistance < 12.0F;
      Vec3 offsetPosition = this.target.position();
      double targetHeight = this.target.getBbHeight();
      Vec3 targetTop = offsetPosition.add(0.0, targetHeight, 0.0);
      float[] targetRotation = RotationManager.getRotationsToPosition(targetTop);
      if (this.striking) {
         float[] strikeRotation = this.strikePlanner.rotation();
         return strikeRotation != null ? strikeRotation : targetRotation;
      }

      float[] flightRotation = new float[2];
      float targetYaw = targetRotation[0];
      int yawOffset;
      if (reverseYaw) {
         yawOffset = 180;
      } else {
         yawOffset = 0;
      }

      flightRotation[0] = Mth.wrapDegrees(targetYaw + yawOffset);
      Float angleValue = this.angleSetting.getValue();
      flightRotation[1] = -angleValue;
      return flightRotation;
   }

   private void refreshMaceAvailability() {
      Predicate<ItemStack> macePredicate = ItemUtils::isMace;
      InventoryUtils.SwapSlot maceSlot = InventoryUtils.findSwapSlot(macePredicate, true);
      this.maceAvailable = maceSlot.isFound();
   }

   private void updateFlightRotation() {
      if (!this.isFlightControlActive()) {
         this.strikePlanner.reset();
      } else {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         double heightAboveTarget = player.getY() - this.target.getBoundingBox().maxY;
         double horizontalDistance = Vec3Utils.horizontalDistance(player.position(), this.target.position());
         if (heightAboveTarget > this.heightSetting.getValue().floatValue() || this.hasOverheadCollision()) {
            this.striking = true;
         }

         boolean controlMode = elytraFly.isToggled() && elytraFly.modeSetting.getValue() == ElytraFlyModule.ModeChoice.CONTROL;
         if (this.striking && this.smartSetting.getValue() && (PlayerStateUtils.isLocalWearingElytra() || PlayerStateUtils.isFallFlying())) {
            double range = aura.getEffectiveRange(aura.rangeSetting);
            if (controlMode) {
               this.strikePlanner
                  .updateControl(
                     this.target,
                     range,
                     elytraFly.flightSpeedSetting.getValue() + fireworks.getAdditionalBoost(false),
                     elytraFly.vSpeedSetting.getValue().floatValue(),
                     elytraFly.glideSetting.getValue().floatValue()
                  );
            } else {
               double boostBudget = Double.POSITIVE_INFINITY;
               if (fireworks.isRocketBoostActive()) {
                  Entity movingTarget = this.target.isPassenger() ? this.target.getVehicle() : this.target;
                  boostBudget = MovementPhysics.getHorizontalSpeedKmh(movingTarget) <= 60.0 ? 10.0 : 0.0;
               }

               this.strikePlanner.updateNormal(this.target, range, true, boostBudget);
            }
         } else {
            this.strikePlanner.reset();
         }

         if (controlMode && elytraFly.getActiveMode() instanceof ControlElytraFlyHandler control) {
            int verticalInput;
            if (this.strikePlanner.isActive()) {
               verticalInput = this.strikePlanner.shouldDescend() ? -1 : 0;
            } else if (this.striking) {
               verticalInput = horizontalDistance < elytraFly.flightSpeedSetting.getValue() * 3.0F ? -1 : 0;
            } else {
               verticalInput = 1;
            }

            control.setVerticalInputOverride(verticalInput);
         }

         RotationManager rotationManager = ClientContext.rotationManager;
         rotationManager.forceRotationOverride();
         rotationManager.requestRotation(this.getFlightRotation(), 2000, true);
      }
   }

   private void boostWithRocket() {
      if (this.target != null && PlayerStateUtils.isFallFlying()) {
         if (!ClientAccess.argusclientMinecraft.player.onGround()) {
            if (!EntityUtils.hasFirework(ClientAccess.argusclientMinecraft.player)) {
               if (this.boostTimer.hasElapsed(400L)) {
                  int previousSlot = InventoryUtils.getSelectedSlot();
                  Item rocketItem = Items.FIREWORK_ROCKET;
                  InventoryUtils.SwapSlot rocketSlot = InventoryUtils.findSwapSlot(rocketItem, true);
                  if (rocketSlot.isFound()) {
                     InventoryUtils.swapTo(rocketSlot);
                     MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
                     LocalPlayer usePlayer = ClientAccess.argusclientMinecraft.player;
                     InteractionHand useHand = rocketSlot.hand();
                     gameMode.useItem(usePlayer, useHand);
                     SwingInteractionManager swingManager = ClientContext.swingManager;
                     InteractionHand swingHand = rocketSlot.hand();
                     SwingAction swingAction = SwingAction.INTERACT;
                     swingManager.swing(swingHand, swingAction);
                     InventoryUtils.swapBack(previousSlot, rocketSlot);
                     this.boostTimer.reset();
                  }
               }
            }
         }
      }
   }

   private void selectTarget() {
      Function<Entity, Integer> priority = this::getTargetPriority;
      Comparator<Entity> priorityComparator = Comparator.comparing(priority);
      Comparator<Entity> sortingComparator = this.sortingSetting.getValue().getComparator();
      Comparator<Entity> comparator = priorityComparator.thenComparing(sortingComparator);
      TargetSelector selector = new TargetSelector();
      Setting<Float> rangeSettingSnapshot = this.rangeSetting;
      float range;
      if (rangeSettingSnapshot.isAtMaximum()) {
         range = 2048.0F;
      } else {
         range = this.rangeSetting.getValue();
      }

      selector = selector.range(range);
      selector = selector.withPlayers(true);
      boolean ignoreNakeds = !this.nakedsSetting.getValue();
      selector = selector.ignoreNakeds(ignoreNakeds).filter(this::hasApproachPath);
      selector = selector.withAnimals(this.animalsSetting.getValue());
      selector = selector.withNeutrals(this.neutralsSetting.getValue());
      selector = selector.withHostiles(this.hostilesSetting.getValue());
      selector = selector.sortBy(comparator);
      this.target = selector.select();
   }

   private int getTargetPriority(Entity candidate) {
      if (candidate instanceof Player player) {
         return ClientContext.socialRegistry.isEnemyPlayer(player) ? 0 : 1;
      } else {
         return 1;
      }
   }

   private boolean hasOverheadCollision() {
      int solidBlocks = 0;
      AABB bounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
      bounds = bounds.setMinY(bounds.maxY);
      bounds = bounds.inflate(1.0, 0.0, 1.0);

      for (BlockPos position : BlockPos.betweenClosed(bounds)) {
         position = position.above();
         VoxelShape collisionShape = ClientAccess.argusclientMinecraft
            .level
            .getBlockState(position)
            .getCollisionShape(ClientAccess.argusclientMinecraft.level, position);
         if (!collisionShape.isEmpty()) {
            solidBlocks++;
         }
      }

      return solidBlocks > 3;
   }

   @Override
   public String getInfo() {
      if (this.target != null && this.target instanceof Player) {
         Object[] infoArguments = new Object[2];
         Entity nameTarget = this.target;
         infoArguments[0] = nameTarget.getName().getString();
         double distance = WorldUtils.getEntityDistance(this.target, false);
         infoArguments[1] = distance;
         return "%s, %.1f".formatted(infoArguments);
      } else {
         return null;
      }
   }

   @Override
   public void onEnable() {
      if (!this.isWorldUnavailable()) {
         this.selectTarget();
         this.targetRenderer.reset();
         this.strikePlanner.reset();
         this.strikeTimer.clear();
         this.savedChestplateSlot = this.pendingChestSwapSlot = -1;
         this.striking = false;
      }
   }

   @Override
   public void onDisable() {
      if (!this.isWorldUnavailable()) {
         boolean savedSlotInvalid = false;
         if (this.savedChestplateSlot != -1) {
            ItemStack savedStack = ClientAccess.argusclientMinecraft.player.getInventory().getItem(this.savedChestplateSlot);
            savedSlotInvalid = !this.isChestArmor(savedStack);
         }

         if (!autoArmor.isToggled() && !savedSlotInvalid) {
            if (this.savedChestplateSlot != -1) {
               this.pendingChestSwapSlot = this.savedChestplateSlot;
               this.savedChestplateSlot = -1;
            }

            this.restoreChestSwap();
         } else {
            this.savedChestplateSlot = this.pendingChestSwapSlot = -1;
         }
      }
   }

   private void refreshTarget() {
      if (this.target != null) {
         Entity targetSnapshot = this.target;
         if (this.isTargetInvalid(targetSnapshot)) {
            this.target = null;
         }
      }

      if (this.target == null) {
         this.selectTarget();
      }

      if (PlayerStateUtils.isInWater(ClientAccess.argusclientMinecraft.player)) {
         this.target = null;
      }
   }

   @EventHandler(priority = -100)
   public void steerFlightOnTick(LocalPlayerPreTickEvent event) {
      this.updateFlightRotation();
   }

   private void startGliding() {
      ClientAccess.argusclientMinecraft.player.startFallFlying();
      PacketSender.sendStartFallFlying();
   }

   private boolean isChestArmor(ItemStack stack) {
      TagKey chestArmorTag = ItemTags.CHEST_ARMOR;
      return stack.is(chestArmorTag);
   }

   @EventHandler(priority = -100)
   public void finishStrikeStage(AuraAttackEvent event) {
      if (event.getStage() != EventStage.Pre && this.striking && this.maceAvailable && this.target != null) {
         this.striking = false;
         this.strikeTimer.reset();
      }
   }

   @EventHandler
   public void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
      this.striking = false;
      this.target = null;
      this.strikeTimer.clear();
      this.strikePlanner.reset();
      this.targetRenderer.reset();
   }

   @EventHandler
   public void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      double previousY = ClientAccess.argusclientMinecraft.player.yo;
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      double verticalDelta = previousY - player.getY();
      this.verticalDisplacement = (float)Math.abs(verticalDelta);
      if (PlayerStateUtils.isFallFlying()) {
         this.recentFlightTimer.reset();
      }
   }

   @EventHandler(priority = 100)
   public void prepareMaceTick(LocalPlayerPreTickEvent event) {
      this.refreshMaceAvailability();
      this.refreshTarget();
      boolean infiniteDurabilityMode = false;
      if (elytraFly.isToggled()) {
         Setting<ElytraFlyModule.InfDurabilityChoice> durabilitySettingSnapshot = elytraFly.infDurabilitySetting;
         infiniteDurabilityMode = !durabilitySettingSnapshot.getValue().isDisabled();
      }

      if (infiniteDurabilityMode || this.target != null && this.maceAvailable) {
         if (!PlayerStateUtils.isLocalWearingElytra() && !this.striking && this.pendingChestSwapSlot == -1 && !infiniteDurabilityMode) {
            autoArmor.skipNextArmorUpdate();
            this.equipElytra();
         } else {
            if (this.renderSetting.getValue()) {
               this.targetRenderer.addTargetMarker(this.target);
            }

            if (this.autoBoostSetting.getValue()) {
               this.boostWithRocket();
            }

            if (this.target != null) {
               autoArmor.skipNextArmorUpdate();
               double targetTopY = this.target.getBoundingBox().maxY;
               double heightAboveTarget = ClientAccess.argusclientMinecraft.player.getY() - targetTopY;
               if (!this.striking) {
                  this.restoreChestSwap();
                  if (!PlayerStateUtils.isFallFlying()
                     && PlayerStateUtils.isWearingElytra(ClientAccess.argusclientMinecraft.player)
                     && !EntityUtils.hasCollisionBelow(ClientAccess.argusclientMinecraft.player)) {
                     PacketSender.sendStartFallFlying();
                     ClientAccess.argusclientMinecraft.player.startFallFlying();
                  }

                  if (!PlayerStateUtils.isFallFlying() && ClientAccess.argusclientMinecraft.player.onGround() && !this.strikeTimer.hasElapsed(500L)) {
                     ClientContext.inputOverrides.requestJump(true);
                  }
               } else {
                  if (ClientAccess.argusclientMinecraft.player.onGround()) {
                     this.striking = false;
                     this.strikeTimer.reset();
                     return;
                  }

                  double playerY = ClientAccess.argusclientMinecraft.player.getY();
                  if (playerY < this.target.getY() - this.target.getBbHeight()) {
                     this.striking = false;
                  }

                  Object auraRangeValue = aura.rangeSetting.getValue();
                  Setting<Float> auraWallRangeSetting = aura.wallRangeSetting;
                  boolean visibleTarget;
                  if (Objects.equals(auraRangeValue, auraWallRangeSetting.getValue())) {
                     visibleTarget = RaycastUtils.hasVisibleEntitySample(this.target);
                  } else {
                     visibleTarget = true;
                  }

                  AuraModule auraSnapshot = aura;
                  Setting<Float> effectiveRangeSetting = visibleTarget ? aura.rangeSetting : aura.wallRangeSetting;
                  float attackRange = auraSnapshot.getEffectiveRange(effectiveRangeSetting);
                  float verticalSpeed = this.verticalDisplacement;
                  float effectiveRange = attackRange + verticalSpeed * 2.0F;
                  if (noSlow.isGrimStrictInventoryEnabled()) {
                     effectiveRange += this.verticalDisplacement;
                  }

                  Setting<ElytraFlyModule.ModeChoice> flightModeSettingSnapshot = elytraFly.modeSetting;
                  if (flightModeSettingSnapshot.getValue() == ElytraFlyModule.ModeChoice.CONTROL) {
                     Setting<Float> verticalSpeedSettingSnapshot = elytraFly.vSpeedSetting;
                     Float verticalSpeedValue = verticalSpeedSettingSnapshot.getValue();
                     effectiveRange += verticalSpeedValue;
                  }

                  boolean withinAttackRange = this.strikePlanner.isActive()
                     ? this.strikePlanner.hitTicks() <= (noSlow.isGrimStrictInventoryEnabled() ? 1 : 0)
                     : heightAboveTarget <= effectiveRange;
                  if (withinAttackRange && this.pendingChestSwapSlot == -1 && PlayerStateUtils.isLocalWearingElytra()) {
                     int chestplateSlot = this.findChestArmorOrEmptySlot();
                     if (chestplateSlot != -1) {
                        this.pendingChestSwapSlot = InventoryUtils.toMenuSlot(chestplateSlot);
                        int chestSwapSlot = this.pendingChestSwapSlot;
                        ArmorSlot chestSlotType = ArmorSlot.CHESTPLATE;
                        int chestContainerSlot = chestSlotType.getContainerSlot();
                        InventoryUtils.swapMenuSlots(chestSwapSlot, chestContainerSlot);
                     }
                  }
               }
            }
         }
      } else {
         this.restoreChestSwap();
      }
   }

   private boolean hasNearbyAirSpace(BlockPos position) {
      int airBlocks = 0;
      AABB bounds = new AABB(position);
      bounds = bounds.inflate(0.5, -0.5, 0.5);

      for (BlockPos neighbor : BlockPos.betweenClosed(bounds)) {
         VoxelShape collisionShape = ClientAccess.argusclientMinecraft
            .level
            .getBlockState(neighbor)
            .getCollisionShape(ClientAccess.argusclientMinecraft.level, neighbor);
         if (collisionShape.isEmpty()) {
            airBlocks++;
         }
      }

      return airBlocks > 2;
   }

   private int findChestArmorOrEmptySlot() {
      int slot = InventoryUtils.findInventorySlot(this::isChestArmor, false);
      if (slot == -1) {
         slot = InventoryUtils.findInventorySlot(ItemStack::isEmpty, false);
      }

      return slot;
   }

   public boolean isStrikingTarget() {
      return this.target != null && this.striking;
   }

   public boolean shouldSuppressGrimBoost() {
      if (this.isToggled() && this.maceAvailable && this.target != null && this.target.distanceTo(ClientAccess.argusclientMinecraft.player) <= 10.0F) {
         Entity movingTarget = this.target.isPassenger() ? this.target.getVehicle() : this.target;
         return MovementPhysics.getHorizontalSpeedKmh(movingTarget) <= 60.0;
      } else {
         return false;
      }
   }

   public boolean isFlightControlActive() {
      return this.target != null && !this.recentFlightTimer.hasElapsed(100L) && this.maceAvailable;
   }

   private void equipElytra() {
      if (this.savedChestplateSlot == -1) {
         int elytraInventorySlot = InventoryUtils.findInventorySlot(stack -> {
            Item elytraItem = Items.ELYTRA;
            return stack.is(elytraItem) && DamageUtils.getDurabilityPercent(stack) > 1;
         }, false);
         int elytraContainerSlot = InventoryUtils.toMenuSlot(elytraInventorySlot);
         if (elytraContainerSlot != -1) {
            Inventory inventory = ClientAccess.argusclientMinecraft.player.getInventory();
            ArmorSlot chestSlotType = ArmorSlot.CHESTPLATE;
            int chestInventorySlot = chestSlotType.getInventoryIndex();
            ItemStack previousChestStack = inventory.getItem(chestInventorySlot);
            ArmorSlot chestContainerSlotType = ArmorSlot.CHESTPLATE;
            int chestContainerSlot = chestContainerSlotType.getContainerSlot();
            InventoryUtils.swapMenuSlots(elytraContainerSlot, chestContainerSlot);
            this.startGliding();
            if (this.isChestArmor(previousChestStack)) {
               this.savedChestplateSlot = elytraContainerSlot;
            }
         }
      }
   }

   public static void bindReferencedModules(ModuleRegistry modules) {
      aura = (AuraModule)modules.getModule(AuraModule.class);
      elytraFly = (ElytraFlyModule)modules.getModule(ElytraFlyModule.class);
      autoArmor = (AutoArmorModule)modules.getModule(AutoArmorModule.class);
      noSlow = (NoSlowModule)modules.getModule(NoSlowModule.class);
      fireworks = (FireworksModule)modules.getModule(FireworksModule.class);
   }

   private static String createDescription() {
      String warningColor = String.valueOf(ChatFormatting.RED);
      return "Automatically flies towards an enemy to smash them.\n" + warningColor + "Requires Aura module enabled.";
   }

   public AutoMaceModule() {
      super("AutoMace", createDescription(), ModuleCategory.COMBAT);
      this.heightSetting = this.add(new NumberSetting<>("Height", 15.0F, 5.0F, 30.0F).withUnit("m"));
      this.angleSetting = this.add(new NumberSetting<>("Angle", 75.0F, 45.0F, 90.0F).withUnit("°"));
      this.autoBoostSetting = this.add(new BooleanSetting("AutoBoost", true));
      this.renderSetting = this.add(new BooleanSetting("Render", false).markVisibilityDependency());
      this.colorSetting = this.add(new ColorSetting("Color", new Color(-4351489, true)).dependsOn(this.renderSetting));
      this.targetsSetting = this.add(new BooleanSetting("Targets", false).markAsGroup().markVisibilityDependency());
      this.playersSetting = this.add(new BooleanSetting("Players", true).dependsOn(this.targetsSetting));
      this.nakedsSetting = this.add(new BooleanSetting("Nakeds", false).dependsOn(this.targetsSetting, this.playersSetting));
      this.animalsSetting = this.add(new BooleanSetting("Animals", false).dependsOn(this.targetsSetting));
      this.hostilesSetting = this.add(new BooleanSetting("Hostiles", false).dependsOn(this.targetsSetting));
      this.neutralsSetting = this.add(new BooleanSetting("Neutrals", false).dependsOn(this.targetsSetting));
      this.targetRenderer = new AutoMaceTargetRenderer(this);
      this.strikePlanner = new AutoMaceStrikePlanner();
      this.boostTimer = new ElapsedTimer();
      this.strikeTimer = new ElapsedTimer();
      this.recentFlightTimer = new ElapsedTimer();
      this.rangeSetting.setBoundaryLabel("Unlimited", SettingLabelCondition.MAX);
      AutoMaceTargetRenderer renderer = this.targetRenderer;
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(renderer);
   }
}
