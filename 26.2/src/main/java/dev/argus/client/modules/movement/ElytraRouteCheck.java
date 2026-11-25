package dev.argus.client.modules.movement;

import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Checks Baritone's elytra route against the real world, for BaritoneElytra and ElytraTrip. Baritone can plan through
 * terrain it hasn't packed yet, or terrain it predicted from a seed that isn't this server's, so a route can run through
 * solid blocks, or through lava when lava routing is off. Only loaded chunks are checked.
 */
public final class ElytraRouteCheck {
   /** How far along the route ahead is checked. */
   private static final double LOOKAHEAD = 96.0;
   private static final double STEP = 0.5;
   private static final int CHECK_TICKS = 10;
   /** Least time between new routes asked for this way, so a route Baritone can't fix doesn't replan every check. */
   private static final int REPLAN_TICKS = 100;

   private int nextReplan;

   /** A new flight. */
   public void reset() {
      this.nextReplan = 0;
   }

   /**
    * True when a new route should be asked for: the route ahead goes through a solid block, or lava when {@code avoidLava},
    * and the last such request was long enough ago.
    */
   public boolean replanDue(LocalPlayer player, List<? extends BlockPos> route, boolean avoidLava) {
      int tick = player.tickCount;
      if (tick % CHECK_TICKS != 0 || (tick < this.nextReplan && this.nextReplan - tick <= REPLAN_TICKS)) {
         return false;
      }
      if (!blockedAhead(player, route, avoidLava)) {
         return false;
      }
      this.nextReplan = tick + REPLAN_TICKS;
      return true;
   }

   /** Whether the route ahead, in loaded chunks, passes through a solid block, or lava when {@code avoidLava}. */
   public static boolean blockedAhead(LocalPlayer player, List<? extends BlockPos> route, boolean avoidLava) {
      Level level = player.level();
      return walkAhead(player, route, pos -> {
         BlockState state = level.getBlockState(pos);
         return avoidLava && state.getFluidState().is(FluidTags.LAVA) || !state.getCollisionShape(level, pos).isEmpty();
      });
   }

   /** Whether the route ahead, in loaded chunks, passes through lava. */
   public static boolean lavaAhead(LocalPlayer player, List<? extends BlockPos> route) {
      Level level = player.level();
      return walkAhead(player, route, pos -> level.getFluidState(pos).is(FluidTags.LAVA));
   }

   /**
    * Whether the body can keep this motion for the next few ticks through loaded, open space, and out of lava when
    * {@code avoidLava}. Baritone's heading is a look direction for elytra physics, which dives and pulls up; flown
    * literally under power it can run straight into the ground, so a powered glide checks this first.
    */
   public static boolean clearAhead(LocalPlayer player, Vec3 motion, boolean avoidLava) {
      double length = motion.length();
      if (length < 1.0E-4) {
         return true;
      }
      Level level = player.level();
      Vec3 way = motion.scale(Math.max(2.0, length * 4.0) / length);
      AABB body = player.getBoundingBox();
      int steps = Math.max(1, (int)Math.ceil(way.length() / STEP));
      for (int i = 1; i <= steps; i++) {
         AABB moved = body.move(way.scale((double)i / steps));
         if (!level.hasChunkAt(BlockPos.containing(moved.getCenter())) || !fits(player, body, moved)
            || avoidLava && touchesLava(level, moved)) {
            return false;
         }
      }
      return true;
   }

   /**
    * Whether the body, moved from {@code start} to {@code moved}, runs into nothing it wasn't already in. A body lodged in the
    * edge of a block (a setback, the hitbox growing as the wings close) may still move out of it, as vanilla and the server
    * allow, so that block doesn't count as being in the way of every move.
    */
   public static boolean fits(LocalPlayer player, AABB start, AABB moved) {
      Level level = player.level();
      if (level.noCollision(player, moved)) {
         return true;
      }
      for (VoxelShape shape : level.getBlockCollisions(player, moved)) {
         for (AABB part : shape.toAabbs()) {
            if (part.intersects(moved) && !part.intersects(start)) {
               return false;
            }
         }
      }
      return level.getWorldBorder().isWithinBounds(moved);
   }

   /** Blocks a tick the body comes down at before an escape run: slower than 0.5, so the glide does no fall damage. */
   public static final Vec3 DESCENT = new Vec3(0.0, -0.35, 0.0);
   /** How far down the ground has to be for coming down onto it to count as safe. */
   private static final int SAFE_DROP = 96;

   /** Steps a block of drop is split into to find where the body would come to rest. */
   private static final int LANDING_STEPS = 16;

   /**
    * Whether straight down from here reaches solid ground before any lava, within {@link #SAFE_DROP} blocks of loaded world,
    * with room to stand where it lands, so the body can come down to run somewhere without ending up crawling under a low
    * ceiling. True when already on the ground or in water; never in lava.
    */
   public static boolean safeToLand(LocalPlayer player) {
      if (player.isInLava()) {
         // Coming down in lava only sinks deeper, and there's nowhere to run: the take-off swims out first.
         return false;
      }
      if (player.onGround() || player.isInWater()) {
         return true;
      }
      Level level = player.level();
      AABB body = player.getBoundingBox();
      for (int drop = 1; drop <= SAFE_DROP; drop++) {
         AABB below = body.move(0.0, -drop, 0.0);
         if (!level.hasChunkAt(BlockPos.containing(below.getCenter())) || below.minY < level.getMinY()) {
            return false;
         }
         if (touchesLava(level, below)) {
            return false;
         }
         if (!level.noCollision(player, below)) {
            return roomToStand(player, body, drop);
         }
      }
      return false;
   }

   /** Whether a standing body fits where the falling one comes to rest, somewhere between {@code drop - 1} and {@code drop} down. */
   private static boolean roomToStand(LocalPlayer player, AABB body, int drop) {
      Level level = player.level();
      double rest = drop - 1;
      for (int i = 1; i <= LANDING_STEPS; i++) {
         double down = drop - 1 + (double)i / LANDING_STEPS;
         if (!level.noCollision(player, body.move(0.0, -down, 0.0))) {
            break;
         }
         rest = down;
      }
      double feet = body.minY - rest;
      AABB standing = new AABB(body.minX, feet, body.minZ, body.maxX, feet + player.getDimensions(net.minecraft.world.entity.Pose.STANDING).height(), body.maxZ);
      return level.noCollision(player, standing);
   }

   /** Whether this box holds any lava. */
   public static boolean touchesLava(Level level, AABB box) {
      BlockPos from = BlockPos.containing(box.minX, box.minY, box.minZ);
      BlockPos to = BlockPos.containing(box.maxX - 1.0E-7, box.maxY - 1.0E-7, box.maxZ - 1.0E-7);
      for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
         if (level.getFluidState(pos).is(FluidTags.LAVA)) {
            return true;
         }
      }
      return false;
   }

   private interface Test {
      boolean hit(BlockPos pos);
   }

   /** Walks the lines between route nodes from the one nearest the player; Baritone's nodes can be far apart. */
   private static boolean walkAhead(LocalPlayer player, List<? extends BlockPos> route, Test test) {
      if (route.isEmpty()) {
         return false;
      }
      Level level = player.level();
      int nearest = 0;
      double best = Double.POSITIVE_INFINITY;
      for (int i = 0; i < route.size(); i++) {
         double distance = player.distanceToSqr(Vec3.atCenterOf(route.get(i)));
         if (distance < best) {
            best = distance;
            nearest = i;
         }
      }
      double reach = LOOKAHEAD * LOOKAHEAD;
      // The block the body is lodged in isn't the route's fault, and a new route starts from here just the same.
      AABB body = player.getBoundingBox();
      BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
      for (int i = nearest; i < route.size(); i++) {
         Vec3 from = Vec3.atCenterOf(route.get(i));
         Vec3 to = i + 1 < route.size() ? Vec3.atCenterOf(route.get(i + 1)) : from;
         if (player.distanceToSqr(from) > reach) {
            return false;
         }
         Vec3 way = to.subtract(from);
         int steps = Math.max(1, (int)Math.ceil(way.length() / STEP));
         for (int s = 0; s <= steps; s++) {
            Vec3 at = from.add(way.scale((double)s / steps));
            cursor.set(at.x, at.y, at.z);
            if (!level.hasChunkAt(cursor)) {
               return false;
            }
            if (!body.intersects(new AABB(cursor)) && test.hit(cursor)) {
               return true;
            }
         }
      }
      return false;
   }
}
