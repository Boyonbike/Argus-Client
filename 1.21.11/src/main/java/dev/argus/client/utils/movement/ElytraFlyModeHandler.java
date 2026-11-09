package dev.argus.client.utils.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.FireworkRocketEntityTickEvent;
import dev.argus.client.events.LivingEntityRotationEvent;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftRunTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.modules.movement.ElytraFlyModule;

public abstract class ElytraFlyModeHandler implements ClientAccess {
   protected final ElytraFlyModule module;

   protected ElytraFlyModeHandler(ElytraFlyModule module) {
      this.module = module;
   }

   public void onEnable() {
   }

   public void onDisable() {
   }

   public void handleFireworkTick(FireworkRocketEntityTickEvent event) {
   }

   public void handlePrePlayerTick(LocalPlayerPreTickEvent event) {
   }

   public void handleOutgoingPacket(PacketEvent.Send event) {
   }

   public void handlePlayerTick(LocalPlayerTickEvent event) {
   }

   public void handleMovement(LocalPlayerMoveEvent event) {
   }

   public void handleTravelRotation(LivingEntityRotationEvent event) {
   }

   public void handleClientTick(MinecraftRunTickEvent event) {
   }

   public void handleRotationManagerEvent(RotationRequestEvent event) {
   }

   public void handleIncomingPacket(PacketEvent.Receive event) {
   }

   public void handleSendPosition(LocalPlayerSendPositionEvent event) {
   }
}
