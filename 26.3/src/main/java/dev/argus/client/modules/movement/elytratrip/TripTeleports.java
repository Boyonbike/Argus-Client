package dev.argus.client.modules.movement.elytratrip;

import dev.argus.client.chat.ChatTeleportState;
import dev.argus.client.chat.ChatTeleportState.Kind;
import dev.argus.client.chat.ChatTeleportState.Phase;
import dev.argus.client.chat.ChatTeleportState.Timer;
import java.util.Map;

/**
 * Follows each /home ElytraTrip sends with its own {@link ChatTeleportState}, the tracker behind the social menu's home
 * cooldowns, and turns its state into what the trip needs to know: still going, done, refused, on cooldown, or given up.
 */
public final class TripTeleports {
   public enum Outcome {
      /** Nothing sent yet. */
      IDLE,
      /** Sent, waiting on the server. */
      PENDING,
      /** The server is counting down to the teleport. */
      WARMUP,
      /** The teleport happened. */
      DONE,
      /** The server refused because /home is on cooldown; send again once {@link #cooldownLeft} is 0. */
      COOLDOWN,
      /** The server refused or never answered. Sending again counts as a retry. */
      FAILED,
      /** FAILED as many times in a row as allowed. */
      GAVE_UP
   }

   /** A refill waits out the cooldown only while the worn elytra outlasts it by this much. */
   public static final long WAIT_MARGIN_MS = 60_000;

   private final ChatTeleportState state = new ChatTeleportState();
   private final int retries;
   private long sentAt = Long.MIN_VALUE;
   private boolean counted;
   private int failures;

   public TripTeleports(int retries) {
      this.retries = retries;
   }

   /** Records a /home; the caller sends the command itself. */
   public void send(String home, long now) {
      state.sent("home " + home, now);
      sentAt = now;
      counted = false;
   }

   public void message(String text, long now) {
      state.message(text, now);
   }

   public void longJump(long now) {
      state.teleported(now);
   }

   public void transferred(long now) {
      state.transferred(now);
   }

   public Outcome poll(long now) {
      if (sentAt == Long.MIN_VALUE) {
         return Outcome.IDLE;
      }
      if (state.completedAt(Kind.Home, now) >= sentAt) {
         failures = 0;
         return Outcome.DONE;
      }
      if (state.pending(Kind.Home, now)) {
         return warmupLeft(now) > 0 ? Outcome.WARMUP : Outcome.PENDING;
      }
      if (cooldownLeft(now) > 0) {
         return Outcome.COOLDOWN;
      }
      if (!counted) {
         counted = true;
         failures++;
      }
      return failures >= retries ? Outcome.GAVE_UP : Outcome.FAILED;
   }

   /** True while a teleport is waiting on the server or counting down. */
   public boolean busy(long now) {
      return sentAt != Long.MIN_VALUE && state.pending(Kind.Home, now);
   }

   public long cooldownLeft(long now) {
      return left(Phase.Cooldown, now);
   }

   public long warmupLeft(long now) {
      return left(Phase.Teleport, now);
   }

   private long left(Phase phase, long now) {
      for (Timer timer : state.timers(now)) {
         if (timer.kind() == Kind.Home && timer.phase() == phase && timer.deadline() > now) {
            return timer.deadline() - now;
         }
      }
      return 0;
   }

   /** Whether a voluntary refill can keep flying through the cooldown: one durability is about a second of gliding. */
   public boolean refillCanWait(int wornElytraDurability, long now) {
      long cooldown = cooldownLeft(now);
      return cooldown > 0 && wornElytraDurability * 1_000L > cooldown + WAIT_MARGIN_MS;
   }

   /** Clears a run of failures, for a fresh refill. */
   public void resetFailures() {
      failures = 0;
   }

   public Map<Kind, Long> learned() {
      return state.learned();
   }

   public void learned(Map<Kind, Long> durations) {
      state.learned(durations);
   }

   /** Called on a new connection to a different server so one server never teaches another its cooldown. */
   public void clear() {
      state.clear();
      sentAt = Long.MIN_VALUE;
      failures = 0;
   }
}
