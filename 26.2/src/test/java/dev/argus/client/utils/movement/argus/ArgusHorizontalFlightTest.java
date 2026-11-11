package dev.argus.client.utils.movement.argus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.argus.client.utils.movement.argus.AdaptiveFlightController.Motion;
import org.junit.jupiter.api.Test;

/** Comet's cruise, boost and boost-to-cruise behaviour, with Comet's default settings. */
class ArgusHorizontalFlightTest {
   private static final double EPS = 1.0E-9;
   private static final double CRUISE = 41.2 / 20.0;
   private static final double BOOST = 300.0 / 20.0;
   private static final ArgusHorizontalFlight.Settings DEFAULTS =
      new ArgusHorizontalFlight.Settings(0.15, 0.3, 15.0, CRUISE, true, BOOST, 0.3);

   private static double fly(ArgusHorizontalFlight flight, int ticks, boolean sprint) {
      Motion out = Motion.ZERO;
      for (int i = 0; i < ticks; i++) out = flight.step(DEFAULTS, 1, 0, 0.0F, sprint);
      return out.horizontalLength();
   }

   @Test
   void keysFollowYawConvention() {
      Motion forward = ArgusHorizontalFlight.requested(1, 0, 0.0F, 2.0);
      assertEquals(0.0, forward.x(), EPS);
      assertEquals(2.0, forward.z(), EPS);
      assertEquals(-2.0, ArgusHorizontalFlight.requested(0, 1, 0.0F, 2.0).x(), EPS);
      assertEquals(2.0, ArgusHorizontalFlight.requested(1, 1, 37.0F, 2.0).horizontalLength(), EPS);
      assertEquals(Motion.ZERO, ArgusHorizontalFlight.requested(0, 0, 0.0F, 2.0));
   }

   @Test
   void cruiseAcceleratesFromRestAtTheAccelerationRate() {
      ArgusHorizontalFlight flight = new ArgusHorizontalFlight();
      assertEquals(0.15, fly(flight, 1, false), EPS);
      assertEquals(0.30, fly(flight, 1, false), EPS);
      assertEquals(CRUISE, fly(flight, 20, false), EPS);
   }

   @Test
   void boostStartsAtSixtyBpsAndClimbsVolytrasCurveWithoutJumping() {
      ArgusHorizontalFlight flight = new ArgusHorizontalFlight();
      fly(flight, 30, false);
      assertEquals(ArgusHorizontalFlight.BOOST_START, fly(flight, 1, true), EPS);
      double expected = ArgusHorizontalFlight.BOOST_START;
      for (int i = 0; i < 50; i++) {
         expected = BoostRamp.next(expected, 0.3, ArgusHorizontalFlight.BOOST_TOP, ArgusHorizontalFlight.BOOST_TOP);
         assertEquals(expected, fly(flight, 1, true), EPS);
      }
      // About seven seconds to 285 b/s, never above 300 b/s.
      double speed = fly(flight, 90, true);
      assertTrue(speed * 20.0 >= 285.0, "speed " + speed * 20.0);
      assertTrue(fly(flight, 400, true) <= ArgusHorizontalFlight.BOOST_TOP + EPS);
   }

   @Test
   void boostEngagesFromCurrentSpeedWhenAlreadyFaster() {
      ArgusHorizontalFlight flight = new ArgusHorizontalFlight();
      ArgusHorizontalFlight.Settings fastCruise = new ArgusHorizontalFlight.Settings(0.15, 0.3, 15.0, 4.0, true, 15.0, 0.3);
      for (int i = 0; i < 60; i++) flight.step(fastCruise, 1, 0, 0.0F, false);
      double next = BoostRamp.next(4.0, 0.3, ArgusHorizontalFlight.BOOST_TOP, ArgusHorizontalFlight.BOOST_TOP);
      assertEquals(4.0, flight.step(fastCruise, 1, 0, 0.0F, true).horizontalLength(), EPS);
      assertEquals(next, flight.step(fastCruise, 1, 0, 0.0F, true).horizontalLength(), EPS);
   }

   @Test
   void boostCapStopsTheCurveShort() {
      ArgusHorizontalFlight flight = new ArgusHorizontalFlight();
      ArgusHorizontalFlight.Settings capped = new ArgusHorizontalFlight.Settings(0.15, 0.3, 15.0, CRUISE, true, 6.0, 0.3);
      Motion out = Motion.ZERO;
      for (int i = 0; i < 400; i++) out = flight.step(capped, 1, 0, 0.0F, true);
      assertEquals(6.0, out.horizontalLength(), EPS);
   }

   /** Base/Boost Speeds off: Volytra's own curve (custom start, step and plateau) always engaged while moving. */
   @Test
   void volytraCurveRunsWhileMovingWithItsOwnStartStepAndCeiling() {
      ArgusHorizontalFlight.Settings curve = new ArgusHorizontalFlight.Settings(0.15, 0.3, 15.0, 2.0, true,
         10.0, 0.5, 12.0, 2.0);
      ArgusHorizontalFlight flight = new ArgusHorizontalFlight();
      assertEquals(2.0, flight.step(curve, 1, 0, 0.0F, true).horizontalLength(), EPS);
      double expected = 2.0;
      for (int i = 0; i < 30; i++) {
         expected = BoostRamp.next(expected, 0.5, 12.0, 10.0);
         assertEquals(expected, flight.step(curve, 1, 0, 0.0F, true).horizontalLength(), EPS);
      }
      Motion out = Motion.ZERO;
      for (int i = 0; i < 400; i++) out = flight.step(curve, 1, 0, 0.0F, true);
      assertEquals(10.0, out.horizontalLength(), EPS);
      assertEquals(ServerMoveLimit.SINGLE, flight.totalCeiling());
   }

   @Test
   void volytraCurveSetbackCostsSpeedInsteadOfKeepingMomentum() {
      ArgusHorizontalFlight.Settings curve = new ArgusHorizontalFlight.Settings(0.15, 0.3, 15.0, 2.999, true,
         14.999, 0.3, 14.999, 2.999);
      ArgusHorizontalFlight flight = new ArgusHorizontalFlight();
      double before = 0;
      for (int i = 0; i < 40; i++) before = flight.step(curve, 1, 0, 0.0F, true).horizontalLength();
      flight.setback(false);
      double after = flight.step(curve, 1, 0, 0.0F, true).horizontalLength();
      assertEquals(BoostRamp.rewind(before, 0.3, 14.999, 2.999, 15), after, EPS);
      assertTrue(after < before - 1.0, "setback at " + before * 20 + " b/s only dropped to " + after * 20);
   }

   @Test
   void boostJumpKicksAheadAlongTheCurveAndKicksLessWhenAlreadyFast() {
      ArgusHorizontalFlight.Settings slow = new ArgusHorizontalFlight.Settings(0.15, 0.3, 15.0, 3.0, true,
         14.999, 0.3, 14.999, 2.999, 10);
      ArgusHorizontalFlight flight = new ArgusHorizontalFlight();
      for (int i = 0; i < 40; i++) flight.step(slow, 1, 0, 0.0F, false);
      double expected = 3.0;
      for (int i = 0; i < 10; i++) expected = BoostRamp.next(expected, 0.3, 14.999, 14.999);
      double kicked = flight.step(slow, 1, 0, 0.0F, true).horizontalLength();
      assertEquals(expected, kicked, EPS);
      double fromBase = kicked - 3.0;
      // About 60 -> 104 b/s from a 60 b/s base.
      assertEquals(104.0, kicked * 20.0, 1.0);
      // Next tick carries on along the curve, with no second kick.
      assertEquals(BoostRamp.next(kicked, 0.3, 14.999, 14.999), flight.step(slow, 1, 0, 0.0F, true).horizontalLength(), EPS);

      ArgusHorizontalFlight.Settings fast = new ArgusHorizontalFlight.Settings(0.15, 0.3, 15.0, 10.0, true,
         14.999, 0.3, 14.999, 2.999, 10);
      ArgusHorizontalFlight cruising = new ArgusHorizontalFlight();
      for (int i = 0; i < 100; i++) cruising.step(fast, 1, 0, 0.0F, false);
      double fastKick = cruising.step(fast, 1, 0, 0.0F, true).horizontalLength() - 10.0;
      assertTrue(fastKick > 0 && fastKick < fromBase, "kick at speed " + fastKick + " vs from base " + fromBase);
   }

   @Test
   void boostJumpNeverPassesTheBoostCap() {
      ArgusHorizontalFlight.Settings capped = new ArgusHorizontalFlight.Settings(0.15, 0.3, 15.0, 3.0, true,
         4.0, 0.3, 14.999, 2.999, 40);
      ArgusHorizontalFlight flight = new ArgusHorizontalFlight();
      assertEquals(4.0, flight.step(capped, 1, 0, 0.0F, true).horizontalLength(), EPS);
   }

   @Test
   void boostingWithoutMovementKeysDoesNothing() {
      ArgusHorizontalFlight flight = new ArgusHorizontalFlight();
      assertEquals(0.0, flight.step(DEFAULTS, 0, 0, 0.0F, true).horizontalLength(), EPS);
      assertFalse(flight.boosting());
   }

   @Test
   void releasingBoostBrakesHardAtSpeedThenGentlyIntoCruise() {
      ArgusHorizontalFlight flight = new ArgusHorizontalFlight();
      double top = fly(flight, 600, true);
      double first = top - fly(flight, 1, false);
      Braking braking = new Braking(0.3, ArgusHorizontalFlight.HORIZON, ArgusHorizontalFlight.BOOST_TOP);
      assertEquals(braking.capacity(top), first, 1.0E-6);
      assertTrue(first > 2.0, "boost speed sheds fast: " + first);
      int ticks = 1;
      double speed;
      while ((speed = fly(flight, 1, false)) > CRUISE + EPS) ticks++;
      assertEquals(CRUISE, speed, EPS);
      assertTrue(ticks > 5 && ticks < 30, "boost to cruise took " + ticks + " ticks");
   }

   @Test
   void releasingKeysCoasts() {
      ArgusHorizontalFlight flight = new ArgusHorizontalFlight();
      fly(flight, 40, false);
      Motion coast = flight.step(DEFAULTS, 0, 0, 0.0F, false);
      assertTrue(coast.horizontalLength() > CRUISE - 0.4 && coast.horizontalLength() < CRUISE);
      assertFalse(flight.boosting());
   }

   @Test
   void setbackRewindsTheBoostRampAndHoldsOneTick() {
      // 240 b/s cap: below the curve's flat top, where a rewind still costs speed.
      ArgusHorizontalFlight.Settings capped = new ArgusHorizontalFlight.Settings(0.15, 0.3, 15.0, CRUISE, true, 12.0, 0.3);
      ArgusHorizontalFlight flight = new ArgusHorizontalFlight();
      double before = 0;
      for (int i = 0; i < 400; i++) before = flight.step(capped, 1, 0, 0.0F, true).horizontalLength();
      assertEquals(12.0, before, EPS);
      flight.setback(false);
      double rewound = BoostRamp.rewind(before, 0.3, ArgusHorizontalFlight.BOOST_TOP, ArgusHorizontalFlight.BOOST_START, 15);
      assertTrue(rewound < before);
      assertEquals(rewound, flight.step(capped, 1, 0, 0.0F, true).horizontalLength(), EPS);
      assertEquals(BoostRamp.next(rewound, 0.3, ArgusHorizontalFlight.BOOST_TOP, 12.0),
         flight.step(capped, 1, 0, 0.0F, true).horizontalLength(), EPS);
   }

   @Test
   void teleportStopsControlledMovement() {
      ArgusHorizontalFlight flight = new ArgusHorizontalFlight();
      fly(flight, 120, true);
      flight.setback(true);
      assertEquals(0.0, flight.output().horizontalLength(), EPS);
      assertEquals(ArgusHorizontalFlight.BOOST_START, fly(flight, 1, true), EPS);
   }

   @Test
   void wallContactKeepsOnlyWhatGotThrough() {
      ArgusHorizontalFlight flight = new ArgusHorizontalFlight();
      fly(flight, 120, true);
      flight.collided(0.0, 1.0);
      assertEquals(1.0, flight.output().horizontalLength(), EPS);
      assertEquals(ArgusHorizontalFlight.BOOST_START, flight.boostSpeed(), EPS);
   }

   @Test
   void boostingHoldsTheWhole3dMoveToTheServerLimit() {
      ArgusHorizontalFlight flight = new ArgusHorizontalFlight();
      assertEquals(Double.POSITIVE_INFINITY, flight.totalCeiling());
      fly(flight, 1, true);
      assertEquals(ServerMoveLimit.SINGLE, flight.totalCeiling());
   }
}
