package dev.argus.client.utils.network;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.world.InteractionHand;

/**
 * Maps an Argus swing to the 26.3 server-visible packet. 26.3 replaced
 * the old hand swing packet with a unit punch packet used for the main hand;
 * the server derives the off-hand animation from other actions, so an off-hand
 * swing has no server packet.
 */
public final class SwingPackets {
   private SwingPackets() {
   }

   public static Packet<?> create(InteractionHand hand) {
      return hand == InteractionHand.MAIN_HAND ? ServerboundPunchPacket.INSTANCE : null;
   }
}
