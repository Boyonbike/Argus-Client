package dev.argus.client.utils.movement;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

/** A heading along Baritone's elytra route, worked out directly from its blocks. Ported from Comet's ElytraPathAim. */
public final class ElytraPathAim {
   /** Route nodes to look past the nearest one, far enough that the heading doesn't jitter between neighbours. */
   static final int LOOKAHEAD = 5;
   /** How many nodes past the nearest one Baritone considers aiming at. */
   static final int AHEAD = 20;
   private static final double MIN_DISTANCE_SQUARED = 1.0E-4;

   public record Aim(float yaw, float pitch) {
   }

   private ElytraPathAim() {
   }

   /**
    * Whether this heading points away from all of the route ahead. Baritone picks a node from the next {@link #AHEAD} to aim
    * at, and when it can't see past something that can be the nearest one, behind the player. Heading for any later node at
    * all is just Baritone finding its way around.
    */
   public static boolean turnedBack(double x, double y, double z, float yaw, float pitch, List<? extends BlockPos> path) {
      int nearest = nearest(x, y, z, path);
      if (nearest < 0 || nearest == path.size() - 1) {
         return false;
      }
      double yawRadians = Math.toRadians(yaw);
      double pitchRadians = Math.toRadians(pitch);
      double level = Math.cos(pitchRadians);
      double headingX = -Math.sin(yawRadians) * level;
      double headingY = -Math.sin(pitchRadians);
      double headingZ = Math.cos(yawRadians) * level;
      for (int i = nearest + 1; i <= Math.min(nearest + AHEAD, path.size() - 1); i++) {
         BlockPos node = path.get(i);
         if ((node.getX() + 0.5 - x) * headingX + (node.getY() + 0.5 - y) * headingY + (node.getZ() + 0.5 - z) * headingZ >= 0.0) {
            return false;
         }
      }
      return true;
   }

   /**
    * Where to head along the route: the furthest of the next few nodes that {@code clear} says there's a straight way to.
    * Aiming further than that goes through whatever the route is on its way around. With no clear node it's the next one,
    * which is at least along the route. Null without a route or when already at its end.
    */
   public static Aim aimClear(double x, double y, double z, List<? extends BlockPos> path, Predicate<BlockPos> clear) {
      int nearest = nearest(x, y, z, path);
      if (nearest < 0) {
         return null;
      }
      int last = Math.min(nearest + LOOKAHEAD, path.size() - 1);
      for (int i = last; i > nearest + 1; i--) {
         if (clear.test(path.get(i))) {
            return aimAt(x, y, z, path.get(i));
         }
      }
      return aimAt(x, y, z, path.get(Math.min(nearest + 1, path.size() - 1)));
   }

   static int nearest(double x, double y, double z, List<? extends BlockPos> path) {
      int nearest = -1;
      double best = Double.POSITIVE_INFINITY;
      for (int i = 0; i < path.size(); i++) {
         double distance = distanceSquared(x, y, z, path.get(i));
         if (distance < best) {
            best = distance;
            nearest = i;
         }
      }
      return nearest;
   }

   private static Aim aimAt(double x, double y, double z, BlockPos target) {
      if (distanceSquared(x, y, z, target) < MIN_DISTANCE_SQUARED) {
         return null;
      }
      double dx = target.getX() + 0.5 - x;
      double dy = target.getY() + 0.5 - y;
      double dz = target.getZ() + 0.5 - z;
      return new Aim((float)Math.toDegrees(Math.atan2(-dx, dz)), (float)(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)))));
   }

   private static double distanceSquared(double x, double y, double z, BlockPos pos) {
      double dx = pos.getX() + 0.5 - x;
      double dy = pos.getY() + 0.5 - y;
      double dz = pos.getZ() + 0.5 - z;
      return dx * dx + dy * dy + dz * dz;
   }
}
