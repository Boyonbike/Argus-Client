package dev.argus.client.modules.movement.elytratrip;

/** When ElytraTrip moves its checkpoint home. Pure. */
public final class CheckpointTiming {
   /** A refill refreshes the checkpoint first when it is older than this. */
   public static final long REFRESH_OLDER_THAN_MS = 60_000;

   private CheckpointTiming() {
   }

   /**
    * Whether to send /sethome now. Timed from the last /sethome sent, not the last success, so replies the scheduler
    * does not recognise never turn into a /sethome every tick.
    *
    * @param lastSentAt when the last /sethome was sent, 0 for never
    * @param serverWaitMs cooldown or retry wait the server reported that is still running
    */
   public static boolean due(long now, long lastSentAt, long intervalMillis, long serverWaitMs, boolean landing, boolean flying) {
      if (!flying || landing || serverWaitMs > 0) return false;
      return lastSentAt == 0 || now - lastSentAt >= intervalMillis;
   }

   public static boolean refreshBeforeRefill(long now, long lastSuccessAt, long serverWaitMs) {
      return serverWaitMs == 0 && now - lastSuccessAt >= REFRESH_OLDER_THAN_MS;
   }
}
