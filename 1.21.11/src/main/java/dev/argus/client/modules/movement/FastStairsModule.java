package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.world.WorldUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.AABB;

public class FastStairsModule extends Module {
   private static final MutableBlockPos scanPosition = new MutableBlockPos();
   private final ElapsedTimer jumpTimer = new ElapsedTimer();
   private boolean roseSincePreviousTick;
   private float capturedSpeed;

   private boolean hasNearbyStairs(BlockPos center) {
      for (int x = -2; x <= 2; x++) {
         for (int z = -2; z < 2; z++) {
            scanPosition.set(center.getX() + x, center.getY(), center.getZ() + z);
            if (WorldUtils.getBlock(scanPosition) instanceof StairBlock) {
               return true;
            }
         }
      }

      return false;
   }

   public FastStairsModule() {
      super("FastStairs", "Makes you move faster on stair blocks.", ModuleCategory.MOVEMENT);
   }

   @EventHandler
   public void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      this.capturedSpeed = (float)ClientContext.movementSpeedTracker.getCurrentSpeed();
      double previousY = ClientAccess.argusclientMinecraft.player.yo;
      this.roseSincePreviousTick = previousY < ClientAccess.argusclientMinecraft.player.getY();
   }

   @EventHandler
   public void onLocalPlayerPreTickEvent(LocalPlayerPreTickEvent event) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      AABB feetBounds = player.getBoundingBox().move(0.0, -0.1, 0.0);
      if (!BlockPos.betweenClosedStream(feetBounds).noneMatch(pos -> WorldUtils.getBlock(pos) instanceof StairBlock)) {
         BlockPos playerPosition = BlockPos.containing(player.getX(), player.getY(), player.getZ());
         if (player.onGround() && this.roseSincePreviousTick && !player.isCreative() && !player.isSpectator()) {
            if (this.hasNearbyStairs(playerPosition.above()) && !(this.capturedSpeed < 20.0F)) {
               if (!this.jumpTimer.hasElapsedAndReset(150L)) {
                  ClientContext.inputOverrides.requestJump(true);
               }
            }
         }
      }
   }
}
