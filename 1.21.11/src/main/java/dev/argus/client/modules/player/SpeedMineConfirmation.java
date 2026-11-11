package dev.argus.client.modules.player;

import dev.argus.client.settings.options.SpeedMineConfirmChoice;

/**
 * Tracks server confirmation of Instant rebreaks on one block (positions are {@code BlockPos.asLong()}).
 *
 * <p>A block enters the rebreak chain once a break has been sent for it and the server has answered with air.
 * Breaks before that are retries of the first break and are never held back. After that, each rebreak is
 * pending until the server sends air again or {@link #PENDING_TIMEOUT_MILLIS} passes; Strict waits for it,
 * Strong ignores it, and Bypass behaves as Strict until one rebreak has been confirmed.
 */
final class SpeedMineConfirmation {
   /** A rebreak the server never answered stops blocking after this long. */
   static final long PENDING_TIMEOUT_MILLIS = 2000L;

   private boolean hasBroken;
   private long brokenPos;
   private boolean confirmed;
   private boolean pending;
   private long pendingDeadline;
   private boolean bypassConfirmed;

   /** Records a break (STOP) sent at {@code pos}. A different block starts a new chain. */
   void onBreakSent(long pos, long now) {
      if (!this.hasBroken || this.brokenPos != pos) {
         this.hasBroken = true;
         this.brokenPos = pos;
         this.confirmed = false;
         this.pending = false;
         this.bypassConfirmed = false;
      } else if (this.confirmed) {
         this.pending = true;
         this.pendingDeadline = now + PENDING_TIMEOUT_MILLIS;
      }
   }

   /** The server set {@code pos} to air. */
   void onServerAir(long pos) {
      if (!this.hasBroken || this.brokenPos != pos) {
         return;
      }
      this.confirmed = true;
      if (this.pending) {
         this.bypassConfirmed = true;
      }
      this.pending = false;
   }

   /** True once a break at {@code pos} has been confirmed, so further breaks there are rebreaks. */
   boolean isRebreak(long pos) {
      return this.hasBroken && this.brokenPos == pos && this.confirmed;
   }

   /** Strong never waits; Bypass stops waiting once one rebreak has been confirmed. */
   boolean isStrong(SpeedMineConfirmChoice mode) {
      return mode == SpeedMineConfirmChoice.STRONG || mode == SpeedMineConfirmChoice.BYPASS && this.bypassConfirmed;
   }

   /** Whether a rebreak may be sent now: always when strong, otherwise once the last one is answered or timed out. */
   boolean canRebreak(SpeedMineConfirmChoice mode, long now) {
      if (this.pending && now >= this.pendingDeadline) {
         this.pending = false;
      }
      return !this.pending || this.isStrong(mode);
   }

   void clear() {
      this.hasBroken = false;
      this.confirmed = false;
      this.pending = false;
      this.bypassConfirmed = false;
   }
}
