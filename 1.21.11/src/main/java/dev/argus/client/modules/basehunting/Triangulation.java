package dev.argus.client.modules.basehunting;

/** Where two bearings cross, for WaypointCoords. */
public final class Triangulation {
   private Triangulation() {
   }

   /**
    * Intersects two rays on the X/Z plane. Angles are the locator bar's azimuth in radians, where the direction is
    * (-sin a, cos a), the same convention Minecraft uses for yaw.
    *
    * @return {x, z}, or null when the rays are parallel or meet behind the first observer
    */
   public static double[] intersect(double x1, double z1, double angle1, double x2, double z2, double angle2) {
      double dx1 = -Math.sin(angle1);
      double dz1 = Math.cos(angle1);
      double dx2 = -Math.sin(angle2);
      double dz2 = Math.cos(angle2);
      double denominator = dx1 * dz2 - dz1 * dx2;
      if (Math.abs(denominator) < 1.0E-8) {
         return null;
      }

      double t = ((x2 - x1) * dz2 - (z2 - z1) * dx2) / denominator;
      if (t < 0.0) {
         return null;
      }

      return new double[]{x1 + t * dx1, z1 + t * dz1};
   }
}
