package dev.argus.client.events;

import dev.argus.client.ClientAccess;

public final class RotationRequestEvent extends CancellableEvent implements ClientAccess {
   public float getPlayerPitch() {
      return ClientAccess.argusclientMinecraft.player.getXRot();
   }

   public float getPlayerYaw() {
      return ClientAccess.argusclientMinecraft.player.getYRot();
   }
}
