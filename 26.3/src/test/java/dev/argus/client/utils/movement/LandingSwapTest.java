package dev.argus.client.utils.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LandingSwapTest {
   private static final long LONG_AGO = 1_000;

   @Test
   void standingOnTheFloorSwapsEvenWithoutTrustingOnGround() {
      assertTrue(VolytraElytraFlyHandler.shouldLandingSwap(0.0, 0.5, -0.0784, false, LONG_AGO));
   }

   @Test
   void hoveringTwoBlocksUpNeverSwaps() {
      assertFalse(VolytraElytraFlyHandler.shouldLandingSwap(2.0, 0.5, 0.0, true, LONG_AGO));
      assertFalse(VolytraElytraFlyHandler.shouldLandingSwap(2.0, 0.5, 0.0, false, LONG_AGO));
   }

   @Test
   void heightIsAdjustable() {
      assertFalse(VolytraElytraFlyHandler.shouldLandingSwap(1.2, 0.5, -0.2, false, LONG_AGO));
      assertTrue(VolytraElytraFlyHandler.shouldLandingSwap(1.2, 1.5, -0.2, false, LONG_AGO));
   }

   @Test
   void skimmingLowWhileGlidingKeepsTheWingsUntilTouching() {
      assertFalse(VolytraElytraFlyHandler.shouldLandingSwap(0.3, 0.5, -0.1, true, LONG_AGO));
      assertTrue(VolytraElytraFlyHandler.shouldLandingSwap(0.0, 0.5, -0.1, true, LONG_AGO));
   }

   @Test
   void jumpingOrJustTakenOffNeverSwaps() {
      assertFalse(VolytraElytraFlyHandler.shouldLandingSwap(0.2, 0.5, 0.42, false, LONG_AGO));
      assertFalse(VolytraElytraFlyHandler.shouldLandingSwap(0.0, 0.5, -0.1, false, 3));
      assertTrue(VolytraElytraFlyHandler.shouldLandingSwap(0.0, 0.5, -0.1, false, VolytraElytraFlyHandler.TAKEOFF_GRACE_TICKS));
   }
}
