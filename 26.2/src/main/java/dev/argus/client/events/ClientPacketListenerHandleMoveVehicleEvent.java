package dev.argus.client.events;

import net.minecraft.world.phys.Vec3;

public final class ClientPacketListenerHandleMoveVehicleEvent extends CancellableEvent {
   private Vec3 position;

   public ClientPacketListenerHandleMoveVehicleEvent(Vec3 position) {
      this.position = position;
   }

   public Vec3 getPosition() {
      return this.position;
   }
}
