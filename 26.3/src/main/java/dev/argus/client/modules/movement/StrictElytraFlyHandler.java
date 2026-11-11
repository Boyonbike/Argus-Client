package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.TickRateController;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.movement.ElytraFlyModeHandler;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.mixin.ServerboundMovePlayerPacketAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

public class StrictElytraFlyHandler extends ElytraFlyModeHandler {
   private long accelerationStartTimeMillis;
   private boolean takeoffPending;
   private boolean awaitingFlight;
   private boolean fireworkLaunchPending;

   public StrictElytraFlyHandler(ElytraFlyModule module) {
      super(module);
   }

   @Override
   public void onEnable() {
      if (ClientAccess.argusclientMinecraft.player.isFallFlying() && !ClientAccess.argusclientMinecraft.player.onGround()) {
         this.takeoffPending = true;
         ClientAccess.argusclientMinecraft.player.stopFallFlying();
      }

      this.awaitingFlight = false;
      this.fireworkLaunchPending = false;
      this.accelerationStartTimeMillis = System.currentTimeMillis();
   }

   @Override
   public void handleOutgoingPacket(PacketEvent.Send event) {
      if (event.getPacket() instanceof ServerboundMovePlayerPacket packet
         && ClientAccess.argusclientMinecraft.player.isFallFlying()
         && !ClientAccess.argusclientMinecraft.player.onGround()) {
         Vec3 velocity = ClientAccess.argusclientMinecraft.player.getDeltaMovement();
         double verticalMotion = velocity.y();
         if (verticalMotion < 0.1 && MovementPhysics.isLocalPlayerMoving()) {
            ServerboundMovePlayerPacketAccessor packetAccess = (ServerboundMovePlayerPacketAccessor)packet;
            packetAccess.setPitch(-5.0F);
         }
      }
   }

   @Override
   public void handlePlayerTick(LocalPlayerTickEvent event) {
      if (ClientAccess.argusclientMinecraft.player.onGround()) {
         this.takeoffPending = false;
      } else if (ClientAccess.argusclientMinecraft.player.isFallFlying()) {
         this.takeoffPending = true;
      }

      Input input = ClientAccess.argusclientMinecraft.player.input.keyPresses;
      boolean requestTakeoff = input.jump();
      Setting<ElytraFlyModule.TakeoffChoice> takeoffSetting = this.module.takeoffSetting;
      if (takeoffSetting.getValue() == ElytraFlyModule.TakeoffChoice.STRICT) {
         LocalPlayer strictGroundPlayer = ClientAccess.argusclientMinecraft.player;
         requestTakeoff = false;
         if (!strictGroundPlayer.onGround()) {
            Vec3 velocity = ClientAccess.argusclientMinecraft.player.getDeltaMovement();
            double verticalMotion = velocity.y();
            requestTakeoff = verticalMotion < 0.1;
         }
      } else {
         ElytraFlyModule timerOwner = this.module;
         TickRateController timerController = ClientContext.tickRateController;
         timerController.releaseMultiplier(timerOwner);
      }

      if (requestTakeoff || this.awaitingFlight) {
         this.module.updateTakeoff();
         LocalPlayer playerAfterTakeoff = ClientAccess.argusclientMinecraft.player;
         this.awaitingFlight = !playerAfterTakeoff.isFallFlying();
      }
   }

   @Override
   public void handleMovement(LocalPlayerMoveEvent event) {
      if (!ClientAccess.argusclientMinecraft.player.isFallFlying() && this.takeoffPending && !ClientAccess.argusclientMinecraft.player.horizontalCollision) {
         Vec3 fallingVelocity = ClientAccess.argusclientMinecraft.player.getDeltaMovement();
         if (fallingVelocity.y() < 0.0) {
            this.awaitingFlight = true;
         }
      }

      if (ClientAccess.argusclientMinecraft.player.isFallFlying()) {
         if (!MovementPhysics.isLocalPlayerMoving()) {
            this.accelerationStartTimeMillis = System.currentTimeMillis();
         }

         float maximumSpeed = this.module.strictSpeedSetting.getValue();
         float startSpeed = this.module.startSetting.getValue();
         float elapsedMillis = (float)(System.currentTimeMillis() - this.accelerationStartTimeMillis);
         float progress = Mth.clamp(elapsedMillis / 500.0F, 0.0F, 1.0F);
         float speed = Mth.lerp(progress, startSpeed, maximumSpeed);
         double[] horizontalMotion = MovementPhysics.applyHorizontalMotion(event, speed);
         ClientAccess.argusclientMinecraft.player.setDeltaMovement(horizontalMotion[0], 0.0, horizontalMotion[1]);
         event.setY(0.0);
         ClientAccess.argusclientMinecraft.player.setDeltaMovement(ClientAccess.argusclientMinecraft.player.getDeltaMovement().with(Axis.Y, 0.0));
      }
   }

   @Override
   public void handleIncomingPacket(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket
         && ClientAccess.argusclientMinecraft.player.isFallFlying()
         && !ClientAccess.argusclientMinecraft.player.onGround()) {
         this.accelerationStartTimeMillis = System.currentTimeMillis();
         ClientAccess.argusclientMinecraft.player.stopFallFlying();
      }
   }

   @Override
   public void handleSendPosition(LocalPlayerSendPositionEvent event) {
      if (event.getStage() == EventStage.Pre) {
         boolean adjustRotation = false;
         if (this.module.isElytraEquipped() && !event.isOnGround()) {
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            if (!player.isFallFlying()) {
               adjustRotation = true;
               this.fireworkLaunchPending = false;
            } else if (!this.fireworkLaunchPending) {
               if (!MovementPhysics.isLocalPlayerMoving()) {
                  adjustRotation = true;
               } else {
                  this.fireworkLaunchPending = true;
               }
            }
         }

         if (adjustRotation) {
            float originalYaw = event.getYaw();
            int yawTick = ClientAccess.argusclientMinecraft.player.tickCount;
            int yawOffset = yawTick % 2 == 0 ? -2 : 2;
            float yaw = originalYaw + yawOffset;
            event.setYaw(yaw);
            float originalPitch = event.getPitch();
            int pitchTick = ClientAccess.argusclientMinecraft.player.tickCount;
            int pitchOffset = pitchTick % 2 == 0 ? -2 : 2;
            float pitch = originalPitch + pitchOffset;
            event.setPitch(pitch);
         }
      }
   }
}
