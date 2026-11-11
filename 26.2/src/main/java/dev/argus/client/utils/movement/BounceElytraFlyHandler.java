package dev.argus.client.utils.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LivingEntityRotationEvent;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftRunTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.movement.ElytraFlyModule;
import dev.argus.client.modules.movement.ObstaclePasserModule;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.math.Vec3Utils;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.mixin.ServerboundMovePlayerPacketAccessor;
import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class BounceElytraFlyHandler extends ElytraFlyModeHandler {
   private static ObstaclePasserModule obstaclePasserModule;
   private final ElapsedTimer normalFlightRetryTimer = new ElapsedTimer();
   private final Queue<Packet<?>> queuedPackets = new ArrayDeque<>();
   private int bounceCooldownTicks;
   private int bounceInputTicks;
   private boolean durabilityBypassAtTickStart;
   private boolean fallFlyingAtTickStart;
   private boolean diagonalPacketPending;
   private float automaticPitch = 90.0F;

   private void flushQueuedPackets() {
      if (this.isWorldUnavailable()) {
         this.queuedPackets.clear();
      } else {
         while (!this.queuedPackets.isEmpty()) {
            Packet<?> packet = this.queuedPackets.poll();
            if (packet == null) {
               break;
            }

            PacketSender.sendDirect(packet);
         }
      }
   }

   public BounceElytraFlyHandler(ElytraFlyModule module) {
      super(module);
   }

   @Override
   public void onEnable() {
      this.bounceInputTicks = 0;
      this.diagonalPacketPending = this.module.diagonalSetting.getValue() && this.module.packetSetting.getValue();
   }

   @Override
   public void onDisable() {
      ClientAccess.argusclientMinecraft.options.keyJump.setDown(false);
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      player.setSprinting(false);
      this.flushQueuedPackets();
   }

   private boolean canApplyBounce() {
      return this.durabilityBypassAtTickStart || this.module.isElytraEquipped();
   }

   @Override
   public void handlePrePlayerTick(LocalPlayerPreTickEvent event) {
      if (!this.diagonalPacketPending) {
         this.fallFlyingAtTickStart = PlayerStateUtils.isFallFlying();
         if (!this.isObstaclePasserActive() && this.canApplyBounce() && !this.module.isItemUseSlowingFlight()) {
            ClientContext.inputOverrides.requestJump(this.bounceInputTicks != 2);
            ClientContext.inputOverrides.requestForward(true);
            ClientContext.inputOverrides.requestSprint(true);
            ClientContext.inputOverrides.requestForwardMovement(1.0F);
            ClientAccess.argusclientMinecraft.player.setSprinting(true);
            this.bounceInputTicks++;
         } else {
            this.bounceInputTicks = 0;
         }

         this.durabilityBypassAtTickStart = this.module.isDurabilityBypassActive();
         if (ClientAccess.argusclientMinecraft.player.isFallFlying()) {
            int groundedTicks = ClientContext.playerStateTracker.getGroundedTicks();
            if (groundedTicks > 5) {
               ClientAccess.argusclientMinecraft.player.stopFallFlying();
               return;
            }
         }

         boolean bounceAllowed = this.canApplyBounce();
         if (!PlayerStateUtils.isInLiquid(ClientAccess.argusclientMinecraft.player)) {
            if (!PlayerStateUtils.isInCobweb(ClientAccess.argusclientMinecraft.player) && !this.module.isItemUseSlowingFlight()) {
               if (!this.isObstaclePasserActive() && bounceAllowed) {
                  if (!PlayerStateUtils.isFallFlying()) {
                     int takeoffGroundedTicks = ClientContext.playerStateTracker.getGroundedTicks();
                     if (takeoffGroundedTicks < 3 && !this.durabilityBypassAtTickStart) {
                        ClientAccess.argusclientMinecraft.player.stopFallFlying();
                        PacketSender.sendStartFallFlying();
                     }
                  }

                  if (!ClientAccess.argusclientMinecraft.player.horizontalCollision && MovementPhysics.isLocalPlayerMoving()) {
                     ClientAccess.argusclientMinecraft.player.setSprinting(true);
                     if (ClientAccess.argusclientMinecraft.player.isFallFlying()) {
                        ClientAccess.argusclientMinecraft.player.setShiftKeyDown(false);
                     }
                  }

                  Boolean pitchLocked = this.module.pitchLockSetting.getValue();
                  if (pitchLocked) {
                     Boolean silent = this.module.silentSetting.getValue();
                     if (!silent) {
                        LocalPlayer pitchPlayer = ClientAccess.argusclientMinecraft.player;
                        Float pitchValue = this.getPitch();
                        float configuredPitch = pitchValue;
                        pitchPlayer.setXRot(configuredPitch);
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public void handleOutgoingPacket(PacketEvent.Send event) {
      if (!this.isObstaclePasserActive() && !this.diagonalPacketPending && this.fallFlyingAtTickStart && this.canApplyBounce()) {
         if (event.getPacket() instanceof ServerboundMovePlayerPacketAccessor packetAccess) {
            Boolean silent = this.module.silentSetting.getValue();
            if (silent) {
               Boolean pitchLocked = this.module.pitchLockSetting.getValue();
               if (pitchLocked && !this.module.isItemUseSlowingFlight()) {
                  Float configuredPitch = this.getPitch();
                  packetAccess.setPitch(configuredPitch);
               }
            }
         }

         Boolean compensate = this.module.compensateSetting.getValue();
         if (compensate
            && !this.module.isItemUseSlowingFlight()
            && !(event.getPacket() instanceof ServerboundPlayerCommandPacket)
            && !(event.getPacket() instanceof ServerboundPongPacket)) {
            Queue<Packet<?>> packetQueue = this.queuedPackets;
            Packet<?> packetToQueue = event.getPacket();
            packetQueue.add(packetToQueue);
            event.cancel();
         }
      }
   }

   @Override
   public void handleMovement(LocalPlayerMoveEvent event) {
      Boolean packetMode = this.module.packetSetting.getValue();
      if (packetMode) {
         if (this.diagonalPacketPending) {
            Vec3 bounceTarget = this.getBounceTarget();
            Vec3 currentPosition = ClientAccess.argusclientMinecraft.player.position();
            float targetDistance = Vec3Utils.horizontalDistance(bounceTarget, currentPosition);
            float clampedDistance = Mth.clamp(targetDistance, 0.0F, 0.1F);
            double distanceAsDouble = clampedDistance;
            if (distanceAsDouble < 0.1) {
               this.diagonalPacketPending = false;
               event.setHorizontalVelocity(0.0, 0.0);
               LocalPlayer playerToPosition = ClientAccess.argusclientMinecraft.player;
               double targetX = bounceTarget.x;
               double currentHeight = ClientAccess.argusclientMinecraft.player.getY();
               double targetZ = bounceTarget.z;
               playerToPosition.setPos(targetX, currentHeight, targetZ);
            } else {
               Vec3 playerPosition = ClientAccess.argusclientMinecraft.player.position();
               Vec3 targetDelta = bounceTarget.subtract(playerPosition);
               Vec3 targetDirection = targetDelta.normalize();
               Vec3 step = targetDirection.scale(0.11);
               event.setHorizontalVelocity(0.0, 0.0);
               double stepZ = step.z;
               double stepX = step.x;
               event.setHorizontalVelocity(stepX, stepZ);
            }
         } else if (ClientContext.serverStateManager.hasElapsedSinceSetback(200L) && !PlayerStateUtils.isHungerLimited() && PlayerStateUtils.isFallFlying()) {
            double displaySpeed = ClientContext.movementSpeedTracker.getDisplaySpeed();
            if (ClientAccess.argusclientMinecraft.player.onGround()) {
               Integer maximumBoost = this.module.maxBoostSetting.getValue();
               if (displaySpeed < maximumBoost.intValue() && displaySpeed >= 97.0) {
                  event.setY(0.0);
               }
            }
         }
      }
   }

   @Override
   public void handlePlayerTick(LocalPlayerTickEvent event) {
      boolean canProcessPackets = !this.isObstaclePasserActive() || this.queuedPackets.isEmpty();
      if (!canProcessPackets || this.diagonalPacketPending) {
         this.flushQueuedPackets();
      } else if (!this.module.isItemUseSlowingFlight()) {
         Boolean packetMode = this.module.packetSetting.getValue();
         if (packetMode) {
            Boolean diagonal = this.module.diagonalSetting.getValue();
            if (diagonal) {
               float playerYaw = ClientAccess.argusclientMinecraft.player.getYRot();
               float yawSteps = (playerYaw + 1.0F) / 45.0F;
               float diagonalYaw = Math.round(yawSteps) * 45;
               if (diagonalYaw % 90.0F == 0.0F) {
                  diagonalYaw += 45.0F;
               }

               ClientAccess.argusclientMinecraft.player.setYRot(diagonalYaw);
            }
         }

         if (ClientAccess.argusclientMinecraft.player.isFallFlying()) {
            ClientAccess.argusclientMinecraft.player.avatarState().walkDist = 0.0F;
         }

         if (!PlayerStateUtils.isInLiquid(ClientAccess.argusclientMinecraft.player) && !PlayerStateUtils.isInCobweb(ClientAccess.argusclientMinecraft.player)) {
            Boolean compensate = this.module.compensateSetting.getValue();
            if (compensate) {
               if (this.bounceCooldownTicks == 0) {
                  this.flushQueuedPackets();
               }
            } else if (!this.queuedPackets.isEmpty()) {
               this.flushQueuedPackets();
            }

            return;
         }

         this.flushQueuedPackets();
      }
   }

   @Override
   public void handleClientTick(MinecraftRunTickEvent event) {
      if (!this.isWorldUnavailable()) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         if (!player.isFallFlying() && this.normalFlightRetryTimer.hasElapsedAndReset(10L) && this.bounceInputTicks >= 4) {
            this.module.tryStartNormalFlight();
         }
      }
   }

   @Override
   public void handleTravelRotation(LivingEntityRotationEvent event) {
      if (this.canApplyBounce() && !this.isObstaclePasserActive() && !this.diagonalPacketPending) {
         Boolean pitchLocked = this.module.pitchLockSetting.getValue();
         if (pitchLocked && !this.module.isItemUseSlowingFlight()) {
            if (this.module.autoPitchSetting.getValue() && !event.isJump()) {
               this.automaticPitch = ClientAccess.argusclientMinecraft.player.getDeltaMovement().y > -0.2 ? 90.0F : 2.0F;
            }

            Float pitchValue = this.getPitch();
            float configuredPitch = pitchValue;
            event.setPitch(configuredPitch);
            event.cancel();
         }
      }
   }

   @Override
   public void handleSendPosition(LocalPlayerSendPositionEvent event) {
      if (this.diagonalPacketPending) {
         if (event.getStage() != EventStage.Post) {
            Vec3 bounceTarget = this.getBounceTarget();
            float[] targetRotations = RotationManager.getRotationsToPosition(bounceTarget);
            event.setRotation(targetRotations);
         }
      } else {
         if (event.getStage() == EventStage.Pre) {
            double deltaX = ClientAccess.argusclientMinecraft.player.getX() - ClientAccess.argusclientMinecraft.player.xo;
            double deltaY = ClientAccess.argusclientMinecraft.player.getY() - ClientAccess.argusclientMinecraft.player.yo;
            double deltaZ = ClientAccess.argusclientMinecraft.player.getZ() - ClientAccess.argusclientMinecraft.player.zo;
            Vec3 playerPosition = ClientAccess.argusclientMinecraft.player.position();
            Vec3 projectedPosition = playerPosition.add(deltaX, deltaY, deltaZ);
            ClientLevel collisionLevel = ClientAccess.argusclientMinecraft.level;
            LocalPlayer collisionPlayer = ClientAccess.argusclientMinecraft.player;
            AABB collisionBox = AABB.ofSize(projectedPosition, 0.2, 0.2, 0.2);
            boolean collisionFree = collisionLevel.noBlockCollision(collisionPlayer, collisionBox);
            if (!collisionFree || ClientAccess.argusclientMinecraft.player.onGround()) {
               this.bounceCooldownTicks = 2;
            } else if (this.bounceCooldownTicks > 0) {
               this.bounceCooldownTicks--;
            }
         }

         if (!this.module.isItemUseSlowingFlight() && this.canApplyBounce() && !this.isObstaclePasserActive() && event.getStage() != EventStage.Post) {
            Boolean pitchLock = this.module.pitchLockSetting.getValue();
            if (pitchLock) {
               Boolean silent = this.module.silentSetting.getValue();
               if (silent) {
                  Float configuredPitch = this.getPitch();
                  float pitch = configuredPitch;
                  event.setPitch(pitch);
               }
            }

            return;
         }
      }
   }

   @Override
   public void handleIncomingPacket(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerCombatKillPacket || event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         Minecraft minecraft = ClientAccess.argusclientMinecraft;
         Runnable flushTask = this::flushQueuedPackets;
         minecraft.execute(flushTask);
      }
   }

   private boolean isObstaclePasserActive() {
      return obstaclePasserModule != null && obstaclePasserModule.isPassingObstacles();
   }

   private float getPitch() {
      return this.module.autoPitchSetting.getValue() ? this.automaticPitch : this.module.pitchSetting.getValue();
   }

   public Vec3 getBounceTarget() {
      Direction openSide = this.getOpenHorizontalSide();
      BlockPos localPosition = PlayerStateUtils.getLocalRoundedBlockPosition();
      return openSide == null ? localPosition.getBottomCenter() : localPosition.relative(openSide).getBottomCenter();
   }

   public Direction getOpenHorizontalSide() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      BlockPos playerPosition = player.blockPosition();
      int blockedSides = 0;
      Direction lastOpenSide = null;

      for (Direction side : Plane.HORIZONTAL) {
         ClientLevel level = ClientAccess.argusclientMinecraft.level;
         BlockPos adjacentPosition = playerPosition.relative(side);
         BlockState adjacentState = level.getBlockState(adjacentPosition);
         if (!adjacentState.canBeReplaced()) {
            blockedSides++;
         } else {
            lastOpenSide = side;
         }
      }

      return blockedSides == 0 ? null : lastOpenSide;
   }

   public boolean isDiagonalPacketPending() {
      return this.diagonalPacketPending;
   }

   public static void bindModuleReferences(ModuleRegistry registry) {
      obstaclePasserModule = (ObstaclePasserModule)registry.getModule(ObstaclePasserModule.class);
   }
}
