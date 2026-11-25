package dev.argus.client.modules.movement.elytratrip;

/** ElytraTrip's phase state machine. Pure: time is passed in, nothing touches the game. */
public final class TripPhases {
   public enum Phase {
      IDLE, FLYING, REFILLING, RETURNING, RESPAWNING, RECONNECTING, PAUSED, PARKED, ENDED;

      /** A phase in which the trip is under way and owns the player. */
      public boolean active() {
         return this != IDLE && this != PAUSED && this != PARKED && this != ENDED;
      }
   }

   public enum EndReason { NONE, ARRIVED, STATION_SHORT, TELEPORT_FAILED, STOPPED }

   public enum Event {
      START, LOW_SUPPLY, DIED, RESPAWNED, REFILL_DONE, REFILL_SHORT, REFILL_FAILED, RETURNED, DISCONNECTED, BACK_IN_WORLD, ARRIVED,
      PAUSE, RESUME, STOP, TELEPORT_GAVE_UP,
      /** The trip moved on: a new checkpoint was set. Clears the deaths-without-progress count. */
      PROGRESS
   }

   /** Everything needed to carry the machine across a restart. {@code lastDeathAt} is {@link Long#MIN_VALUE} for none. */
   public record Snapshot(Phase phase, Phase resumeTo, EndReason endReason, long lastDeathAt, int reconnects, String parkReason,
                          int deathsWithoutProgress, boolean reconnectsExhausted) {
   }

   private final long deathLoopMillis;
   private final int maxReconnects;
   private Phase phase = Phase.IDLE;
   private Phase resumeTo = Phase.FLYING;
   private EndReason endReason = EndReason.NONE;
   private long lastDeathAt = Long.MIN_VALUE;
   private int reconnects;
   private String parkReason = "";
   private int deathsWithoutProgress;
   /** Parked because reconnecting kept failing: stop reconnecting until Resume. */
   private boolean reconnectsExhausted;
   /** This many deaths with no new checkpoint between them is a loop at a dangerous checkpoint. */
   public static final int DEATHS_WITHOUT_PROGRESS = 3;

   public TripPhases(long deathLoopMillis, int maxReconnects) {
      this.deathLoopMillis = deathLoopMillis;
      this.maxReconnects = maxReconnects;
   }

   public static TripPhases restore(Snapshot snapshot, long deathLoopMillis, int maxReconnects) {
      TripPhases phases = new TripPhases(deathLoopMillis, maxReconnects);
      phases.phase = snapshot.phase();
      phases.resumeTo = snapshot.resumeTo();
      phases.endReason = snapshot.endReason();
      phases.lastDeathAt = snapshot.lastDeathAt();
      phases.reconnects = snapshot.reconnects();
      phases.parkReason = snapshot.parkReason() == null ? "" : snapshot.parkReason();
      phases.deathsWithoutProgress = snapshot.deathsWithoutProgress();
      phases.reconnectsExhausted = snapshot.reconnectsExhausted();
      return phases;
   }

   public Snapshot snapshot() {
      return new Snapshot(phase, resumeTo, endReason, lastDeathAt, reconnects, parkReason, deathsWithoutProgress, reconnectsExhausted);
   }

   public Phase phase() {
      return phase;
   }

   public Phase resumeTo() {
      return resumeTo;
   }

   public EndReason endReason() {
      return endReason;
   }

   public String parkReason() {
      return parkReason;
   }

   public int reconnects() {
      return reconnects;
   }

   public boolean reconnectAllowed() {
      return phase != Phase.IDLE && phase != Phase.ENDED && !(phase == Phase.PARKED && reconnectsExhausted);
   }

   public Phase on(Event event, long now) {
      switch (event) {
         case START -> {
            if (phase == Phase.IDLE || phase == Phase.ENDED) {
               lastDeathAt = Long.MIN_VALUE;
               reconnects = 0;
               deathsWithoutProgress = 0;
               reconnectsExhausted = false;
               parkReason = "";
               endReason = EndReason.NONE;
               phase = Phase.FLYING;
            }
         }
         case STOP -> {
            if (phase != Phase.IDLE && phase != Phase.ENDED) end(EndReason.STOPPED);
         }
         case RESUME -> {
            if (phase == Phase.PAUSED || phase == Phase.PARKED) {
               parkReason = "";
               if (reconnectsExhausted) {
                  reconnectsExhausted = false;
                  reconnects = 0;
               }
               enter(resumeTo);
            }
         }
         case PAUSE -> {
            if (phase.active() && phase != Phase.RECONNECTING) {
               resumeTo = phase;
               phase = Phase.PAUSED;
            }
         }
         case DISCONNECTED -> {
            if (phase == Phase.IDLE || phase == Phase.ENDED || phase == Phase.PAUSED || phase == Phase.PARKED) break;
            if (phase != Phase.RECONNECTING) resumeTo = phase;
            if (++reconnects > maxReconnects) {
               reconnectsExhausted = true;
               park("Couldn't get back on the server after " + maxReconnects + " reconnects.");
            } else {
               phase = Phase.RECONNECTING;
            }
         }
         case BACK_IN_WORLD -> {
            if (phase == Phase.RECONNECTING) enter(resumeTo);
         }
         case DIED -> {
            if (!phase.active() || phase == Phase.RECONNECTING) break;
            boolean loop = lastDeathAt != Long.MIN_VALUE && now - lastDeathAt < deathLoopMillis;
            lastDeathAt = now;
            deathsWithoutProgress++;
            if (loop) {
               resumeTo = Phase.RESPAWNING;
               park("Died twice within " + deathLoopMillis / 60_000 + " minutes.");
            } else if (deathsWithoutProgress >= DEATHS_WITHOUT_PROGRESS) {
               resumeTo = Phase.RESPAWNING;
               park("Died " + deathsWithoutProgress + " times without setting a new checkpoint; the checkpoint may be somewhere deadly.");
            } else {
               phase = Phase.RESPAWNING;
            }
         }
         case RESPAWNED -> {
            if (phase == Phase.RESPAWNING) phase = Phase.REFILLING;
         }
         case LOW_SUPPLY -> {
            if (phase == Phase.FLYING) phase = Phase.REFILLING;
         }
         case REFILL_DONE -> {
            if (phase == Phase.REFILLING) phase = Phase.RETURNING;
         }
         case RETURNED -> {
            if (phase == Phase.RETURNING) enter(Phase.FLYING);
         }
         case REFILL_SHORT -> {
            if (phase == Phase.REFILLING || phase == Phase.RETURNING) end(EndReason.STATION_SHORT);
         }
         case REFILL_FAILED -> {
            if (phase == Phase.REFILLING || phase == Phase.RETURNING) {
               resumeTo = phase;
               park("The refill failed.");
            }
         }
         case TELEPORT_GAVE_UP -> {
            if (phase.active()) end(EndReason.TELEPORT_FAILED);
         }
         case PROGRESS -> deathsWithoutProgress = 0;
         case ARRIVED -> {
            if (phase == Phase.FLYING) end(EndReason.ARRIVED);
         }
      }
      return phase;
   }

   /**
    * Parks a trip that was interrupted from outside, such as a game restart, so Resume continues the phase it was in. A
    * trip that was reconnecting resumes to the phase before the disconnect.
    */
   public void suspend(String reason) {
      if (phase.active() && phase != Phase.RECONNECTING) resumeTo = phase;
      park(reason);
   }

   /** Parks with a reason, keeping {@link #resumeTo} so Resume picks up where the trip stopped. */
   public void park(String reason) {
      parkReason = reason;
      phase = Phase.PARKED;
   }

   private void enter(Phase next) {
      phase = next;
      if (next == Phase.FLYING) reconnects = 0;
   }

   private void end(EndReason reason) {
      endReason = reason;
      phase = Phase.ENDED;
   }
}
