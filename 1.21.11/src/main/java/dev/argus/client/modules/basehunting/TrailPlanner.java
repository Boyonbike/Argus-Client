package dev.argus.client.modules.basehunting;

/**
 * Picks the heading that keeps you over a chunk trail, for TrailFollower. It looks down a fan of headings around the
 * current one and scores each by how many trail chunks lie along it, near ones counting more, with a small cost for
 * turning so it does not wander between equally good headings. Pure maths so it can be tested.
 */
public final class TrailPlanner {
   private static final double LATERAL_WEIGHT = 0.35;

   private TrailPlanner() {
   }

   @FunctionalInterface
   public interface TrailMap {
      boolean isTrail(int chunkX, int chunkZ);
   }

   public record Choice(float yaw, double score) {
   }

   /**
    * @param yaw current heading, Minecraft style (0 = south, -90 = east)
    * @param cone how far either side of the heading to look, in degrees
    * @param lookahead how many chunks ahead to look
    * @param turnCost score taken off for turning the full cone
    */
   public static Choice choose(TrailMap map, double x, double z, float yaw, float cone, int lookahead, double turnCost) {
      float bestYaw = yaw;
      double bestScore = Double.NEGATIVE_INFINITY;

      for (float offset = -cone; offset <= cone + 0.001F; offset += 5.0F) {
         float candidate = wrap(yaw + offset);
         double score = score(map, x, z, candidate, lookahead) - turnCost * Math.abs(offset) / Math.max(cone, 1.0F);
         if (score > bestScore) {
            bestScore = score;
            bestYaw = candidate;
         }
      }

      return new Choice(bestYaw, bestScore);
   }

   /** How much trail lies along a heading, near chunks weighted more than far ones. */
   public static double score(TrailMap map, double x, double z, float yaw, int lookahead) {
      double radians = Math.toRadians(yaw);
      double dirX = -Math.sin(radians);
      double dirZ = Math.cos(radians);
      double sideX = -dirZ;
      double sideZ = dirX;
      double score = 0.0;

      for (int step = 1; step <= lookahead; step++) {
         double weight = 1.0 / (1.0 + 0.08 * step);
         double sampleX = x + dirX * 16.0 * step;
         double sampleZ = z + dirZ * 16.0 * step;
         if (map.isTrail(chunk(sampleX), chunk(sampleZ))) {
            score += weight;
         }

         if (map.isTrail(chunk(sampleX + sideX * 16.0), chunk(sampleZ + sideZ * 16.0))) {
            score += weight * LATERAL_WEIGHT;
         }

         if (map.isTrail(chunk(sampleX - sideX * 16.0), chunk(sampleZ - sideZ * 16.0))) {
            score += weight * LATERAL_WEIGHT;
         }
      }

      return score;
   }

   /** Turns from one heading toward another by at most {@code maxStep} degrees. */
   public static float approach(float current, float target, float maxStep) {
      float difference = wrap(target - current);
      return wrap(current + Math.max(-maxStep, Math.min(maxStep, difference)));
   }

   public static float wrap(float degrees) {
      float wrapped = degrees % 360.0F;
      if (wrapped >= 180.0F) {
         wrapped -= 360.0F;
      }

      if (wrapped < -180.0F) {
         wrapped += 360.0F;
      }

      return wrapped;
   }

   private static int chunk(double coordinate) {
      return (int)Math.floor(coordinate / 16.0);
   }
}
