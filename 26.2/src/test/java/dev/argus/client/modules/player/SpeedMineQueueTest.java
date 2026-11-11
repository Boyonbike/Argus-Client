package dev.argus.client.modules.player;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class SpeedMineQueueTest {
   @Test
   void minesInClickOrderWithoutDuplicates() {
      SpeedMineQueue q = new SpeedMineQueue();
      assertTrue(q.add(3L));
      assertTrue(q.add(1L));
      assertFalse(q.add(3L));
      assertTrue(q.add(2L));
      assertEquals(List.of(3L, 1L, 2L), q.snapshot());
      assertEquals(3L, q.head());
      q.headStarted();
      assertEquals(1L, q.head());
   }

   @Test
   void capsSize() {
      SpeedMineQueue q = new SpeedMineQueue();
      for (long i = 0; i < SpeedMineQueue.MAX_SIZE; i++) {
         assertTrue(q.add(i));
      }
      assertFalse(q.add(999L));
      assertEquals(SpeedMineQueue.MAX_SIZE, q.size());
   }

   @Test
   void stuckHeadIsDroppedAfterMaxAttempts() {
      SpeedMineQueue q = new SpeedMineQueue();
      q.add(5L);
      q.add(6L);
      for (int i = 1; i < SpeedMineQueue.MAX_START_ATTEMPTS; i++) {
         assertFalse(q.headFailed());
      }
      assertTrue(q.headFailed());
      assertEquals(6L, q.head());
   }

   @Test
   void attemptsResetWhenHeadChanges() {
      SpeedMineQueue q = new SpeedMineQueue();
      q.add(5L);
      q.add(6L);
      q.add(7L);
      for (int i = 1; i < SpeedMineQueue.MAX_START_ATTEMPTS; i++) {
         q.headFailed();
      }
      q.headStarted();
      assertFalse(q.headFailed());
      q.prune(pos -> pos == 6L);
      assertEquals(7L, q.head());
      for (int i = 1; i < SpeedMineQueue.MAX_START_ATTEMPTS; i++) {
         assertFalse(q.headFailed());
      }
   }

   @Test
   void pruneRemovesInvalidAndKeepsOrder() {
      SpeedMineQueue q = new SpeedMineQueue();
      q.add(1L);
      q.add(2L);
      q.add(3L);
      assertEquals(List.of(2L), q.prune(pos -> pos == 2L));
      assertEquals(List.of(1L, 3L), q.snapshot());
      q.clear();
      assertTrue(q.isEmpty());
   }
}
