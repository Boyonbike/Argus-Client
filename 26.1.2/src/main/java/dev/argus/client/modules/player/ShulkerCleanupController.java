package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity.AnimationStatus;
import net.minecraft.world.phys.Vec3;

public class ShulkerCleanupController extends ChestStealerController {
   private boolean wasShulkerScreenOpen;

   public ShulkerCleanupController(ChestStealerModule module) {
      super(module);
   }

   private BlockPos findOpenShulkerToBreak() {
      BlockPos nearestPosition = null;
      double nearestDistance = 999.0;

      for (Object loadedEntity : WorldUtils.getLoadedBlockEntities()) {
         BlockEntity blockEntity = (BlockEntity)loadedEntity;
         if (blockEntity instanceof ShulkerBoxBlockEntity shulker && shulker.getAnimationStatus() != AnimationStatus.CLOSED) {
            Vec3 eyePosition = ClientAccess.argusclientMinecraft.player.getEyePosition();
            Vec3 shulkerCenter = blockEntity.getBlockPos().getCenter();
            double distance = eyePosition.distanceTo(shulkerCenter);
            if (distance < nearestDistance && distance <= 5.0) {
               nearestDistance = distance;
               nearestPosition = blockEntity.getBlockPos();
            }
         }
      }

      return nearestPosition;
   }

   public void tick() {
      boolean shulkerScreenOpen = ClientAccess.argusclientMinecraft.screen instanceof ShulkerBoxScreen;
      if (this.wasShulkerScreenOpen && !shulkerScreenOpen) {
         BlockPos position = this.findOpenShulkerToBreak();
         if (position != null) {
            Direction fallbackDirection = Direction.UP;
            Direction direction = BlockInteractionUtils.getStrictDirectionsOrFallback(position, fallbackDirection).getFirst();
            MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
            gameMode.startDestroyBlock(position, direction);
         }
      }

      this.wasShulkerScreenOpen = shulkerScreenOpen;
   }
}
