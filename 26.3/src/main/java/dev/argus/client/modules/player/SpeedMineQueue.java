package dev.argus.client.modules.player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.LongPredicate;

/**
 * Blocks clicked while SpeedMine is busy, mined in click order (positions are {@code BlockPos.asLong()}).
 * Duplicates are ignored, the queue holds at most {@link #MAX_SIZE} blocks, and a head that keeps failing
 * to start is dropped after {@link #MAX_START_ATTEMPTS} tries so it can never block the rest.
 */
final class SpeedMineQueue {
   static final int MAX_SIZE = 32;
   static final int MAX_START_ATTEMPTS = 20;

   private final LinkedHashSet<Long> positions = new LinkedHashSet<>();
   private int headAttempts;

   /** Adds {@code pos} to the end; false if it was already queued or the queue is full. */
   boolean add(long pos) {
      return this.positions.size() < MAX_SIZE && this.positions.add(pos);
   }

   boolean contains(long pos) {
      return this.positions.contains(pos);
   }

   boolean isEmpty() {
      return this.positions.isEmpty();
   }

   int size() {
      return this.positions.size();
   }

   /** The next block to mine; only valid when not empty. */
   long head() {
      return this.positions.iterator().next();
   }

   /** The head started mining: remove it. */
   void headStarted() {
      this.removeHead();
   }

   /** The head was asked to start but did not; drops it after too many tries. Returns true if dropped. */
   boolean headFailed() {
      if (++this.headAttempts >= MAX_START_ATTEMPTS) {
         this.removeHead();
         return true;
      }
      return false;
   }

   /** Removes every queued block matching {@code invalid} (broken, out of range, no longer allowed). */
   List<Long> prune(LongPredicate invalid) {
      List<Long> removed = new ArrayList<>();
      boolean headRemoved = false;
      boolean first = true;
      for (var it = this.positions.iterator(); it.hasNext(); ) {
         long pos = it.next();
         if (invalid.test(pos)) {
            it.remove();
            removed.add(pos);
            headRemoved |= first;
         }
         first = false;
      }
      if (headRemoved) {
         this.headAttempts = 0;
      }
      return removed;
   }

   List<Long> snapshot() {
      return new ArrayList<>(this.positions);
   }

   void clear() {
      this.positions.clear();
      this.headAttempts = 0;
   }

   private void removeHead() {
      if (!this.positions.isEmpty()) {
         this.positions.remove(this.head());
      }
      this.headAttempts = 0;
   }
}
