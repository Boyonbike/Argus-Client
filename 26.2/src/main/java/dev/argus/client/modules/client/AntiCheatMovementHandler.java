package dev.argus.client.modules.client;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.AntiCheatRotationsChoice;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.client.utils.movement.MovementSpeedTracker;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.mixin.ServerboundMovePlayerPacketAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public final class AntiCheatMovementHandler implements ClientAccess {
   private final AntiCheatModule module;

   public AntiCheatMovementHandler(AntiCheatModule antiCheatModule) {
      this.module = antiCheatModule;
   }

   public boolean isBaritoneCompatibilityOverrideActive() {
      return false;
   }

   boolean shouldOffsetPacketYaw(ServerboundMovePlayerPacket packet) {
      if (!MovementPhysics.isLocalPlayerMoving()) {
         MovementSpeedTracker speedTracker = ClientContext.movementSpeedTracker;
         double averageSpeed = speedTracker.getAverageSpeed();
         if (averageSpeed < 5.0) {
            return false;
         }
      }

      Setting<Boolean> twoBTwoTSetting = this.module.value2b2tSetting;
      Boolean twoBTwoTEnabled = twoBTwoTSetting.getValue();
      if (!twoBTwoTEnabled) {
         return false;
      }

      if (PlayerStateUtils.isFallFlying()) {
         return false;
      }

      if (packet != null) {
         boolean inventoryScreenWithPlayerMenu = ClientAccess.argusclientMinecraft.screen instanceof AbstractContainerScreen
            && ClientAccess.argusclientMinecraft.player.containerMenu == ClientAccess.argusclientMinecraft.player.inventoryMenu;
         if (!packet.hasRotation() && !inventoryScreenWithPlayerMenu) {
            return false;
         }
      }

      return this.module.movementYawOffsetEnabled || packet == null;
   }

   public void onPacketSend(PacketEvent.Send event) {
      if (event.getPacket() instanceof ServerboundMovePlayerPacket packet && this.shouldOffsetPacketYaw(packet)) {
         ((ServerboundMovePlayerPacketAccessor)packet).setYaw(packet.getYRot(0.0F) + AntiCheatModule.ROTATION_JITTER_STEP);
      }
   }

   public void prepareMovement() {
      if (this.shouldPrepareMovement()) {
         Setting<AntiCheatRotationsChoice> rotationModeSetting = this.module.rotationsSetting;
         if (rotationModeSetting.getValue() == AntiCheatRotationsChoice.SILENT) {
            LocalPlayer playerForX = ClientAccess.argusclientMinecraft.player;
            double playerX = playerForX.getX();
            LocalPlayer playerForY = ClientAccess.argusclientMinecraft.player;
            double playerY = playerForY.getY();
            LocalPlayer playerForZ = ClientAccess.argusclientMinecraft.player;
            double playerZ = playerForZ.getZ();
            LocalPlayer playerForYaw = ClientAccess.argusclientMinecraft.player;
            float playerYaw = playerForYaw.getYRot();
            LocalPlayer playerForPitch = ClientAccess.argusclientMinecraft.player;
            float playerPitch = playerForPitch.getXRot();
            LocalPlayer playerForGround = ClientAccess.argusclientMinecraft.player;
            boolean capturedOnGround = playerForGround.onGround();
            PacketSender.sendPositionRotation(playerX, playerY, playerZ, playerYaw, playerPitch, capturedOnGround);
         }
      }
   }

   public boolean shouldPrepareMovement() {
      return this.shouldOffsetPacketYaw(null);
   }
}
