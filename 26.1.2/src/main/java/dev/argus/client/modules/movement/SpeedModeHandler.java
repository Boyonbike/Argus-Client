package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.utils.movement.MovementPhysics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;

public abstract class SpeedModeHandler implements ClientAccess {
   protected final SpeedModule speedModule;
   protected double moveSpeed;
   protected double lastHorizontalDistance;
   protected int movementStage;

   public void onRotationUpdate() {
   }

   public boolean hasVerticalMotionCollision() {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      AABB bounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
      double verticalMotion = ClientAccess.argusclientMinecraft.player.getDeltaMovement().y;
      return !level.noCollision(bounds.move(0.0, verticalMotion, 0.0));
   }

   protected SpeedModeHandler(SpeedModule speedModule) {
      this.speedModule = speedModule;
   }

   public void onEnable() {
   }

   public void onSendPosition(LocalPlayerSendPositionEvent event) {
      if (EventStage.Post != event.getStage()) {
         if (!MovementPhysics.isLocalPlayerMoving()) {
            this.movementStage = 4;
            this.moveSpeed = 0.0;
         }

         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         double deltaX = player.getX() - player.xo;
         double deltaZ = player.getZ() - player.zo;
         this.lastHorizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
      }
   }

   public abstract void onMove(LocalPlayerMoveEvent var1);
}
