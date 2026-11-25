package dev.argus.client.modules.movement.elytratrip;

/**
 * Tells a 6b6t server transfer from a real disconnect. 6b6t finishes many /home teleports by moving the player to another
 * of its servers, which looks like a fresh login; that must never set off a reconnect. Pure.
 */
public final class DisconnectKind {
   public enum Kind { TRANSFER, DISCONNECT, NONE }

   private DisconnectKind() {
   }

   /**
    * @param loginPacketWhileInWorld a login packet arrived while the player was already in a world
    * @param homePending a /home the trip sent is waiting or counting down
    * @param warmupLeftMs time left on that /home's warmup
    * @param disconnectedScreen the disconnected screen is showing
    */
   public static Kind classify(boolean loginPacketWhileInWorld, boolean homePending, long warmupLeftMs, boolean disconnectedScreen) {
      // A login inside a world is the network moving the player; nothing was lost whether or not a /home caused it.
      if (loginPacketWhileInWorld) return Kind.TRANSFER;
      return disconnectedScreen ? Kind.DISCONNECT : Kind.NONE;
   }
}
