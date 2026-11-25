package dev.argus.client.modules.movement;

/** When Baritone Elytra takes a #goto into the air, and what to do once the flight ends. Ported from Comet's ElytraHandoff. */
public final class BaritoneElytraHandoff {
   private BaritoneElytraHandoff() {
   }

   public enum Refusal {
      /** Short enough to walk. */
      SHORT(true),
      /** Baritone ships no native elytra pathfinder for this CPU and operating system. */
      UNSUPPORTED(true),
      NO_ELYTRA(true),
      /** Mid-jump or falling; checked again once on the ground. */
      AIRBORNE(false),
      /** Something above the player; checked again while Baritone walks. */
      COVERED(false);

      /** True when this trip is walked to the end; false when it is checked again while Baritone walks. */
      public final boolean settled;

      Refusal(boolean settled) {
         this.settled = settled;
      }
   }

   public enum End {
      /** Landed near the goal: Baritone walks the last stretch. */
      WALK_REST,
      /** Landed far from the goal, which Baritone does when elytra durability runs low. */
      LANDED_SHORT,
      /** The flight ended without a landing, which is a #stop; the player has control. */
      STOPPED
   }

   /**
    * Null when the trip can be flown now.
    *
    * @param elytraDurability remaining durability of the best elytra available, worn or to equip; -1 when there is none
    */
   public static Refusal check(double distance, int minDistance, boolean supported, int elytraDurability, int minDurability,
                               boolean fallFlying, boolean onGround, boolean clear) {
      if (distance < minDistance) {
         return Refusal.SHORT;
      } else if (!supported) {
         return Refusal.UNSUPPORTED;
      } else if (elytraDurability < 0 || elytraDurability < minDurability) {
         return Refusal.NO_ELYTRA;
      } else if (fallFlying) {
         return null;
      } else if (!onGround) {
         return Refusal.AIRBORNE;
      } else {
         return clear ? null : Refusal.COVERED;
      }
   }

   /**
    * Why Baritone's elytra process went inactive.
    *
    * @param sawLanding whether Baritone reported landing during this flight, which tells a landing from a #stop
    */
   public static End end(boolean fallFlying, boolean sawLanding, double remaining, int minDistance) {
      if (fallFlying || !sawLanding) {
         return End.STOPPED;
      }
      return remaining < minDistance ? End.WALK_REST : End.LANDED_SHORT;
   }

   public static double horizontalDistance(double x, double z, double targetX, double targetZ) {
      return Math.hypot(targetX - x, targetZ - z);
   }

   /** Velocity per tick for a speed in blocks per second along Minecraft's yaw and pitch (negative pitch looks up). */
   public static double[] velocity(double speedBps, float yaw, float pitch) {
      double perTick = speedBps / 20.0;
      double yawRadians = Math.toRadians(yaw);
      double pitchRadians = Math.toRadians(pitch);
      double level = Math.cos(pitchRadians);
      return new double[]{-Math.sin(yawRadians) * level * perTick, -Math.sin(pitchRadians) * perTick, Math.cos(yawRadians) * level * perTick};
   }
}
