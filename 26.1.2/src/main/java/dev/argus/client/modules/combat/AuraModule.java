package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.AuraAttackEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.modules.exploit.MultiTaskModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.settings.options.AntiCheatRotationsChoice;
import dev.argus.client.settings.options.AuraMaceChoice;
import dev.argus.client.settings.options.AuraSwapChoice;
import dev.argus.client.settings.options.AuraTypeChoice;
import dev.argus.client.settings.options.AuraWeaponChoice;
import dev.argus.client.settings.options.EntitySortChoice;
import dev.argus.client.settings.options.MultiTaskModeChoice;
import dev.argus.client.utils.LockUtils;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.combat.RaycastUtils;
import dev.argus.client.utils.combat.TargetSelector;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.inventory.EnchantmentUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.inventory.ItemUtils;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class AuraModule extends Module {
   private static final InventoryUtils.SwapSlot NO_MACE = new InventoryUtils.SwapSlot(-1, false, InteractionHand.MAIN_HAND);
   private static AntiCheatModule antiCheat = ClientContext.getModule(AntiCheatModule.class);
   private static MultiTaskModule multiTask = ClientContext.getModule(MultiTaskModule.class);
   private static AutoMaceModule autoMace = ClientContext.getModule(AutoMaceModule.class);
   private final Setting<EntitySortChoice> sortingSetting = this.add(new EnumSetting<>("Sorting", EntitySortChoice.DISTANCE));
   private final Setting<Float> fovSetting = this.add(new NumberSetting<>("Fov", 180.0F, 0.0F, 180.0F).withUnit("°"));
   final Setting<Float> delaySetting = this.add(new NumberSetting<>("Delay", 1.0F, 0.0F, 1.0F));
   public final Setting<Float> rangeSetting = this.add(new NumberSetting<>("Range", 6.5F, 1.0F, 6.5F).withUnit("m"));
   public final Setting<Float> wallRangeSetting = this.add(new NumberSetting<>("WallRange", 6.5F, 0.0F, 6.5F).withUnit("m"));
   final Setting<Boolean> extraRangeSetting = this.add(new BooleanSetting("ExtraRange", false));
   final Setting<Boolean> tPSSyncSetting = this.add(new BooleanSetting("TPSSync", false));
   private final Setting<Boolean> pauseBaritoneSetting;
   private final Setting<Boolean> rotateSetting;
   private final Setting<Boolean> magnetSetting;
   private final Setting<Integer> offsetSetting;
   private final Setting<Float> limitSetting;
   final Setting<Boolean> renderSetting;
   final Setting<AuraTypeChoice> typeSetting;
   final Setting<Color> fillSetting;
   final Setting<Color> outlineSetting;
   final Setting<Float> lineWidthSetting;
   private final Setting<Boolean> weaponsSetting;
   final Setting<AuraWeaponChoice> weaponSetting;
   private final Setting<AuraSwapChoice> swapSetting;
   private final Setting<AuraMaceChoice> maceSetting;
   private final Setting<Boolean> alternativeMaceSetting;
   final Setting<Boolean> targetsSetting;
   private final Setting<Boolean> playersSetting;
   private final Setting<Boolean> invisibleSetting;
   private final Setting<Boolean> ignoreNakedsSetting;
   private final Setting<Boolean> animalsSetting;
   private final Setting<Boolean> villagersSetting;
   private final Setting<Boolean> hostilesSetting;
   private final Setting<Boolean> neutralsSetting;
   private final Setting<Boolean> crystalsSetting;
   private final Setting<Boolean> projectilesSetting;
   private final Setting<Boolean> vehiclesSetting;
   private final Setting<Boolean> ignoreNamedSetting;
   private final AuraAttackCooldown attackCooldown;
   private final AuraAttackReadiness attackReadiness;
   private final AuraTargetRenderer targetRenderer;
   private int restoreSlot;
   public boolean criticalAttackFlag;
   private Entity target;
   private Entity crosshairTarget;

   private boolean isWithinFov(Entity candidate) {
      if (this.fovSetting.isAtMinimum()) {
         return candidate == this.crosshairTarget;
      }

      float[] angles = RotationManager.getRotationsToEntity(candidate);
      float targetYaw = angles[0];
      float playerYaw = ClientAccess.argusclientMinecraft.player.getYRot();
      float yawDifference = Mth.degreesDifferenceAbs(targetYaw, playerYaw);
      float pitchDelta = ClientAccess.argusclientMinecraft.player.getXRot() - angles[1];
      float pitchDifference = Math.abs(pitchDelta);
      Float yawLimit = this.fovSetting.getValue();
      return yawDifference <= yawLimit && pitchDifference <= this.fovSetting.getValue();
   }

   private boolean isSwapWeaponMode() {
      return this.weaponSetting.getValue() == AuraWeaponChoice.SWAP;
   }

   float getAttackDelay() {
      if (this.target == null) {
         return this.delaySetting.getValue();
      }

      Entity capturedMaceTarget = this.target;
      InventoryUtils.SwapSlot maceSlot = this.findMaceSlot(capturedMaceTarget);
      if (maceSlot.isFound()) {
         float fallDistance = PlayerStateUtils.getTrackedFallDistance();
         if (fallDistance > 1.0F) {
            Inventory inventory = ClientAccess.argusclientMinecraft.player.getInventory();
            return maceSlot.slot() == inventory.getSelectedSlot() ? 0.1F : 0.25F;
         }
      }

      return this.delaySetting.getValue();
   }

   private void restoreWeaponSlot() {
      if (this.isSwapWeaponMode() && this.swapSetting.getValue() == AuraSwapChoice.SWAPBACK && this.restoreSlot != -1) {
         InventoryUtils.selectHotbarSlotForPlacement(this.restoreSlot);
         this.restoreSlot = -1;
      }
   }

   public boolean isTargetWithinAttackRange() {
      if (this.target == null) {
         return false;
      }

      Entity distanceTarget = this.target;
      boolean useExtraRange = this.extraRangeSetting.getValue();
      double distance = WorldUtils.getEntityDistance(distanceTarget, useExtraRange);
      boolean visible = RaycastUtils.hasVisibleEntitySample(this.target);
      float attackRange = this.getEffectiveRange(visible ? this.rangeSetting : this.wallRangeSetting);
      return distance <= attackRange;
   }

   public Entity getTarget() {
      return this.target;
   }

   private void updateCrosshairTarget() {
      if (!this.fovSetting.isAtMinimum()) {
         this.crosshairTarget = null;
      } else if (ClientAccess.argusclientMinecraft.hitResult instanceof EntityHitResult hit) {
         this.crosshairTarget = hit.getEntity();
      } else {
         this.crosshairTarget = null;
      }
   }

   private InventoryUtils.SwapSlot findWeaponSlot() {
      if (!this.isSwapWeaponMode()) {
         return new InventoryUtils.SwapSlot(InventoryUtils.getSelectedSlot(), true, InteractionHand.MAIN_HAND);
      }

      Predicate<ItemStack> weaponPredicate = stack -> ItemUtils.isAxe(stack) || ItemUtils.isSword(stack) || ItemUtils.isSpear(stack);
      return InventoryUtils.findSwapSlot(weaponPredicate, false);
   }

   @Override
   public String getInfo() {
      if (this.target != null && this.target instanceof Player) {
         Component targetName = this.target.getName();
         return targetName.getString();
      } else {
         return null;
      }
   }

   @Override
   public void onToggle() {
      this.target = null;
      ClientContext.baritoneBridge.releasePause(this);
   }

   @Override
   public void onDisable() {
      if (this.isWorldUnavailable()) {
         this.restoreSlot = -1;
      } else {
         this.restoreWeaponSlot();
      }
   }

   private void attackCurrentTarget() {
      if (this.target != null) {
         Entity capturedVisibilityTarget = this.target;
         float attackRange;
         if (RaycastUtils.hasVisibleEntitySample(capturedVisibilityTarget)) {
            Setting<Float> capturedRange = this.rangeSetting;
            attackRange = this.getEffectiveRange(capturedRange);
         } else {
            Setting<Float> capturedWallRange = this.wallRangeSetting;
            attackRange = this.getEffectiveRange(capturedWallRange);
         }

         Entity capturedDistanceTarget = this.target;
         boolean useExtraRange = this.extraRangeSetting.getValue();
         if (!(WorldUtils.getEntityDistance(capturedDistanceTarget, useExtraRange) > attackRange)) {
            int originalSlot = InventoryUtils.getSelectedSlot();
            InventoryUtils.SwapSlot weaponSlot = this.findWeaponSlot();
            boolean swapRequired = this.isSwapWeaponMode();
            if (this.target instanceof EndCrystal) {
               LocalPlayer weaknessPlayer = ClientAccess.argusclientMinecraft.player;
               Holder weaknessEffect = MobEffects.WEAKNESS;
               swapRequired = weaknessPlayer.hasEffect(weaknessEffect);
               if (swapRequired) {
                  LocalPlayer strengthPlayer = ClientAccess.argusclientMinecraft.player;
                  Holder strengthEffect = MobEffects.STRENGTH;
                  swapRequired = !strengthPlayer.hasEffect(strengthEffect);
               }
            }

            boolean silentSwap = this.swapSetting.getValue() == AuraSwapChoice.SILENT && swapRequired;
            Entity capturedMaceTarget = this.target;
            InventoryUtils.SwapSlot maceSlot = this.findMaceSlot(capturedMaceTarget);
            if (maceSlot.isFound()) {
               weaponSlot = maceSlot;
               swapRequired = true;
               silentSwap = true;
            }

            AuraAttackReadiness capturedReadiness = this.attackReadiness;
            Entity capturedReadinessTarget = this.target;
            float attackStrength = this.attackCooldown
               .getAttackStrength(weaponSlot.isFound() ? weaponSlot.getStack() : ClientAccess.argusclientMinecraft.player.getMainHandItem());
            if (capturedReadiness.canAttack(capturedReadinessTarget, attackStrength)) {
               boolean inventorySwap = weaponSlot.isFound() && !weaponSlot.hotbar() && !weaponSlot.isInHand();
               if (!swapRequired
                  || !inventorySwap
                  || ClientAccess.argusclientMinecraft.player.containerMenu == ClientAccess.argusclientMinecraft.player.inventoryMenu) {
                  if (swapRequired) {
                     if (multiTask.shouldPauseModule(this)
                        && ClientAccess.argusclientMinecraft.player.getUsedItemHand() == InteractionHand.MAIN_HAND
                        && weaponSlot.slot() != originalSlot
                        && weaponSlot.isFound()) {
                        return;
                     }

                     InventoryUtils.swapTo(weaponSlot);
                     if (originalSlot != weaponSlot.slot()) {
                        this.restoreSlot = originalSlot;
                     }
                  }

                  try {
                     EventBus capturedPreBus = ClientAccess.eventBus;
                     AuraAttackEvent preEvent = new AuraAttackEvent(EventStage.Pre, this.target.getId());
                     capturedPreBus.post(preEvent);
                     Entity capturedAttackTarget = this.target;
                     this.sendAttack(capturedAttackTarget);
                     boolean useMagnet = this.magnetSetting.getValue()
                        && this.target.getType() != EntityType.CREEPER
                        && this.target.getType() != EntityType.END_CRYSTAL;
                     if (this.rotateSetting.getValue() && useMagnet) {
                        float[] rotations = RotationManager.getRotationsToEntity(this.target);
                        float targetYaw = rotations[0];
                        Integer offsetValue = this.offsetSetting.getValue();
                        float offsetYaw = targetYaw + offsetValue.intValue();
                        rotations[0] = Mth.wrapDegrees(offsetYaw);
                        ClientContext.rotationManager.requestRotation(rotations, 4);
                     }
                  } finally {
                     if (silentSwap && weaponSlot.isFound()) {
                        InventoryUtils.swapBack(originalSlot, weaponSlot);
                     }
                  }

                  EventBus capturedPostBus = ClientAccess.eventBus;
                  AuraAttackEvent postEvent = new AuraAttackEvent(EventStage.Post, this.target.getId());
                  capturedPostBus.post(postEvent);
               }
            }
         }
      }
   }

   public float getEffectiveRange(Setting<Float> rangeSetting) {
      if (rangeSetting.isAtMaximum()) {
         InventoryUtils.SwapSlot weaponSlot = this.findWeaponSlot();
         ItemStack capturedWeaponStack;
         if (!weaponSlot.isFound()) {
            capturedWeaponStack = ClientAccess.argusclientMinecraft.player.getMainHandItem();
         } else {
            capturedWeaponStack = weaponSlot.getStack();
         }

         ItemStack weaponStack = capturedWeaponStack;
         DataComponentType<AttackRange> attackRangeComponent = DataComponents.ATTACK_RANGE;
         AttackRange attackRange = weaponStack.get(attackRangeComponent);
         return attackRange == null ? 3.0F : attackRange.maxReach();
      } else {
         return rangeSetting.getValue();
      }
   }

   private boolean shouldAttackDuringRotationCollection() {
      return autoMace.isToggled()
         && autoMace.isStrikingTarget()
         && RotationManager.isMovementSyncEnabled()
         && antiCheat.rotationsSetting.getValue() == AntiCheatRotationsChoice.MOTION;
   }

   @EventHandler
   private void onPlayerUpdate(RotationRequestEvent event) {
      this.updateCrosshairTarget();
      boolean targetDead = this.target != null && !this.target.isAlive();
      boolean multiTaskBlocked = multiTask.shouldPauseModule(this) && multiTask.getMode() == MultiTaskModeChoice.NONE;
      Item heldItem = ClientAccess.argusclientMinecraft.player.getMainHandItem().getItem();
      boolean missingWeapon = !this.isAllowedWeapon(heldItem) && this.weaponSetting.getValue() == AuraWeaponChoice.REQUIRE;
      if (autoMace.isToggled() && autoMace.isStrikingTarget()) {
         missingWeapon = false;
      }

      if (missingWeapon || targetDead || multiTaskBlocked) {
         this.target = null;
      } else if (ClientAccess.argusclientMinecraft.player.containerMenu == ClientAccess.argusclientMinecraft.player.inventoryMenu || !antiCheat.is2b2tEnabled()
         )
       {
         float searchPadding = 0.0F;
         if (this.limitSetting.isAtMaximum()
            && RotationManager.isMovementSyncEnabled()
            && (!PlayerStateUtils.isFallFlying() || RotationManager.isSilentRotationMode())) {
            searchPadding = 0.1F;
         }

         TargetSelector targetFilter = this.buildTargetFilter(searchPadding);
         this.target = targetFilter.select();
         ClientContext.baritoneBridge.releasePause(this);
         if (this.pauseBaritoneSetting.getValue() && this.target != null) {
            ClientContext.baritoneBridge.requestPause(this);
         }

         if (this.target == null) {
            this.restoreWeaponSlot();
         } else {
            float attackStrength = this.attackCooldown.getSlotAttackStrength(this.findWeaponSlot());
            if (RotationManager.isMovementSyncEnabled()) {
               Entity readinessTarget = this.target;
               if (!this.attackReadiness.canAttack(readinessTarget, attackStrength)) {
                  return;
               }
            }

            if (this.shouldAttackDuringRotationCollection()) {
               ClientContext.rotationManager.executeAction(this::attackCurrentTarget);
            } else {
               LockUtils.runWithSharedLock(this::attackCurrentTarget);
            }

            if (this.rotateSetting.getValue()) {
               if (!RotationManager.isMovementSyncEnabled() && this.limitSetting.getValue() - 0.0F != 0.0F) {
                  float limit = this.limitSetting.getValue();
                  if (limit * this.getAttackDelay() > attackStrength) {
                     return;
                  }
               }

               Entity capturedTarget = this.target;
               boolean capturedExtraRangeFlag = this.extraRangeSetting.getValue();
               WorldUtils.EntityRange rangeResult = WorldUtils.getEntityRange(capturedTarget, capturedExtraRangeFlag);
               Vec3 rawAimOrigin = rangeResult.origin();
               Vec3 aimOrigin = this.adjustAimOrigin(rawAimOrigin);
               AABB targetBounds = this.target.getBoundingBox();
               Vec3 aimPoint = BoundingBoxUtils.clampToBounds(aimOrigin, targetBounds);
               float[] rotations = RotationManager.calculateRotations(aimOrigin, aimPoint);
               ClientContext.rotationManager.requestRotation(rotations, 5);
            }
         }
      }
   }

   private TargetSelector buildTargetFilter(float searchPadding) {
      TargetSelector filter = new TargetSelector()
         .withPlayers(this.playersSetting.getValue())
         .withNeutrals(this.neutralsSetting.getValue())
         .withAnimals(this.animalsSetting.getValue())
         .withVillagers(this.villagersSetting.getValue())
         .withHostiles(this.hostilesSetting.getValue())
         .withCrystals(this.crystalsSetting.getValue())
         .withProjectiles(this.projectilesSetting.getValue())
         .withVehicles(this.vehiclesSetting.getValue());
      float wallSearchRange = this.getEffectiveRange(this.wallRangeSetting) + searchPadding;
      filter.wallRange(wallSearchRange);
      float visibleSearchRange = this.getEffectiveRange(this.rangeSetting) + searchPadding;
      filter.visibleRange(visibleSearchRange)
         .ignoreNamed(this.ignoreNamedSetting.getValue())
         .ignoreNakeds(this.ignoreNakedsSetting.getValue())
         .ignoreInvisibles(!this.invisibleSetting.getValue());
      filter.sortBy(this.sortingSetting.getValue().getComparator()).filter(this::isWithinFov);
      return filter.useExtraRange(this.extraRangeSetting.getValue());
   }

   private InventoryUtils.SwapSlot findMaceSlot(Entity candidate) {
      if (candidate instanceof Player && !PlayerStateUtils.hasArmor((Player)candidate) && !autoMace.nakedsSetting.getValue()) {
         return NO_MACE;
      }

      if (autoMace.isToggled() && autoMace.isStrikingTarget()) {
         Predicate<ItemStack> strikingMacePredicate = ItemUtils::isMace;
         return InventoryUtils.findSwapSlot(strikingMacePredicate, this.alternativeMaceSetting.getValue());
      }

      boolean densityAllowed = !ClientAccess.argusclientMinecraft.player.isFallFlying() && PlayerStateUtils.getTrackedFallDistance() > 2.0F;
      Predicate<ItemStack> configuredMacePredicate = candidateStack -> ItemUtils.isMace(candidateStack) && this.maceSetting.getValue().matches(candidateStack);
      InventoryUtils.SwapSlot maceSlot = InventoryUtils.findSwapSlot(configuredMacePredicate, this.alternativeMaceSetting.getValue());
      if (maceSlot.isFound()) {
         ItemStack maceStack = maceSlot.getStack();
         ResourceKey densityEnchantment = Enchantments.DENSITY;
         if (EnchantmentUtils.hasEnchantment(densityEnchantment, maceStack) && !densityAllowed) {
            return NO_MACE;
         }
      }

      if (this.maceSetting.getValue() == AuraMaceChoice.SMART) {
         float smartFallDistance = PlayerStateUtils.getTrackedFallDistance();
         if (smartFallDistance > 3.0F && !maceSlot.isFound()) {
            Predicate<ItemStack> fallbackMacePredicate = ItemUtils::isMace;
            return InventoryUtils.findSwapSlot(fallbackMacePredicate, this.alternativeMaceSetting.getValue());
         }
      }

      return maceSlot;
   }

   private Vec3 adjustAimOrigin(Vec3 origin) {
      if (RotationManager.isMovementSyncEnabled() && !RotationManager.isSilentRotationMode()) {
         Vec3 motionOffset = ClientContext.movementPredictionManager.getAdjustedLocalMotion();
         return origin.add(motionOffset);
      } else {
         return origin;
      }
   }

   private boolean isAllowedWeapon(Item item) {
      ItemStack stack = item.getDefaultInstance();
      if (ItemUtils.isSword(stack) || item instanceof TridentItem || item instanceof AxeItem || ItemUtils.isMace(stack) || ItemUtils.isSpear(stack)) {
         return true;
      }

      if (!stack.is(ItemTags.PICKAXES)) {
         return false;
      }

      Predicate<ItemStack> alternativeWeaponPredicate = candidateStack -> ItemUtils.isSword(candidateStack) || candidateStack.getItem() instanceof AxeItem;
      return InventoryUtils.findHotbarSlot(alternativeWeaponPredicate) == -1;
   }

   private void sendAttack(Entity target) {
      boolean wasSprinting = ClientAccess.argusclientMinecraft.player.isSprinting();
      boolean desiredSprint = !this.magnetSetting.getValue() || !this.rotateSetting.getValue();
      boolean changeSprint = desiredSprint == wasSprinting;
      if (RotationManager.is2b2tMode() && RotationManager.isMovementSyncEnabled() && wasSprinting && changeSprint) {
         changeSprint = false;
      }

      if (target.getType() == EntityType.END_CRYSTAL) {
         changeSprint = false;
      }

      if (changeSprint) {
         Action action = wasSprinting ? Action.STOP_SPRINTING : Action.START_SPRINTING;
         PacketSender.sendPlayerCommand(action);
      }

      BlockInteractionUtils.attackEntity(target);
      if (changeSprint) {
         Action action = wasSprinting ? Action.START_SPRINTING : Action.STOP_SPRINTING;
         PacketSender.sendPlayerCommand(action);
      }

      if (!PlayerStateUtils.isFallFlying()) {
         ClientAccess.argusclientMinecraft.player.attack(target);
      }

      ClientAccess.argusclientMinecraft.player.resetAttackStrengthTicker();
   }

   public static void bindModuleRegistry(ModuleRegistry modules) {
      antiCheat = (AntiCheatModule)modules.getModule(AntiCheatModule.class);
      multiTask = (MultiTaskModule)modules.getModule(MultiTaskModule.class);
      autoMace = (AutoMaceModule)modules.getModule(AutoMaceModule.class);
      AuraAttackReadiness.bindModuleRegistry(modules);
   }

   public AuraModule() {
      super("Aura", "Attacks entities nearby.", ModuleCategory.COMBAT, "killaura", "forcefield", "ka");
      Predicate<Object> pauseBaritoneVisibility = unused -> ClientContext.baritoneBridge.isAvailable();
      this.pauseBaritoneSetting = this.add(new BooleanSetting("PauseBaritone", false, pauseBaritoneVisibility));
      this.rotateSetting = this.add(new BooleanSetting("Rotate", false).markVisibilityDependency());
      this.magnetSetting = this.add(new BooleanSetting("Magnet", false).dependsOn(this.rotateSetting));
      this.offsetSetting = this.add(new NumberSetting<>("Offset", 180, -90, 180).withUnit("°").dependsOn(this.magnetSetting, this.rotateSetting));
      this.limitSetting = this.add(new NumberSetting<>("Limit", 1.0F, 0.0F, 1.0F).dependsOn(this.rotateSetting));
      this.renderSetting = this.add(new BooleanSetting("Render", false).markVisibilityDependency());
      this.typeSetting = this.add(new EnumSetting<>("Type", AuraTypeChoice.BOX).dependsOn(this.renderSetting));
      Predicate<Object> fillVisibility = unused -> this.renderSetting.isExpanded();
      this.fillSetting = this.add(new ColorSetting("Fill", new Color(1270716927, true), fillVisibility));
      Predicate<Object> outlineVisibility = unused -> this.renderSetting.isExpanded();
      this.outlineSetting = this.add(new ColorSetting("Outline", new Color(-759326209, true), outlineVisibility));
      Predicate<Object> lineWidthVisibility = unused -> this.renderSetting.isExpanded();
      this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 2.0F, lineWidthVisibility));
      this.weaponsSetting = this.add(new BooleanSetting("Weapons", false).markAsGroup().markVisibilityDependency());
      this.weaponSetting = this.add(new EnumSetting<>("Weapon", AuraWeaponChoice.REQUIRE).dependsOn(this.weaponsSetting));
      Predicate<Object> swapVisibility = unused -> this.weaponSetting.getValue() == AuraWeaponChoice.SWAP;
      this.swapSetting = this.add(new EnumSetting<>("Swap", AuraSwapChoice.NORMAL, swapVisibility).dependsOn(this.weaponsSetting));
      this.maceSetting = this.add(new EnumSetting<>("Mace", AuraMaceChoice.NONE).dependsOn(this.weaponsSetting));
      this.alternativeMaceSetting = this.add(new BooleanSetting("AlternativeMace", false).dependsOn(this.weaponsSetting));
      this.targetsSetting = this.add(new BooleanSetting("Targets", true).markAsGroup().markVisibilityDependency());
      this.playersSetting = this.add(new BooleanSetting("Players", true).markVisibilityDependency().dependsOn(this.targetsSetting));
      this.invisibleSetting = this.add(new BooleanSetting("Invisible", true).dependsOn(this.targetsSetting, this.playersSetting));
      this.ignoreNakedsSetting = this.add(new BooleanSetting("IgnoreNakeds", false).dependsOn(this.targetsSetting, this.playersSetting));
      Predicate<Object> animalsVisibility = unused -> this.targetsSetting.isExpanded();
      this.animalsSetting = this.add(new BooleanSetting("Animals", false, animalsVisibility));
      this.villagersSetting = this.add(new BooleanSetting("Villagers", false).dependsOn(this.targetsSetting));
      this.hostilesSetting = this.add(new BooleanSetting("Hostiles", true).dependsOn(this.targetsSetting));
      this.neutralsSetting = this.add(new BooleanSetting("Neutrals", false).dependsOn(this.targetsSetting));
      this.crystalsSetting = this.add(new BooleanSetting("Crystals", false).dependsOn(this.targetsSetting));
      this.projectilesSetting = this.add(new BooleanSetting("Projectiles", false).dependsOn(this.targetsSetting));
      this.vehiclesSetting = this.add(new BooleanSetting("Vehicles", false).dependsOn(this.targetsSetting));
      this.ignoreNamedSetting = this.add(new BooleanSetting("IgnoreNamed", false).dependsOn(this.targetsSetting));
      this.attackCooldown = new AuraAttackCooldown(this);
      this.attackReadiness = new AuraAttackReadiness(this);
      this.targetRenderer = new AuraTargetRenderer(this);
      this.restoreSlot = -1;
      this.rangeSetting.setBoundaryLabel("Vanilla", SettingLabelCondition.MAX);
      this.wallRangeSetting.setBoundaryLabel("Vanilla", SettingLabelCondition.MAX);
      this.fovSetting.setBoundaryLabel("OnSight", SettingLabelCondition.MIN);
      this.limitSetting.setBoundaryLabel("None", SettingLabelCondition.MIN);
      this.pauseBaritoneSetting.hideWithoutBaritone();
      AuraTargetRenderer renderer = this.targetRenderer;
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(renderer);
   }
}
