package dev.argus.client.utils.movement.comet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.argus.client.utils.movement.comet.AdaptiveFlightController.Motion;
import org.junit.jupiter.api.Test;

/** Comet's cruise, boost and boost-to-cruise behaviour, with Comet's default settings. */
class CometHorizontalFlightTest {
   private static final double EPS = 1.0E-9;
   private static final double CRUISE = 41.2 / 20.0;
   private static final double BOOST = 300.0 / 20.0;
   private static final CometHorizontalFlight.Settings DEFAULTS =
      new CometHorizontalFlight.Settings(0.15, 0.3, 15.0, CRUISE, true, BOOST, 0.3);

   private static double fly(CometHorizontalFlight flight, int ticks, boolean sprint) {
      Motion out = Motion.ZERO;
      for (int i = 0; i < ticks; i++) out = flight.step(DEFAULTS, 1, 0, 0.0F, sprint);
      return out.horizontalLength();
   }

   @Test
   void keysFollowCometYawConvention() {
      Motion forward = CometHorizontalFlight.requested(1, 0, 0.0F, 2.0);
      assertEquals(0.0, forward.x(), EPS);
      assertEquals(2.0, forward.z(), EPS);
      assertEquals(-2.0, CometHorizontalFlight.requested(0, 1, 0.0F, 2.0).x(), EPS);
      assertEquals(2.0, CometHorizontalFlight.requested(1, 1, 37.0F, 2.0).horizontalLength(), EPS);
      assertEquals(Motion.ZERO, CometHorizontalFlight.requested(0, 0, 0.0F, 2.0));
   }

   @Test
   void cruiseAcceleratesFromRestAtTheAccelerationRate() {
      CometHorizontalFlight flight = new CometHorizontalFlight();
      assertEquals(0.15, fly(flight, 1, false), EPS);
      assertEquals(0.30, fly(flight, 1, false), EPS);
      assertEquals(CRUISE, fly(flight, 20, false), EPS);
   }

   @Test
   void boostStartsAtSixtyBpsAndClimbsVolytrasCurveWithoutJumping() {
      CometHorizontalFlight flight = new CometHorizontalFlight();
      fly(flight, 30, false);
      assertEquals(CometHorizontalFlight.BOOST_START, fly(flight, 1, true), EPS);
      double expected = CometHorizontalFlight.BOOST_START;
      for (int i = 0; i < 50; i++) {
         expected = BoostRamp.next(expected, 0.3, CometHorizontalFlight.BOOST_TOP, CometHorizontalFlight.BOOST_TOP);
         assertEquals(expected, fly(flight, 1, true), EPS);
      }
      // About seven seconds to 285 b/s, never above 300 b/s.
      double speed = fly(flight, 90, true);
      assertTrue(speed * 20.0 >= 285.0, "speed " + speed * 20.0);
      assertTrue(fly(flight, 400, true) <= CometHorizontalFlight.BOOST_TOP + EPS);
   }

   @Test
   void boostEngagesFromCurrentSpeedWhenAlreadyFaster() {
      CometHorizontalFlight flight = new CometHorizontalFlight();
      CometHorizontalFlight.Settings fastCruise = new CometHorizontalFlight.Settings(0.15, 0.3, 15.0, 4.0, true, 15.0, 0.3);
      for (int i = 0; i < 60; i++) flight.step(fastCruise, 1, 0, 0.0F, false);
      double next = BoostRamp.next(4.0, 0.3, CometHorizontalFlight.BOOST_TOP, CometHorizontalFlight.BOOST_TOP);
      assertEquals(4.0, flight.step(fastCruise, 1, 0, 0.0F, true).horizontalLength(), EPS);
      assertEquals(next, flight.step(fastCruise, 1, 0, 0.0F, true).horizontalLength(), EPS);
   }

   @Test
   void boostCapStopsTheCurveShort() {
      CometHorizontalFlight flight = new CometHorizontalFlight();
      CometHorizontalFlight.Settings capped = new CometHorizontalFlight.Settings(0.15, 0.3, 15.0, CRUISE, true, 6.0, 0.3);
      Motion out = Motion.ZERO;
      for (int i = 0; i < 400; i++) out = flight.step(capped, 1, 0, 0.0F, true);
      assertEquals(6.0, out.horizontalLength(), EPS);
   }

   @Test
   void boostingWithoutMovementKeysDoesNothing() {
      CometHorizontalFlight flight = new CometHorizontalFlight();
      assertEquals(0.0, flight.step(DEFAULTS, 0, 0, 0.0F, true).horizontalLength(), EPS);
      assertFalse(flight.boosting());
   }

   @Test
   void releasingBoostBrakesHardAtSpeedThenGentlyIntoCruise() {
      CometHorizontalFlight flight = new CometHorizontalFlight();
      double top = fly(flight, 600, true);
      double first = top - fly(flight, 1, false);
      Braking braking = new Braking(0.3, CometHorizontalFlight.HORIZON, CometHorizontalFlight.BOOST_TOP);
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
      CometHorizontalFlight flight = new CometHorizontalFlight();
      fly(flight, 40, false);
      Motion coast = flight.step(DEFAULTS, 0, 0, 0.0F, false);
      assertTrue(coast.horizontalLength() > CRUISE - 0.4 && coast.horizontalLength() < CRUISE);
      assertFalse(flight.boosting());
   }

   @Test
   void setbackRewindsTheBoostRampAndHoldsOneTick() {
      // 240 b/s cap: below the curve's flat top, where a rewind still costs speed.
      CometHorizontalFlight.Settings capped = new CometHorizontalFlight.Settings(0.15, 0.3, 15.0, CRUISE, true, 12.0, 0.3);
      CometHorizontalFlight flight = new CometHorizontalFlight();
      double before = 0;
      for (int i = 0; i < 400; i++) before = flight.step(capped, 1, 0, 0.0F, true).horizontalLength();
      assertEquals(12.0, before, EPS);
      flight.setback(false);
      double rewound = BoostRamp.rewind(before, 0.3, CometHorizontalFlight.BOOST_TOP, CometHorizontalFlight.BOOST_START, 15);
      assertTrue(rewound < before);
      assertEquals(rewound, flight.step(capped, 1, 0, 0.0F, true).horizontalLength(), EPS);
      assertEquals(BoostRamp.next(rewound, 0.3, CometHorizontalFlight.BOOST_TOP, 12.0),
         flight.step(capped, 1, 0, 0.0F, true).horizontalLength(), EPS);
   }

   @Test
   void teleportStopsControlledMovement() {
      CometHorizontalFlight flight = new CometHorizontalFlight();
      fly(flight, 120, true);
      flight.setback(true);
      assertEquals(0.0, flight.output().horizontalLength(), EPS);
      assertEquals(CometHorizontalFlight.BOOST_START, fly(flight, 1, true), EPS);
   }

   @Test
   void wallContactKeepsOnlyWhatGotThrough() {
      CometHorizontalFlight flight = new CometHorizontalFlight();
      fly(flight, 120, true);
      flight.collided(0.0, 1.0);
      assertEquals(1.0, flight.output().horizontalLength(), EPS);
      assertEquals(CometHorizontalFlight.BOOST_START, flight.boostSpeed(), EPS);
   }

   @Test
   void boostingHoldsTheWhole3dMoveToTheServerLimit() {
      CometHorizontalFlight flight = new CometHorizontalFlight();
      assertEquals(Double.POSITIVE_INFINITY, flight.totalCeiling());
      fly(flight, 1, true);
      assertEquals(ServerMoveLimit.SINGLE, flight.totalCeiling());
   }
}
