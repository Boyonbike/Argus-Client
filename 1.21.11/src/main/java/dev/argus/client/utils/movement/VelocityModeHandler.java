package dev.argus.client.utils.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.ClientPacketListenerHandleExplosionEvent;
import dev.argus.client.events.ClientPacketListenerHandleSetEntityMotionEvent;
import dev.argus.client.events.FishingHookPullEntityEvent;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.modules.movement.VelocityModule;

public abstract class VelocityModeHandler implements ClientAccess {
   protected final VelocityModule velocityModule;

   public VelocityModeHandler(VelocityModule velocityModule) {
      this.velocityModule = velocityModule;
   }

   public void onDisable() {
   }

   public void onExplosion(ClientPacketListenerHandleExplosionEvent event) {
   }

   public void onEntityMotion(ClientPacketListenerHandleSetEntityMotionEvent event) {
   }

   public void onSendPacket(PacketEvent.Send event) {
   }

   public void onUseItemOn(MultiPlayerGameModeUseItemOnEvent event) {
   }

   public void onFishingPull(FishingHookPullEntityEvent event) {
   }

   public void onSendPosition(LocalPlayerSendPositionEvent event) {
   }

   public void onReceivePacket(PacketEvent.Receive event) {
   }

   public void onTick() {
   }
}
