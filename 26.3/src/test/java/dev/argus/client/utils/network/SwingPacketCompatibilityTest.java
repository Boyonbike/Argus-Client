package dev.argus.client.utils.network;

import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class SwingPacketCompatibilityTest {
   @Test
   void mainHandSwingUsesThePunchPacket() {
      assertSame(ServerboundPunchPacket.INSTANCE, SwingPackets.create(InteractionHand.MAIN_HAND));
   }

   @Test
   void offHandSwingHasNoServerPacket() {
      assertNull(SwingPackets.create(InteractionHand.OFF_HAND));
   }
}
