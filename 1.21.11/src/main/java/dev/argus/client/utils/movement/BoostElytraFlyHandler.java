package dev.argus.client.utils.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.MinecraftRunTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.TickRateController;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.modules.movement.ElytraFlyModule;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.network.PacketSender;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

public class BoostElytraFlyHandler extends ElytraFlyModeHandler {
   private static AntiCheatModule antiCheatModule;
   private final ElapsedTimer takeoffGraceTimer = new ElapsedTimer();
   private boolean boostPitchInitialized;
   private float targetPitch;
   private float movementYaw;

   public BoostElytraFlyHandler(ElytraFlyModule module) {
      super(module);
   }

   private float computeMovementYaw() {
      if (MovementPhysics.isLocalPlayerMoving() && this.module.responsiveSetting.getValue()) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         float yaw = player.getYRot();
         return MovementPhysics.getMovementYaw(yaw);
      } else {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         return player.getYRot();
      }
   }

   @Override
   public void handleMovement(LocalPlayerMoveEvent event) {
      float limit = this.module.limitSetting.getValue();
      if (limit > 200.0F && this.isEarlyNcpBoost()) {
         limit = 200.0F;
      }

      if (!this.module.limitSetting.isAtMaximum() || this.isEarlyNcpBoost()) {
         Vec3 velocity = event.getVelocity();
         double velocityX = velocity.x();
         double velocityZ = velocity.z();
         double horizontalSpeed = Math.hypot(velocityX, velocityZ);
         double blocksPerSecond = horizontalSpeed * 20.0;
         if (!(blocksPerSecond * 3.6 <= limit)) {
            Vec3 limitedVelocity = event.getVelocity();
            Vec3 direction = limitedVelocity.normalize();
            double limitAsDouble = limit;
            double metersPerSecond = limitAsDouble / 3.6;
            double blocksPerTick = metersPerSecond / 20.0;
            direction = direction.scale(blocksPerTick);
            double limitedX = direction.x();
            double limitedZ = direction.z();
            event.setHorizontalVelocity(limitedX, limitedZ);
         }
      }
   }

   @Override
   public void handlePrePlayerTick(LocalPlayerPreTickEvent event) {
      if (this.module.autoJumpSetting.getValue() && ClientAccess.argusclientMinecraft.player.isFallFlying()) {
         ClientContext.inputOverrides.requestJump(true);
      }

      this.movementYaw = this.computeMovementYaw();
      this.boostPitchInitialized = false;
      LocalPlayer motionPlayer = ClientAccess.argusclientMinecraft.player;
      if (!PlayerStateUtils.isInWater(motionPlayer)) {
         if (antiCheatModule.is2b2tEnabled() && ClientAccess.argusclientMinecraft.player.isUsingItem()) {
            return;
         }

         Input input = ClientAccess.argusclientMinecraft.player.input.keyPresses;
         boolean takeoffRequested = input.jump() && this.module.takeoffSetting.getValue() != ElytraFlyModule.TakeoffChoice.NONE;
         if (this.module.takeoffSetting.getValue() == ElytraFlyModule.TakeoffChoice.STRICT) {
            boolean strictTakeoffRequested;
            if (!ClientAccess.argusclientMinecraft.player.onGround()) {
               Vec3 velocity = ClientAccess.argusclientMinecraft.player.getDeltaMovement();
               double verticalSpeed = velocity.y();
               strictTakeoffRequested = verticalSpeed < 0.1;
            } else {
               strictTakeoffRequested = false;
            }

            takeoffRequested = strictTakeoffRequested;
         } else {
            ElytraFlyModule moduleForTickRate = this.module;
            TickRateController tickRateController = ClientContext.tickRateController;
            tickRateController.releaseMultiplier(moduleForTickRate);
         }

         if (ClientAccess.argusclientMinecraft.player.isFallFlying()) {
            this.takeoffGraceTimer.reset();
         }

         if ((takeoffRequested || !this.takeoffGraceTimer.hasElapsed(1500L))
            && !ClientAccess.argusclientMinecraft.player.isFallFlying()
            && !EntityUtils.hasCollisionBelow(ClientAccess.argusclientMinecraft.player)
            && this.module.isElytraEquipped()) {
            PacketSender.sendStartFallFlying();
            ClientAccess.argusclientMinecraft.player.startFallFlying();
         }

         if (this.module.isFallFlyingWithoutOverride()) {
            float movementYawRadians = (float)Math.toRadians(this.movementYaw);
            float minimumBoost = this.module.minBoostSetting.getValue();
            float boostMagnitude = minimumBoost * 0.1F;
            if (this.module.minBoostSetting.isAtMaximum() && antiCheatModule.is2b2tEnabled()) {
               boostMagnitude = 0.0205F;
               if (this.targetPitch <= 0.0F) {
                  ElapsedTimer setbackTimer = ClientContext.serverStateManager.getSetbackTimer();
                  double setbackWindowSeconds = 10.0;
                  TimeUnit seconds = TimeUnit.SECONDS;
                  if (!setbackTimer.hasElapsed(setbackWindowSeconds, seconds)) {
                     boostMagnitude = 0.008F;
                  }
               }
            }

            if (this.isEarlyNcpBoost()) {
               boostMagnitude = 3.0F;
            }

            if (this.module.nCPBoostSetting.getValue()) {
               this.updateBoostPitch();
            }

            if (!this.boostPitchInitialized) {
               if (this.module.inLavaSetting.getValue() && ClientAccess.argusclientMinecraft.player.isInLava()) {
                  float lavaBoostScale = 0.2F;
                  Float configuredSpeed = this.module.speedSetting.getValue();
                  boostMagnitude = lavaBoostScale * configuredSpeed;
               }

               if (this.module.factorizeSetting.getValue()
                  ? !(this.targetPitch > 0.0F)
                  : !PlayerMovementKeys.isDown(ClientAccess.argusclientMinecraft.options.keyUp) && !this.module.alwaysSetting.getValue()) {
                  if (PlayerMovementKeys.isDown(ClientAccess.argusclientMinecraft.options.keyDown)) {
                     motionPlayer = ClientAccess.argusclientMinecraft.player;
                     double backwardYawForSine = movementYawRadians;
                     double backwardX = Mth.sin(backwardYawForSine) * boostMagnitude;
                     double backwardYawForCosine = movementYawRadians;
                     double backwardZ = Mth.cos(backwardYawForCosine) * -boostMagnitude;
                     double backwardY = 0.0;
                     motionPlayer.push(backwardX, backwardY, backwardZ);
                  }

                  return;
               }

               motionPlayer = ClientAccess.argusclientMinecraft.player;
               double forwardYawForSine = movementYawRadians;
               double forwardX = Mth.sin(forwardYawForSine) * -boostMagnitude;
               double forwardYawForCosine = movementYawRadians;
               double forwardZ = Mth.cos(forwardYawForCosine) * boostMagnitude;
               double forwardY = 0.0;
               motionPlayer.push(forwardX, forwardY, forwardZ);
            }
         }
      }
   }

   @Override
   public void handleClientTick(MinecraftRunTickEvent event) {
      if (!this.module.nCPBoostSetting.getValue()) {
         if (!this.isWorldUnavailable() && this.module.isFallFlyingWithoutOverride() && event.hasPendingTicks()) {
            this.updateBoostPitch();
         }
      }
   }

   private boolean isEarlyNcpBoost() {
      if (this.module.nCPBoostSetting.getValue()) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         int fallFlyingTicks = player.getFallFlyingTicks();
         if (fallFlyingTicks <= 5) {
            return true;
         }
      }

      return false;
   }

   private void updateBoostPitch() {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      int randomSample = random.nextInt(6);
      float pitchJitter;
      if (RotationManager.isMovementSyncEnabled()) {
         float jitterStep = AntiCheatModule.ROTATION_JITTER_STEP;
         pitchJitter = jitterStep * (-3 + randomSample);
      } else {
         pitchJitter = 0.0F;
      }

      if (MovementPhysics.isLocalPlayerMoving()) {
         RotationManager rotationForMovement = ClientContext.rotationManager;
         float[] movementRotation = new float[]{this.movementYaw, 0.0F};
         LocalPlayer playerForMovementPitch = ClientAccess.argusclientMinecraft.player;
         movementRotation[1] = playerForMovementPitch.getXRot();
         rotationForMovement.requestRotation(movementRotation, 10000, true);
         ClientContext.rotationManager.forceRotationOverride();
      }

      if (!RotationManager.isMovementSyncEnabled()) {
         this.targetPitch = ClientAccess.argusclientMinecraft.player.getXRot();
      } else {
         float previousTargetPitch = this.targetPitch;
         LocalPlayer playerForPitchDifference = ClientAccess.argusclientMinecraft.player;
         float pitchDifference = previousTargetPitch - playerForPitchDifference.getXRot();
         float absolutePitchDifference = Math.abs(pitchDifference);
         if (absolutePitchDifference < 15.0F) {
            this.targetPitch = ClientAccess.argusclientMinecraft.player.getXRot();
         } else {
            float targetPitchForDirection = this.targetPitch;
            LocalPlayer playerForPitchDirection = ClientAccess.argusclientMinecraft.player;
            if (targetPitchForDirection > playerForPitchDirection.getXRot()) {
               float targetBeforeDecrease = this.targetPitch;
               this.targetPitch = targetBeforeDecrease - 10.0F;
            } else {
               float targetBeforeIncrease = this.targetPitch;
               this.targetPitch = targetBeforeIncrease + 10.0F;
            }
         }
      }

      if (this.module.responsiveSetting.getValue()) {
         float desiredPitch = this.targetPitch + pitchJitter;
         if (ClientAccess.argusclientMinecraft.player.input.keyPresses.jump()) {
            float jumpPitch;
            if (RotationManager.isMovementSyncEnabled()) {
               jumpPitch = -45.0F;
            } else {
               jumpPitch = -60.0F;
            }

            desiredPitch = jumpPitch + pitchJitter;
         } else if (ClientAccess.argusclientMinecraft.player.input.keyPresses.shift()) {
            desiredPitch = 60.0F + pitchJitter;
         }

         desiredPitch = Mth.clamp(desiredPitch, -90.0F, 90.0F);
         if (ClientAccess.argusclientMinecraft.player.getXRot() != desiredPitch) {
            RotationManager rotationForPitch = ClientContext.rotationManager;
            float[] pitchRotation = new float[]{this.movementYaw, desiredPitch};
            rotationForPitch.requestRotation(pitchRotation, 1000001, true);
            ClientContext.rotationManager.forceRotationOverride();
            this.targetPitch = desiredPitch;
         }
      }

      if (this.targetPitch < 0.0F && this.module.nCPBoostSetting.getValue()) {
         int flightTicks = ClientAccess.argusclientMinecraft.player.getFallFlyingTicks();
         if (this.module.isDurabilityBypassActive()) {
            flightTicks = ClientAccess.argusclientMinecraft.player.tickCount;
         }

         int boostPhase = flightTicks % 6;
         if (boostPhase > 2 || flightTicks >= 1 && flightTicks < 20) {
            this.boostPitchInitialized = true;
            return;
         }

         RotationManager rotationForBoost = ClientContext.rotationManager;
         float[] boostRotation = new float[]{this.movementYaw, 0.0F};
         rotationForBoost.requestRotation(boostRotation, 100002, true);
         ClientContext.rotationManager.forceRotationOverride();
      }
   }

   @Override
   public void handleIncomingPacket(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundSetHealthPacket healthPacket) {
         float healthAndSaturation = healthPacket.getHealth();
         healthAndSaturation += healthPacket.getSaturation();
         if (healthAndSaturation < EntityUtils.getPlayerHealthWithAbsorption()) {
            this.onDisable();
            this.onEnable();
         }
      }
   }

   public static void bindModuleReferences(ModuleRegistry registry) {
      antiCheatModule = (AntiCheatModule)registry.getModule(AntiCheatModule.class);
   }
}
