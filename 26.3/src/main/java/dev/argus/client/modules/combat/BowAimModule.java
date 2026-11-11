package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.GameModeUseItemPacketEvent;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.combat.TargetSelector;
import dev.argus.client.utils.inventory.PotionUtils;
import dev.argus.client.utils.math.MathUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.util.Comparator;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class BowAimModule extends Module {
   final Setting<Boolean> targetsSetting = this.add(new BooleanSetting("Targets", true).markAsGroup());
   final Setting<Boolean> playersSetting;
   private final Setting<Boolean> ignoreNakedsSetting;
   private final Setting<Boolean> animalsSetting;
   private final Setting<Boolean> hostilesSetting;
   private final Setting<Boolean> neutralsSetting;
   private float[] cachedRotations;

   private Potion findArrowPotion() {
      int slot = 0;

      while (true) {
         Inventory sizeInventory = ClientAccess.argusclientMinecraft.player.getInventory();
         if (slot >= sizeInventory.getContainerSize()) {
            return PotionUtils.MISSING_POTION;
         }

         ItemStack stack = ClientAccess.argusclientMinecraft.player.getInventory().getItem(slot);
         if (stack.getItem() instanceof ArrowItem) {
            return PotionUtils.getPotion(stack);
         }

         slot++;
      }
   }

   private static float[] calculateBallisticRotations(Entity target, boolean predictMotion, float predictionTicks) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Vec3 targetPosition = target.position().add(0.0, target.getBbHeight() / 2.0F + 0.1F, 0.0);
      Vec3 eyePosition = player.getEyePosition();
      if (predictMotion) {
         targetPosition = targetPosition.add(MathUtils.getPositionDelta(target).scale(predictionTicks));
         eyePosition = eyePosition.add(MathUtils.getPositionDelta(player).scale(predictionTicks));
      }

      Vec3 delta = targetPosition.subtract(eyePosition);
      double horizontalDistance = Math.hypot(delta.x, delta.z);
      float charge = player.isHolding(Items.CROSSBOW) ? 1.0F : player.getTicksUsingItem() / 20.0F;
      float launchPower = Math.min((charge * charge + 2.0F * charge) / 3.0F, 1.0F);
      double gravity = 0.006F;
      float powerSquared = launchPower * launchPower;
      double discriminant = launchPower * launchPower * launchPower * launchPower
         - gravity * (gravity * (horizontalDistance * horizontalDistance) + 2.0 * delta.y * powerSquared);
      if (discriminant < 0.0) {
         return null;
      }

      float yaw = (float)(Math.atan2(delta.z, delta.x) * 180.0 / Math.PI) - 90.0F;
      float pitch = (float)(-Math.toDegrees(Math.atan((powerSquared - Math.sqrt(discriminant)) / (gravity * horizontalDistance))));
      return Float.isFinite(yaw) && Float.isFinite(pitch) ? new float[]{yaw, pitch} : null;
   }

   @EventHandler
   private void onUseItemPacket(GameModeUseItemPacketEvent event) {
      ServerboundUseItemPacket packet = event.getPacket();
      LocalPlayer playerSnapshot = ClientAccess.argusclientMinecraft.player;
      InteractionHand packetHand = packet.getHand();
      ItemStack heldStack = playerSnapshot.getItemInHand(packetHand);
      Item crossbow = Items.CROSSBOW;
      if (heldStack.is(crossbow) && this.cachedRotations != null) {
         event.setRotations(this.cachedRotations);
      }
   }

   @EventHandler
   private void onPlayerUpdate(RotationRequestEvent event) {
      this.cachedRotations = null;
      Item mainHandItem = ClientAccess.argusclientMinecraft.player.getMainHandItem().getItem();
      boolean projectileWeapon = mainHandItem instanceof ProjectileWeaponItem;
      boolean activeUse = ClientAccess.argusclientMinecraft.player.isUsingItem();
      if (CrossbowItem.isCharged(ClientAccess.argusclientMinecraft.player.getMainHandItem())) {
         activeUse = true;
      }

      if (projectileWeapon && activeUse) {
         for (MobEffectInstance effect : this.findArrowPotion().getEffects()) {
            Holder<MobEffect> effectHolder = effect.getEffect();
            MobEffect effectType = effectHolder.value();
            if (effectType.isBeneficial() && effectHolder != MobEffects.SLOW_FALLING && effectHolder != MobEffects.JUMP_BOOST) {
               return;
            }
         }

         TargetSelector selectionBuilder = new TargetSelector();
         selectionBuilder = selectionBuilder.withPlayers(this.playersSetting.getValue());
         selectionBuilder = selectionBuilder.withNeutrals(this.neutralsSetting.getValue());
         selectionBuilder = selectionBuilder.withAnimals(this.animalsSetting.getValue());
         selectionBuilder = selectionBuilder.withHostiles(this.hostilesSetting.getValue());
         TargetSelector sortBuilder = selectionBuilder.ignoreNakeds(this.ignoreNakedsSetting.getValue());
         Comparator<Entity> angularOrder = Comparator.comparing(BowAimModule::getAngularDistance);
         selectionBuilder = sortBuilder.sortBy(angularOrder);
         Entity target = selectionBuilder.visibleRange(128.0F).select();
         if (target != null && !(target instanceof EnderMan)) {
            AABB targetBounds = target.getBoundingBox();
            Vec3 targetCenter = targetBounds.getCenter();
            if (RotationManager.isVisible(targetBounds)) {
               if (WorldUtils.canSeeFromEyes(targetCenter)) {
                  double targetDistance = target.position().distanceTo(ClientAccess.argusclientMinecraft.player.getEyePosition());
                  float distanceLeadTicks = (float)(targetDistance / 8.0);
                  float predictionTicks = 4.0F + distanceLeadTicks;
                  float[] rotations = calculateBallisticRotations(target, true, predictionTicks);
                  if (rotations != null) {
                     if (!Float.isNaN(rotations[0])) {
                        if (!Float.isNaN(rotations[1])) {
                           this.cachedRotations = rotations;
                           ClientContext.rotationManager.requestRotation(rotations, 5, true);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static float getAngularDistance(Entity target) {
      Vec3 pointSnapshot = ClientAccess.argusclientMinecraft.player.getEyePosition();
      AABB targetBounds = target.getBoundingBox();
      pointSnapshot = BoundingBoxUtils.clampToBounds(pointSnapshot, targetBounds);
      return MathUtils.cameraRotationDifference(pointSnapshot);
   }

   public BowAimModule() {
      super("BowAim", "Helps aiming with bows.", ModuleCategory.COMBAT);
      Predicate<Object> playersVisibility = unused -> this.targetsSetting.isExpanded();
      this.playersSetting = this.add(new BooleanSetting("Players", true, playersVisibility).markVisibilityDependency());
      Predicate<Object> nakedsVisibility = unused -> this.targetsSetting.isExpanded() && this.playersSetting.isExpanded();
      this.ignoreNakedsSetting = this.add(new BooleanSetting("IgnoreNakeds", false, nakedsVisibility));
      Predicate<Object> animalsVisibility = unused -> this.targetsSetting.isExpanded();
      this.animalsSetting = this.add(new BooleanSetting("Animals", false, animalsVisibility));
      Predicate<Object> hostilesVisibility = unused -> this.targetsSetting.isExpanded();
      this.hostilesSetting = this.add(new BooleanSetting("Hostiles", true, hostilesVisibility));
      this.neutralsSetting = this.add(new BooleanSetting("Neutrals", false).dependsOn(this.targetsSetting));
   }
}
