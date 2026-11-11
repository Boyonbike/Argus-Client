package dev.argus.client.events;

import dev.argus.client.ClientAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class VehicleMoveEvent extends LocalPlayerMoveEvent {
   public VehicleMoveEvent(Vec3 velocity) {
      super(velocity, null);
   }

   public static boolean isLocalPlayerVehicle(Object candidate) {
      return ClientAccess.argusclientMinecraft.player == null
         ? false
         : candidate instanceof Entity && ClientAccess.argusclientMinecraft.player.getVehicle() == candidate;
   }
}
