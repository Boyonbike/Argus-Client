package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.movement.ElytraFlyModeHandler;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.client.utils.network.PacketSender;
import net.minecraft.core.Direction.Axis;

public class PacketElytraFlyHandler extends ElytraFlyModeHandler {
   private long accelerationStartTimeMillis;
   private float flightSpeed;
   private final ElapsedTimer idleMovementTimer = new ElapsedTimer();

   public PacketElytraFlyHandler(ElytraFlyModule module) {
      super(module);
   }

   @Override
   public void onEnable() {
      this.flightSpeed = 0.0F;
      this.accelerationStartTimeMillis = System.currentTimeMillis();
   }

   @Override
   public void handleMovement(LocalPlayerMoveEvent event) {
      if (this.module.isPacketFlightActive()) {
         if (!MovementPhysics.isLocalPlayerMoving()) {
            this.accelerationStartTimeMillis = System.currentTimeMillis();
         }

         if (this.module.accelerateSetting.getValue()) {
            this.flightSpeed = (float)MovementPhysics.accelerateTo(
               this.module.flightSpeedSetting.getValue().floatValue(),
               this.module.accelMinSetting.getValue().floatValue(),
               this.module.accelTimeSetting.getValue().floatValue(),
               this.accelerationStartTimeMillis
            );
         } else {
            this.flightSpeed = this.module.flightSpeedSetting.getValue();
         }

         double[] horizontalMotion = MovementPhysics.applyHorizontalMotion(event, this.flightSpeed);
         float glideMotion = -this.module.glideSetting.getValue();
         ClientAccess.argusclientMinecraft.player.setDeltaMovement(horizontalMotion[0], glideMotion, horizontalMotion[1]);
         event.setY(glideMotion);
         if (ClientAccess.argusclientMinecraft.player.input.keyPresses.shift()) {
            event.setY(-0.5);
            ClientAccess.argusclientMinecraft.player.setDeltaMovement(ClientAccess.argusclientMinecraft.player.getDeltaMovement().with(Axis.Y, -0.5));
         }

         PacketSender.sendStartFallFlying();
         if (this.module.antiAfkSetting.getValue() && event.getX() == 0.0 && event.getZ() == 0.0 && this.idleMovementTimer.hasElapsed(2500L)) {
            double radians = Math.toRadians(ClientAccess.argusclientMinecraft.player.tickCount % 360);
            event.setHorizontalVelocity(Math.sin(radians) * 0.03, Math.cos(radians) * 0.03);
         } else {
            if (MovementPhysics.isLocalPlayerMoving()) {
               this.idleMovementTimer.reset();
            }
         }
      }
   }
}
