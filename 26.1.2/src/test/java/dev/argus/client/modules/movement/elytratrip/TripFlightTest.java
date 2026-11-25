package dev.argus.client.modules.movement.elytratrip;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TripFlightTest {
   @Test
   void arrivedNeedsDistanceAndBaritoneInactive() {
      assertTrue(TripFlight.arrived(50.0, 50, false));
      assertFalse(TripFlight.arrived(50.1, 50, false));
      assertFalse(TripFlight.arrived(10.0, 50, true));
   }
}
