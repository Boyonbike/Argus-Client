package dev.argus.client.modules.movement.elytratrip;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CheckpointTimingTest {
   private static final long INTERVAL = 300_000;

   @Test
   void dueAfterInterval() {
      assertTrue(CheckpointTiming.due(301_000, 1_000, INTERVAL, 0, false, true));
      assertFalse(CheckpointTiming.due(300_999, 1_000, INTERVAL, 0, false, true));
   }

   @Test
   void firstCheckpointIsDueStraightAway() {
      assertTrue(CheckpointTiming.due(5_000, 0, INTERVAL, 0, false, true));
   }

   @Test
   void notDueWhileLanding() {
      assertFalse(CheckpointTiming.due(600_000, 0, INTERVAL, 0, true, true));
   }

   @Test
   void notDueOnServerCooldown() {
      assertFalse(CheckpointTiming.due(600_000, 0, INTERVAL, 1, false, true));
   }

   @Test
   void notDueWhenNotFlying() {
      assertFalse(CheckpointTiming.due(600_000, 0, INTERVAL, 0, false, false));
   }

   @Test
   void refreshOnlyWhenStaleAndOffCooldown() {
      assertTrue(CheckpointTiming.refreshBeforeRefill(160_000, 100_000, 0));
      assertFalse(CheckpointTiming.refreshBeforeRefill(159_999, 100_000, 0));
      assertFalse(CheckpointTiming.refreshBeforeRefill(500_000, 100_000, 5_000));
   }
}
