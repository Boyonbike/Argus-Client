package dev.argus.client.utils.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LivingEntityRotationEvent;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.combat.AutoMaceModule;
import dev.argus.client.modules.movement.ElytraFlyModule;
import dev.argus.client.modules.movement.FireworksModule;
import dev.argus.client.modules.movement.ObstaclePasserModule;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.InputUtils;
import dev.argus.client.utils.math.MathUtils;
import dev.argus.mixin.ServerboundMovePlayerPacketAccessor;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

public class ControlElytraFlyHandler extends ElytraFlyModeHandler {
   private static ObstaclePasserModule obstaclePasserModule;
   private static FireworksModule fireworksModule;
   private static AutoMaceModule autoMaceModule;
   private Vec3 savedVelocity = Vec3.ZERO;
   private int verticalInputTicks;
   private double flightSpeed;
   private long accelerationStartTimeMillis;
   private long verticalAccelerationStartTimeMillis;
   private boolean verticalVelocityApplied;
   private boolean verticalInputOverride;
   private int verticalInputDirection;

   public ControlElytraFlyHandler(ElytraFlyModule module) {
      super(module);
   }

   @Override
   public void onEnable() {
      this.flightSpeed = 0.0;
      this.verticalAccelerationStartTimeMillis = this.accelerationStartTimeMillis = System.currentTimeMillis();
   }

   public void setVerticalInputOverride(int direction) {
      this.verticalInputOverride = true;
      this.verticalInputDirection = direction;
   }

   private float computeFlightPitch() {
      float pitch = ClientAccess.argusclientMinecraft.player.getXRot();
      if (this.isVerticalInputRequested()) {
         pitch = 90.0F;
         if (MovementPhysics.isLocalPlayerMoving()) {
            pitch = 45.0F;
         }

         if (ClientAccess.argusclientMinecraft.player.input.keyPresses.jump()) {
            pitch = -pitch;
         }
      }

      return pitch;
   }

   @Override
   public void handleMovement(LocalPlayerMoveEvent event) {
      if (!this.module.isFallFlyingWithoutOverride()) {
         this.accelerationStartTimeMillis = System.currentTimeMillis();
      } else if (this.module.isManualVerticalControlActive()) {
         this.accelerationStartTimeMillis = System.currentTimeMillis();
      } else if (this.isFireworkMovementAllowed()) {
         if (!this.isObstaclePasserPathActive()) {
            if (!MovementPhysics.isLocalPlayerMoving()) {
               this.accelerationStartTimeMillis = System.currentTimeMillis();
            }

            float verticalSpeed = this.module.vSpeedSetting.getValue();
            if (this.module.accelerateSetting.getValue()) {
               double targetHorizontalSpeed = this.module.flightSpeedSetting.getValue().floatValue();
               double minimumHorizontalSpeed = this.module.accelMinSetting.getValue().floatValue();
               double horizontalAccelerationDuration = this.module.accelTimeSetting.getValue().floatValue();
               long horizontalAccelerationStart = this.accelerationStartTimeMillis;
               this.flightSpeed = (float)MovementPhysics.accelerateTo(
                  targetHorizontalSpeed, minimumHorizontalSpeed, horizontalAccelerationDuration, horizontalAccelerationStart
               );
               double targetVerticalSpeed = verticalSpeed;
               double minimumVerticalSpeed = this.module.verAccelMinSetting.getValue().floatValue();
               double verticalAccelerationDuration = this.module.accelTimeSetting.getValue().floatValue();
               long verticalAccelerationStart = this.verticalAccelerationStartTimeMillis;
               verticalSpeed = (float)MovementPhysics.accelerateTo(
                  targetVerticalSpeed, minimumVerticalSpeed, verticalAccelerationDuration, verticalAccelerationStart
               );
            } else {
               this.flightSpeed = this.module.flightSpeedSetting.getValue().floatValue();
            }

            this.flightSpeed = this.flightSpeed + fireworksModule.getAdditionalBoost(false);
            if (this.verticalInputTicks == 0) {
               if (ClientAccess.argusclientMinecraft.player.input.keyPresses.jump()
                  && this.module.verticalSetting.getValue() == ElytraFlyModule.VerticalChoice.STRICT) {
                  double speedBeforeLimit = this.flightSpeed;
                  if (speedBeforeLimit >= 2.0) {
                     this.flightSpeed = 2.0;
                  }
               }

               double horizontalSpeed = this.flightSpeed;
               double[] horizontalMotion = MovementPhysics.applyHorizontalMotion(event, horizontalSpeed);
               if (autoMaceModule.isToggled() && autoMaceModule.isFlightControlActive()) {
                  float[] maceRotation = autoMaceModule.getFlightRotation();
                  float maceSpeedMultiplier;
                  if (maceRotation[1] >= 0.0F) {
                     maceSpeedMultiplier = 1.0F;
                  } else {
                     maceSpeedMultiplier = 1.0F - -maceRotation[1] / 90.0F;
                  }

                  float maceYaw = maceRotation[0];
                  ClientInput maceMovementInput = InputUtils.createInput();
                  double maceRequestedSpeed = this.flightSpeed * maceSpeedMultiplier;
                  double maceSpeedCap = 1.0;
                  double maceLimitedSpeed = Math.min(maceRequestedSpeed, maceSpeedCap);
                  horizontalMotion = MovementPhysics.getHorizontalMotion(maceYaw, maceMovementInput, maceLimitedSpeed);
                  double maceMotionX = horizontalMotion[0];
                  double maceMotionZ = horizontalMotion[1];
                  event.setHorizontalVelocity(maceMotionX, maceMotionZ);
               }

               ClientAccess.argusclientMinecraft
                  .player
                  .setDeltaMovement(horizontalMotion[0], ClientAccess.argusclientMinecraft.player.getDeltaMovement().y, horizontalMotion[1]);
               this.savedVelocity = ClientAccess.argusclientMinecraft.player.getDeltaMovement();
               int playerTickCount = ClientAccess.argusclientMinecraft.player.tickCount;
               if (playerTickCount % 4 == 0) {
                  double motionBeforeDescent = event.getY();
                  double descendingMotionY = motionBeforeDescent - 1.0E-4;
                  event.setY(descendingMotionY);
                  ClientAccess.argusclientMinecraft.player.setDeltaMovement(ClientAccess.argusclientMinecraft.player.getDeltaMovement().add(0.0, -1.0E-4, 0.0));
               }
            }

            boolean jumping = ClientAccess.argusclientMinecraft.player.input.keyPresses.jump();
            boolean sneaking = ClientAccess.argusclientMinecraft.player.input.keyPresses.shift();
            if (this.verticalInputOverride) {
               jumping = this.verticalInputDirection > 0;
               sneaking = this.verticalInputDirection < 0;
               this.verticalInputOverride = false;
            }

            if (jumping && this.module.isFallFlyingWithoutOverride()) {
               if (this.module.verticalSetting.getValue() == ElytraFlyModule.VerticalChoice.STRICT) {
                  this.verticalInputTicks++;
               }

               if (this.module.verticalSetting.getValue() == ElytraFlyModule.VerticalChoice.PLAIN) {
                  double ascendingMotionY = verticalSpeed;
                  event.setY(ascendingMotionY);
                  ClientAccess.argusclientMinecraft
                     .player
                     .setDeltaMovement(ClientAccess.argusclientMinecraft.player.getDeltaMovement().with(Axis.Y, verticalSpeed));
               } else if (this.module.verticalSetting.getValue() == ElytraFlyModule.VerticalChoice.STRICT) {
                  if (MovementPhysics.isLocalPlayerMoving()) {
                     this.applyVerticalPitchVelocity();
                  }

                  return;
               }
            } else {
               if (sneaking) {
                  this.verticalInputTicks = 0;
                  double descendingMotionY = -verticalSpeed;
                  event.setY(descendingMotionY);
                  ClientAccess.argusclientMinecraft
                     .player
                     .setDeltaMovement(ClientAccess.argusclientMinecraft.player.getDeltaMovement().with(Axis.Y, -verticalSpeed));
               } else {
                  this.verticalInputTicks = 0;
                  double zeroMotionY = 0.0;
                  event.setY(zeroMotionY);
                  ClientAccess.argusclientMinecraft.player.setDeltaMovement(ClientAccess.argusclientMinecraft.player.getDeltaMovement().with(Axis.Y, 0.0));
                  if (!ClientAccess.argusclientMinecraft.player.verticalCollision) {
                     Float configuredGlide = this.module.glideSetting.getValue();
                     if (configuredGlide != 0.0F) {
                        ClientAccess.argusclientMinecraft
                           .player
                           .setDeltaMovement(ClientAccess.argusclientMinecraft.player.getDeltaMovement().add(0.0, -this.module.glideSetting.getValue(), 0.0));
                        double glidingMotionY = ClientAccess.argusclientMinecraft.player.getDeltaMovement().y;
                        event.setY(glidingMotionY);
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public void handleOutgoingPacket(PacketEvent.Send event) {
      if (!this.isObstaclePasserPathActive()
         && event.getPacket() instanceof ServerboundMovePlayerPacket movementPacket
         && this.module.isFallFlyingWithoutOverride()) {
         if (this.module.isManualVerticalControlActive()) {
            return;
         }

         if (!this.isFireworkMovementAllowed()) {
            return;
         }

         if (this.isVerticalInputRequested()) {
            ServerboundMovePlayerPacketAccessor verticalPacket = (ServerboundMovePlayerPacketAccessor)movementPacket;
            verticalPacket.setPitch(this.computeFlightPitch());
            return;
         }

         if (!MovementPhysics.isLocalPlayerMoving()) {
            return;
         }

         if (this.module.spoofPitchSetting.getValue()) {
            ServerboundMovePlayerPacketAccessor spoofedPacket = (ServerboundMovePlayerPacketAccessor)movementPacket;
            Float spoofedPitch = this.module.spoofedPitchSetting.getValue();
            spoofedPacket.setPitch(spoofedPitch);
         }

         if (this.verticalInputTicks > 0) {
            ServerboundMovePlayerPacketAccessor ascendingPacket = (ServerboundMovePlayerPacketAccessor)movementPacket;
            Float ascendingPitch = this.module.vPitchSetting.getValue();
            ascendingPacket.setPitch(-ascendingPitch);
         }
      }
   }

   @Override
   public void handlePrePlayerTick(LocalPlayerPreTickEvent event) {
      if (!this.isObstaclePasserPathActive() && this.module.isManualVerticalControlActive()) {
         ClientContext.inputOverrides.requestJump(true);
         ClientContext.inputOverrides.requestForward(true);
         ClientContext.inputOverrides.requestForwardMovement(1.0F);
      }
   }

   @Override
   public void handlePlayerTick(LocalPlayerTickEvent event) {
      if (!this.module.isFallFlyingWithoutOverride()) {
         this.verticalAccelerationStartTimeMillis = System.currentTimeMillis();
      }

      if (!ClientAccess.argusclientMinecraft.player.input.keyPresses.jump() && !ClientAccess.argusclientMinecraft.player.input.keyPresses.shift()) {
         this.verticalAccelerationStartTimeMillis = System.currentTimeMillis();
      }

      if (ClientAccess.argusclientMinecraft.player.input.keyPresses.jump() && this.module.isFallFlyingWithoutOverride()) {
         if (!this.verticalVelocityApplied && this.module.verticalSetting.getValue() == ElytraFlyModule.VerticalChoice.MANUAL) {
            float yaw = ClientAccess.argusclientMinecraft.player.getYRot();
            ClientInput forwardInput = InputUtils.createInput();
            double horizontalSpeed = this.flightSpeed;
            double[] motion = MovementPhysics.getHorizontalMotion(yaw, forwardInput, horizontalSpeed);
            ClientAccess.argusclientMinecraft.player.push(motion[0], 0.0, motion[1]);
         }

         this.verticalVelocityApplied = true;
      } else {
         this.verticalVelocityApplied = false;
      }
   }

   @Override
   public void handleTravelRotation(LivingEntityRotationEvent event) {
      if (this.module.isFallFlyingWithoutOverride() && !this.module.isManualVerticalControlActive() && this.isFireworkMovementAllowed()) {
         if (!this.isObstaclePasserPathActive()) {
            if (this.isVerticalInputRequested()) {
               float pitch = this.computeFlightPitch();
               event.setPitch(pitch);
            }

            if (MovementPhysics.isLocalPlayerMoving()) {
               float yaw = MovementPhysics.getMovementYaw(ClientAccess.argusclientMinecraft.player.getYRot());
               event.setYaw(yaw);
            }
         }
      }
   }

   @Override
   public void handleSendPosition(LocalPlayerSendPositionEvent event) {
      if (this.module.isFallFlyingWithoutOverride()
         && !this.module.isManualVerticalControlActive()
         && this.isFireworkMovementAllowed()
         && event.getStage() == EventStage.Pre) {
         if (!this.isObstaclePasserPathActive()) {
            if (this.isVerticalInputRequested()) {
               float pitch = this.computeFlightPitch();
               event.setPitch(pitch);
            } else if (MovementPhysics.isLocalPlayerMoving()) {
               float yaw = MovementPhysics.getMovementYaw(ClientAccess.argusclientMinecraft.player.getYRot());
               event.setYaw(yaw);
               if (this.module.spoofPitchSetting.getValue()) {
                  float spoofedPitch = this.module.spoofedPitchSetting.getValue();
                  event.setPitch(spoofedPitch);
               }

               if (this.verticalInputTicks > 0) {
                  Float configuredVerticalPitch = this.module.vPitchSetting.getValue();
                  float verticalPitch = -configuredVerticalPitch;
                  event.setPitch(verticalPitch);
               }
            }
         }
      }
   }

   @Override
   public void handleIncomingPacket(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         this.accelerationStartTimeMillis = System.currentTimeMillis();
      }
   }

   private boolean isFireworkMovementAllowed() {
      if (this.module.isFallFlyingWithoutOverride() && fireworksModule.autoLaunchSetting.getValue() && fireworksModule.isToggled()) {
         ElapsedTimer rocketUseTimer = fireworksModule.rocketUseTimer;
         return !rocketUseTimer.hasElapsed(500L) ? true : fireworksModule.isRocketBoostActive();
      } else {
         return true;
      }
   }

   private boolean isObstaclePasserPathActive() {
      return obstaclePasserModule == null ? false : obstaclePasserModule.isPathActive();
   }

   private boolean isVerticalInputRequested() {
      return ClientAccess.argusclientMinecraft.player.input.keyPresses.jump()
         ? true
         : ClientAccess.argusclientMinecraft.player.input.keyPresses.shift()
            && this.module.verticalSetting.getValue() == ElytraFlyModule.VerticalChoice.PLAIN
            && RotationManager.isMovementSyncEnabled();
   }

   private void applyVerticalPitchVelocity() {
      Float anglePitch = this.module.vPitchSetting.getValue();
      float negativeAnglePitch = -anglePitch;
      double pitchRadians = MathUtils.toRadians(negativeAnglePitch);
      Vec3 velocity = this.savedVelocity;
      Float directionPitch = this.module.vPitchSetting.getValue();
      float negativeDirectionPitch = -directionPitch;
      float yaw = ClientAccess.argusclientMinecraft.player.getYRot();
      Vec3 lookDirection = MathUtils.directionFromPitchYaw(negativeDirectionPitch, yaw);
      double horizontalSpeed = velocity.horizontalDistance();
      double horizontalLookLength = lookDirection.horizontalDistance();
      double lookLength = lookDirection.length();
      double pitchCosine = Math.cos(pitchRadians);
      double squaredPitchCosine = pitchCosine * pitchCosine;
      double lookLengthScale = lookLength / 0.4;
      double lift = squaredPitchCosine * Math.min(1.0, lookLengthScale);
      double gravity = 0.08 * (-1.0 + lift * 0.75);
      velocity = velocity.add(0.0, gravity, 0.0);
      if (pitchRadians < 0.0 && horizontalLookLength > 0.0) {
         double upwardProjection = horizontalSpeed * -Math.sin(pitchRadians);
         double climbAcceleration = upwardProjection * 0.04;
         double accelerationX = -lookDirection.x * climbAcceleration / horizontalLookLength;
         double accelerationY = climbAcceleration * 3.2;
         double accelerationZ = -lookDirection.z * climbAcceleration / horizontalLookLength;
         velocity = velocity.add(accelerationX, accelerationY, accelerationZ);
      }

      LocalPlayer playerForVelocity = ClientAccess.argusclientMinecraft.player;
      double horizontalDrag = 0.99;
      double verticalDrag = 0.98;
      double depthDrag = 0.99;
      Vec3 slowedVelocity = velocity.multiply(horizontalDrag, verticalDrag, depthDrag);
      playerForVelocity.setDeltaMovement(slowedVelocity);
      this.savedVelocity = ClientAccess.argusclientMinecraft.player.getDeltaMovement();
   }

   public static void bindModuleReferences(ModuleRegistry registry) {
      obstaclePasserModule = (ObstaclePasserModule)registry.getModule(ObstaclePasserModule.class);
      fireworksModule = (FireworksModule)registry.getModule(FireworksModule.class);
      autoMaceModule = (AutoMaceModule)registry.getModule(AutoMaceModule.class);
   }
}
