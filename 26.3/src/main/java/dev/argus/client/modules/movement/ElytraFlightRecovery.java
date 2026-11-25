package dev.argus.client.modules.movement;

import dev.argus.client.ArgusClient;
import dev.argus.client.utils.movement.ElytraProgressWatch;
import dev.argus.client.utils.movement.ElytraRouteFollower;
import dev.argus.client.utils.movement.ElytraRouteRecovery;
import dev.argus.client.utils.movement.ElytraRouteStall;
import dev.argus.client.utils.movement.ElytraStuckWatch;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps a Baritone elytra flight going, for BaritoneElytra and ElytraTrip: strict flight along Baritone's route, and noticing
 * a flight that is stuck, getting no closer, or whose route Baritone has given up on, and saying what to do about it.
 * Ported from Comet's Baritone Elytra.
 */
public final class ElytraFlightRecovery {
   public enum Action { NONE, REPLAN, REBUILD, UNSTICK }

   /** Blocks per tick a vanilla server takes from one move packet without setting the player back. */
   private static final double SERVER_MOVE_LIMIT = 17.0;
   /** Further from the destination than this, getting no closer is a problem; nearer, Baritone is landing. */
   public static final int NEAR = 128;
   /** Ticks of Baritone's own unpowered glide when everything else has failed, to get somewhere new to start from. */
   private static final int COAST_TICKS = 40;
   /** Baritone failing to recompute its route this many times in a row asks for a new one. */
   private static final int RECOMPUTE_FAILURES = 20;
   private static final int RECOMPUTE_REPLAN_TICKS = 100;
   private static final double SWEEP_STEP = 0.5;
   /** Ticks without another failed path after which tight spaces go back off, so open flight keeps its wide margins. */
   private static final int TIGHT_TICKS = 600;

   private final String name;
   private final ElytraStuckWatch stuck = new ElytraStuckWatch();
   private final ElytraProgressWatch progress = new ElytraProgressWatch();
   private final ElytraRouteRecovery route = new ElytraRouteRecovery();
   private final ElytraRouteStall stall = new ElytraRouteStall();
   private final ElytraRouteFollower follower = new ElytraRouteFollower();
   private volatile boolean routeEnded;
   private volatile boolean emergency;
   private volatile int recomputeFailures;
   /** Set from Baritone's thread when it found no path at all from here. */
   private volatile boolean pathFailed;
   private boolean tight;
   private int tightUntil;
   /** Baritone found no path again with tight spaces already on: time to run somewhere else. */
   private boolean escapeDue;
   private int nextRecomputeReplan;
   private boolean stallReplanDue;
   private int coastUntil;
   private boolean segmentFailureSeen;
   private boolean landingFailureSeen;
   private boolean recomputeSeen;
   private boolean warned;

   public ElytraFlightRecovery(String name) {
      this.name = name;
   }

   /** A new trip. */
   public void reset() {
      this.stuck.reset();
      this.progress.reset();
      this.route.reset();
      this.stall.reset();
      this.follower.reset();
      this.routeEnded = this.emergency = false;
      this.recomputeFailures = 0;
      this.pathFailed = this.tight = this.escapeDue = false;
      this.tightUntil = 0;
      this.stallReplanDue = false;
      this.coastUntil = 0;
      this.segmentFailureSeen = this.landingFailureSeen = this.recomputeSeen = false;
      this.warned = false;
   }

   /** A new route for the same trip, after a re-plan. What was said about the old route no longer counts. */
   public void routeStarted() {
      this.stuck.restart();
      this.routeReplaced();
   }

   /**
    * A new route asked for because the old one ran through a block. The stuck watch carries on: these come every few
    * seconds when Baritone's terrain is wrong here, and restarting it each time would mean never walking out.
    */
   public void routeReplaced() {
      this.route.routeStarted();
      this.follower.reset();
      this.routeEnded = false;
      this.stallReplanDue = false;
      this.segmentFailureSeen = this.landingFailureSeen = false;
   }

   /**
    * A message Baritone wrote to chat during this flight. Its route ending and its failures read the same in every build.
    * Returns true to hide one Baritone repeats every tick.
    */
   public boolean onBaritoneMessage(String message) {
      if (message.contains("Emergency landing")) {
         this.emergency = true;
      } else if (message.contains("Path complete") || message.contains("Above the landing spot")) {
         this.routeEnded = true;
      } else if (message.contains("Failed to compute path to destination")) {
         this.pathFailed = true;
      } else if (message.contains("Failed to compute next segment")) {
         boolean seen = this.segmentFailureSeen;
         this.segmentFailureSeen = true;
         return seen;
      } else if (message.contains("unable to land at")) {
         boolean seen = this.landingFailureSeen;
         this.landingFailureSeen = true;
         return seen;
      } else if (message.contains("Failed to recompute segment")) {
         this.recomputeFailures++;
         boolean seen = this.recomputeSeen;
         this.recomputeSeen = true;
         return seen;
      }
      return false;
   }

   /**
    * Once per tick of a flight Baritone has control of. {@code landing} is Baritone's own landing, which is left alone, and
    * {@code usableElytra} says whether there are wings left to carry on with.
    */
   public Action tick(LocalPlayer player, BlockPos destination, double remaining, boolean landing, boolean usableElytra) {
      int tick = player.tickCount;
      if (this.emergency) {
         this.emergency = false;
         this.route.emergencyLanding(tick);
      }
      if (this.routeEnded) {
         this.routeEnded = false;
         this.route.routeEnded();
      }
      switch (this.route.tick(tick, player.getX(), player.getZ(), remaining, NEAR, usableElytra)) {
         case REPLAN -> {
            return this.log(Action.REPLAN, player, "Baritone ran out of route " + (int)remaining + " blocks short");
         }
         case REBUILD -> {
            return this.log(Action.REBUILD, player, "Baritone ran out of route again " + (int)remaining + " blocks short");
         }
         case NONE -> {
         }
      }
      if (landing || this.coasting(tick)) {
         this.stuck.restart();
         return Action.NONE;
      }
      if (this.stallReplanDue) {
         this.stallReplanDue = false;
         return this.log(Action.REPLAN, player, "strict flight blocked on the route");
      }
      if (this.recomputeFailures >= RECOMPUTE_FAILURES && tick >= this.nextRecomputeReplan) {
         this.recomputeFailures = 0;
         this.recomputeSeen = false;
         this.nextRecomputeReplan = tick + RECOMPUTE_REPLAN_TICKS;
         return this.log(Action.REPLAN, player, "Baritone can't recompute its route");
      }
      switch (this.stuck.tick(player.getX(), player.getY(), player.getZ())) {
         case REPLAN -> {
            return this.log(Action.REPLAN, player, "not moving");
         }
         case WALK -> {
            return this.log(Action.UNSTICK, player, "still not moving after new routes");
         }
         case NONE -> {
         }
      }
      if (remaining >= NEAR) {
         return switch (this.progress.tick(remaining, destination.getX(), destination.getZ())) {
            case REPLAN -> this.log(Action.REPLAN, player, "no closer to the destination in a minute");
            case REBUILD -> this.log(Action.REBUILD, player, "no closer to the destination in two minutes");
            case UNSTICK -> this.log(Action.UNSTICK, player, "no closer to the destination in three minutes");
            case NONE -> Action.NONE;
         };
      }
      return Action.NONE;
   }

   /**
    * Whether Baritone should be allowed tight spaces now, called once per flight tick. Baritone's pathfinder wants a gap four
    * blocks wide by default, so in a congested area it can find no path at all. After that, tight spaces are on until a while
    * passes without another such failure.
    */
   public boolean tightSpaces(LocalPlayer player) {
      int tick = player.tickCount;
      if (this.pathFailed) {
         this.pathFailed = false;
         if (!this.tight) {
            ArgusClient.LOGGER.info("{}: Baritone found no path from {}; allowing tight spaces", this.name, player.blockPosition());
         } else {
            ArgusClient.LOGGER.info("{}: Baritone found no path from {} even in tight spaces", this.name, player.blockPosition());
            this.escapeDue = true;
         }
         this.tight = true;
         this.tightUntil = tick + TIGHT_TICKS;
      } else if (this.tight && tick >= this.tightUntil && this.tightUntil - tick <= TIGHT_TICKS) {
         this.tight = false;
         ArgusClient.LOGGER.info("{}: no failed paths for a while at {}; tight spaces off again", this.name, player.blockPosition());
      }
      return this.tight;
   }

   /** True once, after Baritone found no path even with tight spaces on, so the caller runs somewhere else. */
   public boolean takeEscapeDue() {
      boolean due = this.escapeDue;
      this.escapeDue = false;
      return due;
   }

   private Action log(Action action, LocalPlayer player, String reason) {
      ArgusClient.LOGGER.info("{}: {} at {}; {}", this.name, reason, player.blockPosition(), action);
      return action;
   }

   /** Says one thing about recovering once per trip, so chat isn't filled. True the first time. */
   public boolean firstWarning() {
      boolean first = !this.warned;
      this.warned = true;
      return first;
   }

   /** Lets Baritone glide unpowered for a moment, which takes the player somewhere new to plan from. */
   public void coast(int tick) {
      this.coastUntil = tick + COAST_TICKS;
   }

   /** True while Baritone is gliding by itself after {@link #coast}. */
   public boolean coasting(int tick) {
      return tick < this.coastUntil && this.coastUntil - tick <= COAST_TICKS;
   }

   /**
    * Strict flight: this tick's move along Baritone's route, keeping the body within {@code tolerance} blocks of it and
    * stopping at a corner rather than cutting through what the route goes round. Null when there's no route to follow,
    * so the caller flies by heading instead.
    */
   public Vec3 strictMove(LocalPlayer player, List<? extends BlockPos> path, double speedBps, double tolerance, boolean avoidLava) {
      AABB body = player.getBoundingBox();
      Vec3 centre = body.getCenter();
      if (!this.follower.locate(centre.x, centre.y, centre.z, path)) {
         return null;
      }
      double budget = Math.min(SERVER_MOVE_LIMIT, speedBps / 20.0);
      Level level = player.level();
      ElytraRouteFollower.Motion move = this.follower.next(centre.x, centre.y, centre.z, budget, tolerance);
      boolean blocked = !clear(player, level, body, move, avoidLava);
      // Cutting a corner is only ever an extra.
      if (blocked && tolerance > ElytraRouteFollower.EXACT) {
         move = this.follower.next(centre.x, centre.y, centre.z, budget, ElytraRouteFollower.EXACT);
         blocked = !clear(player, level, body, move, avoidLava);
      }
      for (int attempt = 0; blocked && this.follower.offRoute() > ElytraRouteFollower.EXACT; attempt++) {
         ElytraRouteFollower.Motion other = this.follower.rejoin(centre.x, centre.y, centre.z, budget, attempt);
         if (other == null) {
            break;
         }
         if (clear(player, level, body, other, avoidLava)) {
            move = other;
            blocked = false;
         }
      }
      if (this.stall.tick(blocked, move.length() > 0.05)) {
         this.stallReplanDue = true;
      }
      // Hold still rather than push into the block; the stall asks for a new route.
      return blocked ? Vec3.ZERO : new Vec3(move.x(), move.y(), move.z());
   }

   /** Whether the body can make this move through loaded, open space, and out of lava when {@code avoidLava}. */
   private static boolean clear(LocalPlayer player, Level level, AABB body, ElytraRouteFollower.Motion move, boolean avoidLava) {
      double length = move.length();
      int steps = Math.max(1, (int)Math.ceil(length / SWEEP_STEP));
      for (int i = 1; i <= steps; i++) {
         double t = (double)i / steps;
         AABB moved = body.move(move.x() * t, move.y() * t, move.z() * t);
         if (!level.hasChunkAt(BlockPos.containing(moved.getCenter())) || !ElytraRouteCheck.fits(player, body, moved)
            || avoidLava && ElytraRouteCheck.touchesLava(level, moved)) {
            return false;
         }
      }
      return true;
   }

   /** How far the body is from the route, for the status line. */
   public double offRoute() {
      return this.follower.offRoute();
   }
}
