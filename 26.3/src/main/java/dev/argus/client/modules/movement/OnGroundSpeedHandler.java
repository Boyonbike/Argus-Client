package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.utils.movement.MovementPhysics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;

public final class OnGroundSpeedHandler extends SpeedModeHandler {
   public OnGroundSpeedHandler(SpeedModule speedModule) {
      super(speedModule);
      this.movementStage = 2;
   }

   @Override
   public void onSendPosition(LocalPlayerSendPositionEvent event) {
      if (EventStage.Post != event.getStage() && !this.shouldResetGroundMovement()) {
         super.onSendPosition(event);
         if (this.movementStage == 3) {
            double offset = this.hasVerticalMotionCollision() ? 0.2 : 0.4;
            event.setY(event.getY() + offset + MovementPhysics.getJumpBoost());
         }
      }
   }

   @Override
   public void onMove(LocalPlayerMoveEvent event) {
      if (this.shouldResetGroundMovement()) {
         this.speedModule.reset();
         MovementPhysics.applyHorizontalMotion(event, MovementPhysics.getBaseMoveSpeed(true));
      } else {
         if (this.movementStage == 4) {
            this.speedModule.reset();
         }

         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         if (player.onGround() || this.movementStage == 3) {
            if (!player.horizontalCollision && player.zza != 0.0F || player.xxa != 0.0F) {
               if (this.movementStage == 2) {
                  this.moveSpeed *= 2.149;
                  this.movementStage = 3;
               } else if (this.movementStage == 3) {
                  this.movementStage = 2;
                  this.moveSpeed = this.lastHorizontalDistance - 0.66 * (this.lastHorizontalDistance - MovementPhysics.getBaseMoveSpeed(true));
               }
            }

            this.moveSpeed = Math.min(this.moveSpeed, this.speedModule.speedSetting.getValue().floatValue());
            this.moveSpeed = Math.max(this.moveSpeed, MovementPhysics.getBaseMoveSpeed(true));
            double[] motion = MovementPhysics.getHorizontalMotion(player.getViewYRot(RenderUtils.getPartialTick()), player.input, this.moveSpeed);
            if (!ClientAccess.argusclientMinecraft.level.noCollision(player.getBoundingBox().expandTowards(motion[0], 0.0, motion[1]))) {
               this.speedModule.reset();
               this.moveSpeed = MovementPhysics.getBaseMoveSpeed(true);
            }

            MovementPhysics.applyHorizontalMotion(event, this.moveSpeed);
         }
      }
   }

   private boolean shouldResetGroundMovement() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      AABB bounds = player.getBoundingBox();
      double deltaX = player.getX() - player.xo;
      double deltaY = player.getY() - player.yo;
      double deltaZ = player.getZ() - player.zo;
      if (level.noCollision(bounds.move(deltaX, deltaY, deltaZ)) && !level.noCollision(bounds.expandTowards(0.0, -1.0, 0.0))) {
         if (!player.horizontalCollision && !(player.getY() > Math.floor(player.getY())) && ClientContext.serverStateManager.getSetbackTimer().hasElapsed(500L)
            )
          {
            double predictedY = ClientContext.movementPredictionManager.getPredictedBoundingBox(player, 3).minY;
            if (!(predictedY < player.getY())) {
               return false;
            }
         }

         return true;
      } else {
         return true;
      }
   }
}
