package dev.argus.client.modules.movement.elytratrip;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.argus.client.modules.movement.elytratrip.DisconnectKind.Kind;
import org.junit.jupiter.api.Test;

class DisconnectKindTest {
   @Test
   void transferDuringHomeIsNotADisconnect() {
      assertEquals(Kind.TRANSFER, DisconnectKind.classify(true, true, 1_500, false));
      assertEquals(Kind.TRANSFER, DisconnectKind.classify(true, true, 0, false));
   }

   @Test
   void transferWithoutHomeIsStillTransfer() {
      assertEquals(Kind.TRANSFER, DisconnectKind.classify(true, false, 0, false));
   }

   @Test
   void disconnectedScreenIsDisconnect() {
      assertEquals(Kind.DISCONNECT, DisconnectKind.classify(false, true, 0, true));
      assertEquals(Kind.DISCONNECT, DisconnectKind.classify(false, false, 0, true));
   }

   @Test
   void nothingIsNone() {
      assertEquals(Kind.NONE, DisconnectKind.classify(false, false, 0, false));
   }
}
