package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.ClientPacketListenerHandleExplosionEvent;
import dev.argus.client.events.ClientPacketListenerHandleSetEntityMotionEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.utils.movement.VelocityModeHandler;
import net.minecraft.world.phys.Vec3;

public class PlainVelocityHandler extends VelocityModeHandler {
   public PlainVelocityHandler(VelocityModule velocityModule) {
      super(velocityModule);
   }

   @Override
   public void onExplosion(ClientPacketListenerHandleExplosionEvent event) {
      if (this.velocityModule.explosionsSetting.getValue()) {
         int horizontalPercent = this.velocityModule.horizontalSetting.getValue();
         int verticalPercent = this.velocityModule.verticalSetting.getValue();
         int sign = this.velocityModule.inverseSetting.getValue() ? -1 : 1;
         event.setVelocityX((int)(event.getVelocityX() * (horizontalPercent * 0.01F) * sign));
         event.setVelocityY((int)(event.getVelocityY() * (verticalPercent * 0.01F)));
         event.setVelocityZ((int)(event.getVelocityZ() * (horizontalPercent * 0.01F) * sign));
      }
   }

   @Override
   public void onEntityMotion(ClientPacketListenerHandleSetEntityMotionEvent event) {
      if (event.getEntityId() == ClientAccess.argusclientMinecraft.player.getId()) {
         int horizontalPercent = this.velocityModule.horizontalSetting.getValue();
         int verticalPercent = this.velocityModule.verticalSetting.getValue();
         int sign = this.velocityModule.inverseSetting.getValue() ? -1 : 1;
         Vec3 velocity = event.getVelocity();
         if (!this.shouldLeaveMotionUnchanged((float)velocity.length())) {
            if (!RotationManager.isMovementSyncEnabled() || velocity.x != 0.0 || velocity.z != 0.0 || !(Math.abs(velocity.y) < 0.0788)) {
               event.setVelocity(velocity.multiply(horizontalPercent * 0.01 * sign, verticalPercent * 0.01F, horizontalPercent * 0.01 * sign));
               if (horizontalPercent == 0 && verticalPercent == 0) {
                  event.cancel();
               }
            }
         }
      }
   }

   private boolean shouldLeaveMotionUnchanged(float motionLength) {
      if (this.velocityModule.isLungeMotionPending() && motionLength > 0.458F) {
         this.velocityModule.setLungeMotionPending(false);
         return true;
      } else {
         long reductionWindow = motionLength <= 0.3 ? 200L : 100L;
         return RotationManager.isMovementSyncEnabled()
            && this.velocityModule.reduceSetting.getValue()
            && !ClientContext.serverStateManager.hasElapsedSinceSetback(reductionWindow);
      }
   }
}
