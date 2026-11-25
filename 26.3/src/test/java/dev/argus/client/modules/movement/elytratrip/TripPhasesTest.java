package dev.argus.client.modules.movement.elytratrip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.argus.client.modules.movement.elytratrip.TripPhases.EndReason;
import dev.argus.client.modules.movement.elytratrip.TripPhases.Event;
import dev.argus.client.modules.movement.elytratrip.TripPhases.Phase;
import org.junit.jupiter.api.Test;

class TripPhasesTest {
   private static TripPhases flying() {
      TripPhases phases = new TripPhases(180_000, 10);
      phases.on(Event.START, 0);
      return phases;
   }

   @Test
   void startFlies() {
      assertEquals(Phase.FLYING, flying().phase());
   }

   @Test
   void fullRefillCycle() {
      TripPhases phases = flying();
      assertEquals(Phase.REFILLING, phases.on(Event.LOW_SUPPLY, 1));
      assertEquals(Phase.RETURNING, phases.on(Event.REFILL_DONE, 2));
      assertEquals(Phase.FLYING, phases.on(Event.RETURNED, 3));
   }

   @Test
   void deathLoopParks() {
      TripPhases phases = flying();
      assertEquals(Phase.RESPAWNING, phases.on(Event.DIED, 0));
      phases.on(Event.RESPAWNED, 1);
      phases.on(Event.REFILL_DONE, 2);
      phases.on(Event.RETURNED, 3);
      assertEquals(Phase.PARKED, phases.on(Event.DIED, 179_999));
      assertTrue(phases.parkReason().contains("Died twice"));
   }

   @Test
   void deathsFurtherApartRespawn() {
      TripPhases phases = flying();
      phases.on(Event.DIED, 0);
      phases.on(Event.RESPAWNED, 1);
      phases.on(Event.REFILL_DONE, 2);
      phases.on(Event.RETURNED, 3);
      assertEquals(Phase.RESPAWNING, phases.on(Event.DIED, 180_000));
   }

   @Test
   void respawnedRefills() {
      TripPhases phases = flying();
      phases.on(Event.DIED, 0);
      assertEquals(Phase.REFILLING, phases.on(Event.RESPAWNED, 1));
   }

   @Test
   void reconnectResumesRefill() {
      TripPhases phases = flying();
      phases.on(Event.LOW_SUPPLY, 1);
      assertEquals(Phase.RECONNECTING, phases.on(Event.DISCONNECTED, 2));
      assertEquals(Phase.REFILLING, phases.on(Event.BACK_IN_WORLD, 3));
   }

   @Test
   void reconnectGivesUpAfterMax() {
      TripPhases phases = flying();
      phases.on(Event.LOW_SUPPLY, 1);
      for (int i = 0; i < 10; i++) {
         assertEquals(Phase.RECONNECTING, phases.on(Event.DISCONNECTED, 2));
         phases.on(Event.BACK_IN_WORLD, 3);
      }
      assertEquals(Phase.PARKED, phases.on(Event.DISCONNECTED, 4));
   }

   @Test
   void reachingFlyingResetsReconnects() {
      TripPhases phases = flying();
      for (int i = 0; i < 10; i++) {
         phases.on(Event.DISCONNECTED, 2);
         assertEquals(Phase.FLYING, phases.on(Event.BACK_IN_WORLD, 3));
      }
      assertEquals(Phase.RECONNECTING, phases.on(Event.DISCONNECTED, 4));
      assertEquals(0, phases.snapshot().reconnects() - 1);
   }

   @Test
   void arrivalEndsAndBlocksReconnect() {
      TripPhases phases = flying();
      assertEquals(Phase.ENDED, phases.on(Event.ARRIVED, 1));
      assertEquals(EndReason.ARRIVED, phases.endReason());
      assertFalse(phases.reconnectAllowed());
      assertEquals(Phase.ENDED, phases.on(Event.DISCONNECTED, 2));
   }

   @Test
   void shortStationEnds() {
      TripPhases phases = flying();
      phases.on(Event.LOW_SUPPLY, 1);
      assertEquals(Phase.ENDED, phases.on(Event.REFILL_SHORT, 2));
      assertEquals(EndReason.STATION_SHORT, phases.endReason());
   }

   @Test
   void teleportGiveUpEnds() {
      TripPhases phases = flying();
      phases.on(Event.LOW_SUPPLY, 1);
      assertEquals(Phase.ENDED, phases.on(Event.TELEPORT_GAVE_UP, 2));
      assertEquals(EndReason.TELEPORT_FAILED, phases.endReason());
   }

   @Test
   void refillFailedParks() {
      TripPhases phases = flying();
      phases.on(Event.LOW_SUPPLY, 1);
      assertEquals(Phase.PARKED, phases.on(Event.REFILL_FAILED, 2));
   }

   @Test
   void parkedOnlyResumesOrStops() {
      TripPhases phases = flying();
      phases.on(Event.LOW_SUPPLY, 1);
      phases.on(Event.REFILL_FAILED, 2);
      assertEquals(Phase.PARKED, phases.on(Event.LOW_SUPPLY, 3));
      assertEquals(Phase.REFILLING, phases.on(Event.RESUME, 4));
   }

   @Test
   void pauseAndResume() {
      TripPhases phases = flying();
      assertEquals(Phase.PAUSED, phases.on(Event.PAUSE, 1));
      assertFalse(phases.reconnectAllowed() && phases.phase() == Phase.FLYING);
      assertEquals(Phase.FLYING, phases.on(Event.RESUME, 2));
   }

   @Test
   void stopEnds() {
      TripPhases phases = flying();
      assertEquals(Phase.ENDED, phases.on(Event.STOP, 1));
      assertEquals(EndReason.STOPPED, phases.endReason());
   }

   @Test
   void snapshotRoundTrip() {
      TripPhases phases = flying();
      phases.on(Event.LOW_SUPPLY, 1);
      phases.on(Event.DIED, 5);
      phases.on(Event.DISCONNECTED, 6);
      TripPhases restored = TripPhases.restore(phases.snapshot(), 180_000, 10);
      assertEquals(phases.snapshot(), restored.snapshot());
      assertEquals(Phase.RESPAWNING, restored.on(Event.BACK_IN_WORLD, 7));
   }

   @Test
   void suspendParksAndResumesToCurrentPhase() {
      TripPhases phases = flying();
      phases.on(Event.LOW_SUPPLY, 1);
      phases.suspend("The game restarted mid-trip.");
      assertEquals(Phase.PARKED, phases.phase());
      assertEquals(Phase.REFILLING, phases.on(Event.RESUME, 2));
   }

   @Test
   void suspendWhileReconnectingResumesToThePhaseBeforeTheDisconnect() {
      TripPhases phases = flying();
      phases.on(Event.DISCONNECTED, 1);
      phases.suspend("The game restarted mid-trip.");
      assertEquals(Phase.FLYING, phases.on(Event.RESUME, 2));
   }

   @Test
   void reconnectCapStopsReconnecting() {
      TripPhases phases = flying();
      // Each failed reconnect attempt is another disconnect before the trip gets going again.
      for (int i = 0; i < 11; i++) phases.on(Event.DISCONNECTED, 2);
      assertEquals(Phase.PARKED, phases.phase());
      assertFalse(phases.reconnectAllowed());
      phases.on(Event.RESUME, 4);
      assertTrue(phases.reconnectAllowed());
   }

   @Test
   void threeDeathsWithoutProgressPark() {
      TripPhases phases = flying();
      long t = 0;
      for (int death = 1; death <= 2; death++) {
         assertEquals(Phase.RESPAWNING, phases.on(Event.DIED, t));
         phases.on(Event.RESPAWNED, t + 1);
         phases.on(Event.REFILL_DONE, t + 2);
         phases.on(Event.RETURNED, t + 3);
         t += 600_000;
      }
      assertEquals(Phase.PARKED, phases.on(Event.DIED, t));
      assertTrue(phases.parkReason().contains("checkpoint"));
   }

   @Test
   void progressResetsTheDeathCount() {
      TripPhases phases = flying();
      long t = 0;
      for (int death = 1; death <= 4; death++) {
         assertEquals(Phase.RESPAWNING, phases.on(Event.DIED, t));
         phases.on(Event.RESPAWNED, t + 1);
         phases.on(Event.REFILL_DONE, t + 2);
         phases.on(Event.RETURNED, t + 3);
         phases.on(Event.PROGRESS, t + 4);
         t += 600_000;
      }
   }
}
