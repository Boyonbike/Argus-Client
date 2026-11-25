package dev.argus.client.modules.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.argus.client.modules.movement.BaritoneElytraHandoff.End;
import dev.argus.client.modules.movement.BaritoneElytraHandoff.Refusal;
import org.junit.jupiter.api.Test;

/** Ported from Comet's ElytraHandoffTest. */
class BaritoneElytraHandoffTest {
   // Baritone's default elytraMinimumDurability is 5.
   private static Refusal check(double distance, int durability, boolean fallFlying, boolean onGround, boolean clear) {
      return BaritoneElytraHandoff.check(distance, 300, true, durability, 5, fallFlying, onGround, clear);
   }

   @Test
   void longTripOnOpenGroundIsFlown() {
      assertNull(check(5000, 432, false, true, true));
   }

   @Test
   void shortTripIsWalked() {
      assertEquals(Refusal.SHORT, check(299, 432, false, true, true));
      assertNull(check(300, 432, false, true, true));
   }

   @Test
   void unsupportedSystemIsWalked() {
      assertEquals(Refusal.UNSUPPORTED, BaritoneElytraHandoff.check(5000, 300, false, 432, 5, false, true, true));
   }

   @Test
   void needsAnElytraBaritoneWontLandStraightAway() {
      assertEquals(Refusal.NO_ELYTRA, check(5000, -1, false, true, true));
      assertEquals(Refusal.NO_ELYTRA, check(5000, 4, false, true, true));
      assertNull(check(5000, 5, false, true, true));
   }

   @Test
   void coveredOrAirborneIsCheckedAgainLater() {
      assertEquals(Refusal.COVERED, check(5000, 432, false, true, false));
      assertEquals(Refusal.AIRBORNE, check(5000, 432, false, false, true));
      assertFalse(Refusal.COVERED.settled);
      assertFalse(Refusal.AIRBORNE.settled);
      assertTrue(Refusal.NO_ELYTRA.settled);
      assertTrue(Refusal.SHORT.settled);
   }

   @Test
   void alreadyGlidingIsFlownWhateverIsAbove() {
      assertNull(check(5000, 432, true, false, false));
   }

   @Test
   void flightEnds() {
      assertEquals(End.STOPPED, BaritoneElytraHandoff.end(true, true, 20, 300), "still gliding");
      assertEquals(End.STOPPED, BaritoneElytraHandoff.end(false, false, 20, 300), "never landed");
      assertEquals(End.WALK_REST, BaritoneElytraHandoff.end(false, true, 40, 300));
      assertEquals(End.LANDED_SHORT, BaritoneElytraHandoff.end(false, true, 300, 300));
   }

   @Test
   void distanceIgnoresHeight() {
      assertEquals(500, BaritoneElytraHandoff.horizontalDistance(0, 0, 300, 400), 1e-9);
   }

   @Test
   void velocityIsTheSetSpeedAlongTheHeading() {
      double[] south = BaritoneElytraHandoff.velocity(40, 0, 0);
      assertEquals(0, south[0], 1e-9);
      assertEquals(0, south[1], 1e-9);
      assertEquals(2, south[2], 1e-9);
      double[] west = BaritoneElytraHandoff.velocity(40, 90, 0);
      assertEquals(-2, west[0], 1e-9);
      double[] up = BaritoneElytraHandoff.velocity(40, 0, -90);
      assertEquals(2, up[1], 1e-9);
      double[] climbing = BaritoneElytraHandoff.velocity(40, 30, -20);
      assertEquals(2, Math.sqrt(climbing[0] * climbing[0] + climbing[1] * climbing[1] + climbing[2] * climbing[2]), 1e-9);
   }
}
