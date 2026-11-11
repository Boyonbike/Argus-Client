package dev.argus.client.modules.player;

/** SpeedMine's tick pacing and delays, free of the client so it can be unit tested. */
final class SpeedMineTiming {
   /** Vanilla's pause between breaks, skipped by NoBreakDelay. */
   static final long BREAK_DELAY_MILLIS = 250L;
   /** Instant rebreaks with Alternative on 2b2t-style servers never go below this delay. */
   static final float MIN_2B2T_INSTANT_DELAY = 0.1F;

   private SpeedMineTiming() {
   }

   /** Per-tick progress multiplier: server TPS when synced, divided by the client timer speed. */
   static float progressScale(boolean tpsSync, float tickRateScale, float timerMultiplier) {
      float scale = tpsSync ? tickRateScale : 1.0F;
      return scale * (1.0F / timerMultiplier);
   }

   /** The vanilla break delay applies unless NoBreakDelay is on or it has already elapsed. */
   static boolean isBreakDelayActive(boolean noBreakDelay, boolean delayElapsed) {
      return !noBreakDelay && !delayElapsed;
   }

   /** The instant-rebreak delay in seconds, raised to the 2b2t floor when Alternative is in use there. */
   static float instantDelaySeconds(float configured, boolean alternativeAllowed, boolean is2b2tMode) {
      return alternativeAllowed && is2b2tMode ? Math.max(configured, MIN_2B2T_INSTANT_DELAY) : configured;
   }

   /** Server ticks elapsed over a wall-clock interval, at a TPS clamped to 0-20 (20 if unknown). */
   static double serverTicks(long elapsedMillis, float tps) {
      float safeTps = Float.isFinite(tps) ? tps : 20.0F;
      return Math.max(0L, elapsedMillis) / 1000.0 * Math.clamp(safeTps, 0.0F, 20.0F);
   }

   /**
    * Earns block starts at the server's tick rate. Fractional credit carries over so client-tick jitter
    * never throws progress away, and at most {@link #MAX_START_CREDIT} starts can be banked.
    */
   static double replenishStartCredit(double credit, long elapsedMillis, float tps) {
      return Math.min(MAX_START_CREDIT, credit + serverTicks(elapsedMillis, tps));
   }

   /** At full TPS, with an unknown TPS, or with sync off, a start never waits for credit. */
   static boolean canStart(boolean tpsSync, float tps, double credit) {
      return !tpsSync || !Float.isFinite(tps) || tps >= 20.0F || credit >= 1.0;
   }

   /** Credit left after a start; only TPS sync spends it. */
   static double spendStartCredit(boolean tpsSync, double credit) {
      return tpsSync ? Math.max(0.0, credit - 1.0) : credit;
   }

   /** Most block starts that can be banked while TPS is low. */
   static final double MAX_START_CREDIT = 2.0;

   /** Progress as a 0-1 fraction of the damage threshold, for rendering. */
   static float renderProgress(float progress, float damage) {
      return Math.clamp(progress / damage, 0.0F, 1.0F);
   }
}
