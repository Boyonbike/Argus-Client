package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.utils.movement.MovementPhysics;

public final class VanillaSpeedHandler extends SpeedModeHandler {
   public VanillaSpeedHandler(SpeedModule speedModule) {
      super(speedModule);
   }

   @Override
   public void onMove(LocalPlayerMoveEvent event) {
      if (!this.speedModule.shouldPauseInBlocks()
         && !this.speedModule.shouldPauseInLiquid()
         && !ClientAccess.argusclientMinecraft.player.isFallFlying()
         && !this.speedModule.shouldYieldToObstaclePasser()) {
         double speed = this.speedModule.speedSetting.getValue().floatValue();
         MovementPhysics.applyHorizontalMotion(event, MovementPhysics.applySpeedEffects(false, speed));
      }
   }
}
