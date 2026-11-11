package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LivingEntityJumpFromGroundEvent;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.entity.SwingAction;
import dev.argus.client.utils.math.Vec3Utils;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity.AnimationStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class HighJumpModule extends Module {
   private static AntiCheatModule antiCheatModule;
   final Setting<HighJumpModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", HighJumpModule.ModeChoice.PLAIN));
   private final Setting<Float> strengthSetting;
   private final Setting<Boolean> inMovementSetting;
   private final Setting<Boolean> infiniteSetting;
   private int remainingBoostTicks;
   private boolean shulkerJumpPending;
   private boolean shulkerBoostPaused;

   @Override
   public String getInfo() {
      return this.modeSetting.getValue().getName();
   }

   private void startShulkerJump(LivingEntityJumpFromGroundEvent event) {
      BlockPos shulkerPos = this.findShulker(true);
      if (shulkerPos != null) {
         if (((ShulkerBoxBlockEntity)ClientAccess.argusclientMinecraft.level.getBlockEntity(shulkerPos)).getAnimationStatus() == AnimationStatus.CLOSED) {
            this.interactWithShulker(shulkerPos);
         } else {
            event.setJumpPower(1.0F);
         }

         this.shulkerBoostPaused = false;
         this.shulkerJumpPending = true;
         Float configuredStrength = this.strengthSetting.getValue();
         this.remainingBoostTicks = configuredStrength.intValue();
      }
   }

   private boolean isShulkerInRange(BlockEntity blockEntity) {
      Vec3 playerPositionSnapshot = ClientAccess.argusclientMinecraft.player.position();
      Vec3 blockCenterSnapshot = blockEntity.getBlockPos().getCenter();
      float distance = Vec3Utils.horizontalDistance(playerPositionSnapshot, blockCenterSnapshot);
      return distance <= 1.0F;
   }

   @EventHandler
   private void onJumpFromGround(LivingEntityJumpFromGroundEvent event) {
      HighJumpModule.ModeChoice choice = this.modeSetting.getValue();
      switch (choice) {
         case PLAIN:
            this.applyPlainJump(event);
            break;
         case FENCE:
            this.applyFenceJump(event);
            break;
         case SHULKER:
            this.startShulkerJump(event);
      }
   }

   @EventHandler
   private void onPlayerTick(LocalPlayerPreTickEvent event) {
      boolean openShulkerNearby = this.findShulker(false) != null;
      boolean anyShulkerNearby = this.findShulker(true) != null;
      if (!openShulkerNearby && !this.shulkerJumpPending || !anyShulkerNearby) {
         this.remainingBoostTicks = 0;
      }

      if (this.remainingBoostTicks > 0 && openShulkerNearby) {
         if (!this.shulkerBoostPaused) {
            float upwardImpulse = 0.908F;
            if (antiCheatModule.is2b2tEnabled()) {
               upwardImpulse = 1.0F;
            } else if (this.remainingBoostTicks == 1) {
               upwardImpulse = 1.051F;
            }

            double upwardImpulseDouble = upwardImpulse;
            ClientAccess.argusclientMinecraft.player.push(0.0, upwardImpulseDouble, 0.0);
            double risingVelocityY = ClientAccess.argusclientMinecraft.player.getDeltaMovement().y;
            if (risingVelocityY >= 7.0) {
               this.shulkerBoostPaused = true;
            }
         } else {
            double fallingVelocityY = ClientAccess.argusclientMinecraft.player.getDeltaMovement().y;
            this.shulkerBoostPaused = fallingVelocityY > -0.2;
         }

         this.shulkerJumpPending = false;
         if (!this.infiniteSetting.getValue()) {
            this.remainingBoostTicks--;
         }
      }
   }

   private void applyPlainJump(LivingEntityJumpFromGroundEvent event) {
      if (this.inMovementSetting.getValue() || !MovementPhysics.isLocalPlayerMoving()) {
         float jumpStrength = this.strengthSetting.getValue();
         event.setJumpPower(jumpStrength);
      }
   }

   private BlockPos findShulker(boolean includeClosed) {
      for (Object blockEntityValue : WorldUtils.getLoadedBlockEntities()) {
         BlockEntity blockEntity = (BlockEntity)blockEntityValue;
         if (blockEntity instanceof ShulkerBoxBlockEntity shulker
            && this.isShulkerInRange(shulker)
            && (shulker.getAnimationStatus() != AnimationStatus.CLOSED || includeClosed)
            && shulker.getBlockState().getValue(ShulkerBoxBlock.FACING) == Direction.UP) {
            return blockEntity.getBlockPos();
         }
      }

      return null;
   }

   private void interactWithShulker(BlockPos position) {
      List<Direction> directions = BlockInteractionUtils.getStrictDirections(position);
      Direction direction = directions.isEmpty() ? Direction.UP : directions.getFirst();
      Vec3 hitPoint = position.getCenter().relative(direction, 0.5);
      int interactionCount = this.infiniteSetting.getValue() ? 3 : 1;

      for (int i = 0; i < interactionCount; i++) {
         BlockHitResult hit = new BlockHitResult(hitPoint, direction, position, false);
         PacketSender.sendUseItemOn(InteractionHand.MAIN_HAND, hit);
         ClientContext.swingManager.swingPacket(InteractionHand.MAIN_HAND, SwingAction.INTERACT);
      }
   }

   private void applyFenceJump(LivingEntityJumpFromGroundEvent event) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      AABB fenceBounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
      boolean intersectsFence = level.getBlockStates(fenceBounds).anyMatch(state -> state.getBlock() instanceof FenceBlock);
      if (intersectsFence) {
         event.setJumpPower(0.6F);
      }
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      antiCheatModule = (AntiCheatModule)registry.getModule(AntiCheatModule.class);
   }

   public HighJumpModule() {
      super("HighJump", "Makes you jump higher.", ModuleCategory.MOVEMENT);
      Predicate<Object> showStrength = ignored -> this.modeSetting.getValue() != HighJumpModule.ModeChoice.FENCE;
      this.strengthSetting = this.add(new NumberSetting<>("Strength", 1.0F, 0.0F, 5.0F, showStrength));
      Predicate<Object> showInMovement = ignored -> this.modeSetting.getValue() == HighJumpModule.ModeChoice.PLAIN;
      this.inMovementSetting = this.add(new BooleanSetting("InMovement", true, showInMovement));
      Predicate<Object> showInfinite = ignored -> this.modeSetting.getValue() == HighJumpModule.ModeChoice.SHULKER;
      this.infiniteSetting = this.add(new BooleanSetting("Infinite", false, showInfinite));
   }

   public enum ModeChoice implements NamedEntry {
      PLAIN("Plain"),
      FENCE("Fence"),
      SHULKER("Shulker");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
