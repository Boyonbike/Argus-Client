package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.SwingInteractionManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.BlockRaycastContext;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.combat.RaycastUtils;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.SwingAction;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.WorldUtils;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult.Success;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class AutoPotModule extends Module {
   private final Setting<Set<Potion>> potionsSetting = this.add(new RegistrySetSetting<>("Potions", BuiltInRegistries.POTION));
   private final Setting<Integer> delaySetting = this.add(new NumberSetting<>("Delay", 75, 75, 500).withUnit("ms"));
   private final Setting<Integer> frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 3));
   private final Setting<Integer> healthSetting = this.add(new NumberSetting<>("Health", 8, 1, 36));
   private final Setting<Integer> totemsSetting = this.add(new NumberSetting<>("Totems", 1, 0, 10));
   private final Setting<Boolean> overrideSetting = this.add(new BooleanSetting("Override", false));
   private final Setting<Boolean> autoDisableSetting = this.add(new BooleanSetting("AutoDisable", false));
   private final ElapsedTimer throwTimer = new ElapsedTimer();

   private float[] calculateThrowRotations() {
      Vec3 eyePosition = ClientAccess.argusclientMinecraft.player.getEyePosition();
      BlockPos playerBlockPosition = BlockPos.containing(ClientAccess.argusclientMinecraft.player.position());
      AABB throwBounds = ClientContext.movementPredictionManager.getPredictedBoundingBox(ClientAccess.argusclientMinecraft.player, 4);
      float[] rotations = RotationManager.getRotationsToPosition(BoundingBoxUtils.getBottomCenter(throwBounds));
      double movementSpeed = ClientContext.movementSpeedTracker.getCurrentSpeed();
      Vec3 rayEndSnapshot;
      if (movementSpeed > 15.0) {
         float pitchSnapshot = rotations[1];
         float yaw = rotations[0];
         Vec3 directionSnapshot = Vec3.directionFromRotation(pitchSnapshot, yaw);
         Vec3 scaledDirectionSnapshot = directionSnapshot.scale(4.0);
         rayEndSnapshot = scaledDirectionSnapshot.add(eyePosition);
      } else {
         rayEndSnapshot = ClientAccess.argusclientMinecraft.player.position().add(0.0, -0.01F, 0.0);
      }

      BlockRaycastContext.Builder raycastBuilder = new BlockRaycastContext.Builder(eyePosition, rayEndSnapshot);
      if (RaycastUtils.raycast(raycastBuilder.build()).getType() != Type.MISS) {
         return new float[]{rotations[0], rotations[1]};
      }

      AABB playerBounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
      double minimumY = playerBounds.minY;
      AABB bottomFaceSnapshot = playerBounds.setMaxY(minimumY);
      AABB belowBounds = bottomFaceSnapshot.expandTowards(0.0, -1.0, 0.0);
      double maximumY = playerBounds.maxY;
      AABB topFaceSnapshot = playerBounds.setMinY(maximumY);
      AABB aboveBounds = topFaceSnapshot.expandTowards(0.0, 1.0, 0.0);
      Vec3 playerPosition = ClientAccess.argusclientMinecraft.player.position();
      Stream<BlockPos> belowStream = BlockPos.betweenClosedStream(belowBounds);
      Stream<BlockPos> aboveStream = BlockPos.betweenClosedStream(aboveBounds);
      Optional<BlockPos> nearestBlockOptional = Stream.concat(belowStream, aboveStream)
         .map(BlockPos::immutable)
         .filter(WorldUtils::isPlacementOccupied)
         .min(Comparator.comparingDouble(block -> {
            double distanceZ = playerPosition.z;
            double distanceY = playerPosition.y;
            double distanceX = playerPosition.x;
            return block.distToCenterSqr(distanceX, distanceY, distanceZ);
         }));
      BlockPos nearestBlock = nearestBlockOptional.orElse(playerBlockPosition);
      Vec3 nearestCenter = nearestBlock.getCenter();
      return RotationManager.getRotationsToPosition(nearestCenter);
   }

   private int calculateThrowCount() {
      if (!this.potionsSetting.getValue().stream().anyMatch(this::isHealingPotion)) {
         return 1;
      }

      int healingStacks = 0;

      for (int slot = 0; slot < 9; slot++) {
         ItemStack stack = ClientAccess.argusclientMinecraft.player.getInventory().getItem(slot);
         Potion potion = getSplashPotion(stack);
         if (potion != null && this.isHealingPotion(potion)) {
            healingStacks++;
         }
      }

      Integer frequency = this.frequencySetting.getValue();
      return Mth.clamp(healingStacks, 1, frequency);
   }

   public boolean hasNearbySplashPotion() {
      AABB searchBounds = ClientAccess.argusclientMinecraft.player.getBoundingBox().inflate(3.0);

      for (Entity entity : ClientAccess.argusclientMinecraft.level.entitiesForRendering()) {
         if (entity instanceof ThrownSplashPotion && searchBounds.contains(entity.position())) {
            return true;
         }
      }

      return false;
   }

   private void applyLocalItemUse(ItemStack stack, InteractionHand hand) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      LocalPlayer usePlayer = ClientAccess.argusclientMinecraft.player;
      ItemStack resultStack;
      if (stack.use(level, usePlayer, hand) instanceof Success success) {
         resultStack = success.heldItemTransformedTo();
      } else {
         resultStack = ClientAccess.argusclientMinecraft.player.getItemInHand(hand);
      }

      if (resultStack != stack) {
         ClientAccess.argusclientMinecraft.player.setItemInHand(hand, resultStack);
      }
   }

   private boolean isEligiblePotion(ItemStack stack, Collection<Potion> allowedPotions) {
      Potion potion = getSplashPotion(stack);
      if (potion == null) {
         return false;
      }

      if (potion.getEffects().isEmpty()) {
         return false;
      }

      boolean hasApplicableEffect = false;

      for (MobEffectInstance effect : potion.getEffects()) {
         LocalPlayer effectPlayer = ClientAccess.argusclientMinecraft.player;
         Holder effectType = effect.getEffect();
         if (!effectPlayer.hasEffect(effectType) || this.overrideSetting.getValue()) {
            hasApplicableEffect = true;
         }
      }

      return allowedPotions.contains(potion) && hasApplicableEffect;
   }

   @EventHandler
   private void onPlayerUpdate(RotationRequestEvent event) {
      if (!this.healthSetting.isAtMaximum()) {
         float health = EntityUtils.getPlayerHealthWithAbsorption();
         Integer healthLimit = this.healthSetting.getValue();
         if (health > healthLimit.intValue()) {
            return;
         }
      }

      if (!this.totemsSetting.isAtMaximum()) {
         Predicate<ItemStack> totemPredicate = stack -> {
            Item totem = Items.TOTEM_OF_UNDYING;
            return stack.is(totem);
         };
         int totemCount = InventoryUtils.countItems(totemPredicate);
         Integer totemsLimit = this.totemsSetting.getValue();
         if (totemCount > totemsLimit) {
            return;
         }
      }

      ElapsedTimer timerSnapshot = this.throwTimer;
      long delayMillisSnapshot = this.delaySetting.getValue().intValue();
      if (timerSnapshot.hasElapsedAndReset(delayMillisSnapshot)) {
         if (!this.hasNearbySplashPotion()) {
            AABB supportBounds = ClientAccess.argusclientMinecraft.player.getBoundingBox().expandTowards(0.0, -2.0, 0.0);
            if (!ClientAccess.argusclientMinecraft.player.isFallFlying()) {
               if (!ClientAccess.argusclientMinecraft.level.noBlockCollision(ClientAccess.argusclientMinecraft.player, supportBounds)) {
                  int throwCount = this.calculateThrowCount();

                  for (int throwIndex = 0; throwIndex < throwCount; throwIndex++) {
                     Collection<Potion> allowedPotions = this.potionsSetting.getValue();
                     if (!this.throwPotion(allowedPotions)) {
                        break;
                     }
                  }

                  if (this.autoDisableSetting.getValue()) {
                     this.toggle();
                  }
               }
            }
         }
      }
   }

   private boolean isHealingPotion(Potion potion) {
      Holder<Potion> healingPotion = Potions.HEALING;
      if (potion == healingPotion.value()) {
         return true;
      }

      Holder<Potion> strongHealingPotion = Potions.STRONG_HEALING;
      return potion == strongHealingPotion.value();
   }

   private void sendPotionUse(InteractionHand hand, float yaw, float pitch) {
      ServerboundUseItemPacket packet = new ServerboundUseItemPacket(hand, PacketSender.nextPredictionSequence(), yaw, pitch);
      PacketSender.markImportant(packet);
      PacketSender.send(packet);
   }

   public boolean throwPotion(Collection<Potion> allowedPotions) {
      int previousSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
      Predicate<ItemStack> eligiblePotion = candidateStack -> this.isEligiblePotion(candidateStack, allowedPotions);
      InventoryUtils.SwapSlot selectedItem = InventoryUtils.findSwapSlot(eligiblePotion, true);
      if (!selectedItem.isFound()) {
         return false;
      }

      float[] rotations = this.calculateThrowRotations();
      InventoryUtils.swapTo(selectedItem);
      InteractionHand packetHandSnapshot = selectedItem.hand();
      float yawSnapshot = rotations[0];
      float pitch = rotations[1];
      this.sendPotionUse(packetHandSnapshot, yawSnapshot, pitch);
      SwingInteractionManager swingManager = ClientContext.swingManager;
      InteractionHand swingHand = InteractionHand.MAIN_HAND;
      SwingAction swingKind = SwingAction.INTERACT;
      swingManager.swingPacket(swingHand, swingKind);
      ItemStack stackSnapshot = selectedItem.getStack();
      InteractionHand localHand = selectedItem.hand();
      this.applyLocalItemUse(stackSnapshot, localHand);
      InventoryUtils.swapBack(previousSlot, selectedItem);
      ClientContext.rotationManager.requestRotation(rotations, 5000);
      return true;
   }

   private static Potion getSplashPotion(ItemStack stack) {
      Item splashPotion = Items.SPLASH_POTION;
      if (!stack.is(splashPotion)) {
         return null;
      }

      DataComponentType<PotionContents> potionComponent = DataComponents.POTION_CONTENTS;
      PotionContents contents = stack.get(potionComponent);
      if (contents == null) {
         return null;
      }

      Optional<Holder<Potion>> potionOptional = contents.potion();
      Holder<Potion> potionHolder = potionOptional.orElse(null);
      return potionHolder == null ? null : potionHolder.value();
   }

   public AutoPotModule() {
      super("AutoPot", "Throws a potion in certain conditions.", ModuleCategory.COMBAT);
      Setting<?> settingSnapshot = this.healthSetting;
      SettingLabelCondition healthLabelBoundary = SettingLabelCondition.MAX;
      settingSnapshot.setBoundaryLabel("None", healthLabelBoundary);
      settingSnapshot = this.totemsSetting;
      SettingLabelCondition totemsLabelBoundary = SettingLabelCondition.MAX;
      settingSnapshot.setBoundaryLabel("None", totemsLabelBoundary);
      Holder<Potion> healingPotionHolder = Potions.HEALING;
      Potion healingPotionSnapshot = healingPotionHolder.value();
      Holder<Potion> strongHealingPotionHolder = Potions.STRONG_HEALING;
      Potion strongHealingPotion = strongHealingPotionHolder.value();
      List<Potion> defaultPotions = List.of(healingPotionSnapshot, strongHealingPotion);
      settingSnapshot = this.potionsSetting;
      ((Set)settingSnapshot.getDefaultValue()).addAll(defaultPotions);
      this.potionsSetting.getValue().addAll(defaultPotions);
   }
}
