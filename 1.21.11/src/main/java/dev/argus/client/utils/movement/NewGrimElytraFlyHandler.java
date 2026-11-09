package dev.argus.client.utils.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.FireworkRocketEntityTickEvent;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.MinecraftRunTickEvent;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.managers.TickRateController;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.modules.combat.AutoMaceModule;
import dev.argus.client.modules.movement.ElytraFlyModule;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.math.Vec3Utils;
import dev.argus.client.utils.network.PacketSender;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

public class NewGrimElytraFlyHandler extends ElytraFlyModeHandler {
   private static AutoMaceModule autoMaceModule;
   private boolean movementStarted;
   private boolean negativeYawJitter;
   private boolean pitchJitterPhase;
   private float serverYaw;
   private float serverPitch;

   public NewGrimElytraFlyHandler(ElytraFlyModule module) {
      super(module);
   }

   @Override
   public void handleFireworkTick(FireworkRocketEntityTickEvent event) {
      if (!autoMaceModule.isToggled() || !autoMaceModule.isFlightControlActive()) {
         float yaw = this.serverYaw;
         event.setYaw(yaw);
         float pitch = this.serverPitch;
         event.setPitch(pitch);
      }
   }

   @Override
   public void handleMovement(LocalPlayerMoveEvent event) {
      if (EntityUtils.hasCollisionBelow(ClientAccess.argusclientMinecraft.player)) {
         this.movementStarted = false;
      }

      if (this.isIdleAirborne() && this.hasDurabilityFlightCondition()) {
         Setting<ElytraFlyModule.StopMotionChoice> stopMotionSetting = this.module.stopMotionSetting;
         if (stopMotionSetting.getValue() == ElytraFlyModule.StopMotionChoice.STATIC) {
            if (autoMaceModule.isToggled() && autoMaceModule.isFlightControlActive()) {
               return;
            }

            Vec3 stoppedVelocity = Vec3.ZERO;
            event.setVelocity(stoppedVelocity);
         }
      }
   }

   @Override
   public void handleClientTick(MinecraftRunTickEvent event) {
      ElytraFlyModule moduleForTimerRelease = this.module;
      TickRateController timerControllerForRelease = ClientContext.tickRateController;
      timerControllerForRelease.releaseMultiplier(moduleForTimerRelease);
      if (!autoMaceModule.isToggled() || !autoMaceModule.isFlightControlActive()) {
         if (!this.isWorldUnavailable()) {
            Setting<ElytraFlyModule.StopMotionChoice> stopMotionSetting = this.module.stopMotionSetting;
            if (stopMotionSetting.getValue() == ElytraFlyModule.StopMotionChoice.TIMER && this.hasDurabilityFlightCondition()) {
               this.movementStarted = true;
               if (this.isIdleAirborne()) {
                  TickRateController timerControllerForRequest = ClientContext.tickRateController;
                  ElytraFlyModule moduleForTimerRequest = this.module;
                  timerControllerForRequest.requestMultiplier(moduleForTimerRequest, 0.02F);
               }

               return;
            }
         }
      }
   }

   @Override
   public void handleRotationManagerEvent(RotationRequestEvent event) {
      if (!EntityUtils.hasCollisionBelow(ClientAccess.argusclientMinecraft.player)) {
         if (autoMaceModule.isToggled() && autoMaceModule.isFlightControlActive()) {
            return;
         }

         if (PlayerStateUtils.isLocalWearingElytra()) {
            ElytraFlyModule.InfDurabilityChoice durabilityChoice = this.module.infDurabilitySetting.getValue();
            if (durabilityChoice.isDisabled() && !PlayerStateUtils.isFallFlying()) {
               ClientAccess.argusclientMinecraft.player.startFallFlying();
               PacketSender.sendStartFallFlying();
            }
         }

         if (this.hasDurabilityFlightCondition()) {
            float playerYaw = ClientAccess.argusclientMinecraft.player.getYRot();
            ClientInput movementInput = ClientAccess.argusclientMinecraft.player.input;
            double[] horizontalMotion = MovementPhysics.getHorizontalMotion(playerYaw, movementInput, 1.0);
            double directionZ = horizontalMotion[1];
            double directionX = horizontalMotion[0];
            double directionRadians = Math.atan2(directionZ, directionX);
            float yaw = (float)(Math.toDegrees(directionRadians) - 90.0);
            if (!MovementPhysics.isLocalPlayerMoving()) {
               yaw = playerYaw;
            }

            yaw += (this.negativeYawJitter ? -1 : 1) * AntiCheatModule.ROTATION_JITTER_STEP;
            this.negativeYawJitter = !this.negativeYawJitter;
            float pitch = -1.0F;
            if (ClientAccess.argusclientMinecraft.player.input.keyPresses.jump()) {
               pitch = MovementPhysics.isLocalPlayerMoving() ? -45.0F : -90.0F;
               if (!MovementPhysics.isLocalPlayerMoving()) {
                  float stationaryJumpYaw = yaw + 180.0F;
                  yaw = Mth.wrapDegrees(stationaryJumpYaw);
               }
            }

            if (ClientAccess.argusclientMinecraft.player.input.keyPresses.shift()) {
               Boolean responsiveDescend = this.module.responsiveDescendSetting.getValue();
               if (responsiveDescend) {
                  float responsiveDescendPitch;
                  if (MovementPhysics.isLocalPlayerMoving()) {
                     responsiveDescendPitch = ClientAccess.argusclientMinecraft.player.getXRot();
                  } else {
                     responsiveDescendPitch = 90.0F;
                  }

                  pitch = responsiveDescendPitch;
               } else {
                  pitch = MovementPhysics.isLocalPlayerMoving() ? 45.0F : 90.0F;
               }
            }

            Setting<ElytraFlyModule.StopMotionChoice> stopMotionSetting = this.module.stopMotionSetting;
            if (stopMotionSetting.getValue() == ElytraFlyModule.StopMotionChoice.MOTION && this.isIdleAirborne()) {
               if (this.pitchJitterPhase) {
                  float jitteredYaw = yaw + 180.0F;
                  yaw = Mth.wrapDegrees(jitteredYaw);
               }

               this.pitchJitterPhase = !this.pitchJitterPhase;
            }

            this.serverYaw = yaw;
            this.serverPitch = pitch;
            RotationManager rotationManagerForRequest = ClientContext.rotationManager;
            float[] rotation = new float[]{yaw, pitch};
            rotationManagerForRequest.requestRotation(rotation, 999999, true);
            ClientContext.rotationManager.forceRotationOverride();
         }
      }
   }

   private boolean isIdleAirborne() {
      Input movementInput = ClientAccess.argusclientMinecraft.player.input.keyPresses;
      boolean inputRequested = MovementPhysics.isLocalPlayerMoving() || movementInput.jump() || movementInput.shift() || this.isMovementKeyDown();
      return !EntityUtils.hasCollisionBelow(ClientAccess.argusclientMinecraft.player) && !inputRequested;
   }

   public boolean shouldFallWithChestplate() {
      if (this.module.isToggled() && this.movementStarted) {
         if (!ClientAccess.argusclientMinecraft.player.input.keyPresses.shift()) {
            return false;
         }

         Boolean fallChestplateEnabled = this.module.fallChestplateSetting.getValue();
         if (!fallChestplateEnabled) {
            return false;
         }

         float nearestPlayerDistance = 999.0F;
         ClientLevel level = ClientAccess.argusclientMinecraft.level;

         for (AbstractClientPlayer otherPlayer : level.players()) {
            if (otherPlayer != ClientAccess.argusclientMinecraft.player) {
               SocialRegistry socialRegistry = ClientContext.socialRegistry;
               if (!socialRegistry.isFriendPlayer(otherPlayer)) {
                  double otherPlayerY = otherPlayer.getY();
                  LocalPlayer playerForHeightCheck = ClientAccess.argusclientMinecraft.player;
                  if (!(otherPlayerY > playerForHeightCheck.getY())) {
                     otherPlayerY = otherPlayer.getY();
                     LocalPlayer playerForHeightDifference = ClientAccess.argusclientMinecraft.player;
                     double heightDifference = otherPlayerY - playerForHeightDifference.getY();
                     double verticalDistance = Math.abs(heightDifference);
                     if (!(verticalDistance > 15.0)) {
                        Vec3 otherPosition = otherPlayer.position();
                        Vec3 localPosition = ClientAccess.argusclientMinecraft.player.position();
                        float horizontalDistance = Vec3Utils.horizontalDistance(otherPosition, localPosition);
                        nearestPlayerDistance = Math.min(nearestPlayerDistance, horizontalDistance);
                     }
                  }
               }
            }
         }

         return nearestPlayerDistance < 5.0F;
      } else {
         return false;
      }
   }

   private boolean hasDurabilityFlightCondition() {
      ElytraFlyModule.InfDurabilityChoice durabilityChoice = this.module.infDurabilitySetting.getValue();
      return durabilityChoice.isDisabled() ? PlayerStateUtils.isFallFlying() : !EntityUtils.hasCollisionBelow(ClientAccess.argusclientMinecraft.player);
   }

   private boolean isMovementKeyDown() {
      KeyMapping forwardKey = ClientAccess.argusclientMinecraft.options.keyUp;
      KeyMapping backwardKey = ClientAccess.argusclientMinecraft.options.keyDown;
      KeyMapping rightKey = ClientAccess.argusclientMinecraft.options.keyRight;
      KeyMapping leftKey = ClientAccess.argusclientMinecraft.options.keyLeft;
      KeyMapping sneakKey = ClientAccess.argusclientMinecraft.options.keyShift;
      KeyMapping jumpKey = ClientAccess.argusclientMinecraft.options.keyJump;
      return forwardKey.isDown() || backwardKey.isDown() || rightKey.isDown() || leftKey.isDown() || sneakKey.isDown() || jumpKey.isDown();
   }

   public static void bindModuleReferences(ModuleRegistry registry) {
      autoMaceModule = (AutoMaceModule)registry.getModule(AutoMaceModule.class);
   }
}
