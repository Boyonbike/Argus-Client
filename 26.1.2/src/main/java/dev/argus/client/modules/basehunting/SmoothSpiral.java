package dev.argus.client.modules.basehunting;

/**
 * A continuous Archimedean spiral on the X/Z plane, for flying a search without corners. Rings are {@code gap} blocks
 * apart. The spiral is placed so that it starts where you are, already heading the way you face, and curves away to the
 * left or right from there. Angles along the spiral ({@code theta}) grow outwards; yaw follows Minecraft's convention,
 * where a yaw of a faces (-sin a, cos a).
 */
public record SmoothSpiral(double centerX, double centerZ, double b, double rotation, double side, double startTheta) {
   /** Starts half a ring out from the centre, so the first turn is gentle rather than a spin on the spot. */
   static final double START_THETA = Math.PI;

   public static SmoothSpiral startingAt(double x, double z, float yawDegrees, double gap, boolean curveRight) {
      double b = gap / (2.0 * Math.PI);
      double side = curveRight ? 1.0 : -1.0;
      double[] tangent = localTangent(b, side, START_THETA);
      double yaw = Math.toRadians(yawDegrees);
      double facing = Math.atan2(Math.cos(yaw), -Math.sin(yaw));
      double rotation = facing - Math.atan2(tangent[1], tangent[0]);
      double[] offset = rotate(localPoint(b, side, START_THETA), rotation);
      return new SmoothSpiral(x - offset[0], z - offset[1], b, rotation, side, START_THETA);
   }

   private static double[] localPoint(double b, double side, double theta) {
      return new double[]{b * theta * Math.cos(theta), side * b * theta * Math.sin(theta)};
   }

   private static double[] localTangent(double b, double side, double theta) {
      return new double[]{b * (Math.cos(theta) - theta * Math.sin(theta)), side * b * (Math.sin(theta) + theta * Math.cos(theta))};
   }

   private static double[] rotate(double[] vector, double angle) {
      double cos = Math.cos(angle);
      double sin = Math.sin(angle);
      return new double[]{vector[0] * cos - vector[1] * sin, vector[0] * sin + vector[1] * cos};
   }

   /** The world X/Z of the spiral at {@code theta}. */
   public double[] point(double theta) {
      double[] offset = rotate(localPoint(this.b, this.side, theta), this.rotation);
      return new double[]{this.centerX + offset[0], this.centerZ + offset[1]};
   }

   /** How far apart the rings are. */
   public double gap() {
      return this.b * 2.0 * Math.PI;
   }

   public double radius(double theta) {
      return this.b * theta;
   }

   /** Blocks travelled along the spiral per radian at {@code theta}. */
   public double speedAt(double theta) {
      return this.b * Math.sqrt(1.0 + theta * theta);
   }

   /**
    * The spiral angle of the ring point nearest to (x, z) on the same turn as {@code near}, so being blown off course
    * never jumps you a whole ring in or out. Pass a negative {@code near} to pick whichever ring is closest.
    */
   public double thetaNear(double x, double z, double near) {
      double[] local = rotate(new double[]{x - this.centerX, z - this.centerZ}, -this.rotation);
      double angle = Math.atan2(this.side * local[1], local[0]);
      double radius = Math.hypot(local[0], local[1]);
      double best = Double.NaN;
      double bestScore = Double.MAX_VALUE;
      double guess = near >= 0.0 ? near : radius / this.b;
      long turn = Math.round((guess - angle) / (2.0 * Math.PI));
      for (long k = turn - 1; k <= turn + 1; k++) {
         double theta = angle + 2.0 * Math.PI * k;
         if (theta < this.startTheta - 0.5) {
            continue;
         }

         double score = near >= 0.0 ? Math.abs(theta - near) : Math.abs(this.b * theta - radius);
         if (score < bestScore) {
            bestScore = score;
            best = theta;
         }
      }

      return Double.isNaN(best) ? this.startTheta : Math.max(this.startTheta, best);
   }

   /** The spiral angle {@code distance} blocks further along from {@code theta}. */
   public double ahead(double theta, double distance) {
      return theta + distance / Math.max(1.0E-6, this.speedAt(theta));
   }

   /** The yaw that faces from (x, z) towards the spiral point at {@code theta}. */
   public float yawTowards(double x, double z, double theta) {
      double[] target = this.point(theta);
      return (float)Math.toDegrees(Math.atan2(-(target[0] - x), target[1] - z));
   }
}
