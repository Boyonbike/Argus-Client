package dev.argus.client.utils.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.movement.LongJumpModule;
import dev.argus.client.modules.movement.SpeedModeHandler;
import dev.argus.client.modules.movement.SpeedModule;
import dev.argus.client.utils.entity.PlayerStateUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class StrictStrafeSpeedHandler extends SpeedModeHandler {
   private static LongJumpModule longJumpModule;

   @Override
   public void onRotationUpdate() {
      if (!this.shouldPauseStrictStrafe() && MovementPhysics.isLocalPlayerMoving() && ClientAccess.argusclientMinecraft.player.verticalCollisionBelow) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         double[] motion = MovementPhysics.getHorizontalMotion(player.getYRot(), player.input, 1.0);
         float movementYaw = (float)(Math.toDegrees(Math.atan2(motion[1], motion[0])) - 90.0);
         float radians = (float)Math.toRadians(movementYaw);
         Vec3 velocity = player.getDeltaMovement();
         player.setDeltaMovement(velocity.x, 0.4 + MovementPhysics.getJumpBoost(), velocity.z);
         player.addDeltaMovement(new Vec3(-Mth.sin(radians) * 0.2F, 0.0, Mth.cos(radians) * 0.2F));
         player.setSprinting(true);
      }
   }

   public StrictStrafeSpeedHandler(SpeedModule speedModule) {
      super(speedModule);
   }

   @Override
   public void onMove(LocalPlayerMoveEvent event) {
      if (!this.shouldPauseStrictStrafe()) {
         if (this.speedModule.useTimerSetting.getValue() && ClientContext.serverStateManager.getSetbackTimer().hasElapsed(250L)) {
            ClientContext.tickRateController.requestMultiplier(this.speedModule, 1.0888F);
         } else {
            ClientContext.tickRateController.releaseMultiplier(this.speedModule);
         }

         if (!MovementPhysics.isLocalPlayerMoving()) {
            event.setHorizontalVelocity(0.0, 0.0);
         } else {
            double speed = MovementPhysics.getBaseMoveSpeed(false);
            if (ClientContext.serverStateManager.getSetbackTimer().hasElapsed(500L)) {
               speed = Math.max(speed, Math.hypot(event.getX(), event.getZ()));
            }

            MovementPhysics.applyHorizontalMotion(event, speed);
         }
      }
   }

   private boolean shouldPauseStrictStrafe() {
      return this.speedModule.shouldPauseInBlocks()
         || this.speedModule.shouldPauseInLiquid()
         || PlayerStateUtils.isHungerLimited()
         || ClientAccess.argusclientMinecraft.player.isFallFlying()
         || ClientAccess.argusclientMinecraft.player.isSpectator()
         || longJumpModule.isToggled();
   }

   public static void bindModules(ModuleRegistry modules) {
      longJumpModule = (LongJumpModule)modules.getModule(LongJumpModule.class);
   }
}
