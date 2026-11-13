package dev.argus.client.modules.basehunting;

import java.util.ArrayList;
import java.util.List;

/** Pure path maths for Spiral: each path is a list of X/Z corners to fly between, in order. */
public final class AreaPaths {
   private AreaPaths() {
   }

   /** Back-and-forth rows covering the rectangle between two corners, rows {@code gap} blocks apart. */
   public static List<double[]> rectangle(double startX, double startZ, double endX, double endZ, double gap) {
      List<double[]> points = new ArrayList<>();
      double step = Math.max(1.0, gap);
      double direction = endZ >= startZ ? 1.0 : -1.0;
      double rowCount = Math.floor(Math.abs(endZ - startZ) / step);
      boolean outward = true;

      for (int row = 0; row <= rowCount; row++) {
         double z = startZ + direction * row * step;
         if (outward) {
            points.add(new double[]{startX, z});
            points.add(new double[]{endX, z});
         } else {
            points.add(new double[]{endX, z});
            points.add(new double[]{startX, z});
         }

         outward = !outward;
      }

      if (Math.abs(startZ + direction * rowCount * step - endZ) > 0.5) {
         double[] last = points.get(points.size() - 1);
         points.add(new double[]{last[0], endZ});
         points.add(new double[]{last[0] == startX ? endX : startX, endZ});
      }

      return points;
   }

   /** A square spiral outward from the centre: legs of 1, 1, 2, 2, 3, 3... times {@code gap}. */
   public static List<double[]> squareSpiral(double centerX, double centerZ, double gap, int legs) {
      List<double[]> points = new ArrayList<>(legs + 1);
      double x = centerX;
      double z = centerZ;
      points.add(new double[]{x, z});
      // East, south, west, north: Minecraft's +X then +Z.
      int[][] directions = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

      for (int leg = 0; leg < legs; leg++) {
         double length = (leg / 2 + 1) * gap;
         int[] direction = directions[leg % 4];
         x += direction[0] * length;
         z += direction[1] * length;
         points.add(new double[]{x, z});
      }

      return points;
   }

   /** An Archimedean spiral whose rings are {@code gap} apart, sampled every {@code segment} blocks along the curve. */
   public static List<double[]> circleSpiral(double centerX, double centerZ, double gap, double segment, double maxRadius) {
      List<double[]> points = new ArrayList<>();
      double ringFactor = gap / (Math.PI * 2.0);
      double angle = 0.0;
      double step = Math.max(1.0, segment);
      points.add(new double[]{centerX, centerZ});

      while (ringFactor * angle < maxRadius) {
         double radius = Math.max(ringFactor * angle, ringFactor);
         // Advance the angle so the arc between samples stays about one segment long.
         angle += step / radius;
         double r = ringFactor * angle;
         points.add(new double[]{centerX + r * Math.cos(angle), centerZ + r * Math.sin(angle)});
      }

      return points;
   }

   /**
    * An endless lawnmower: legs of {@code legLength} across the heading, each row {@code gap} further along it.
    * Yaw uses Minecraft's convention (0 = south, 90 = west).
    */
   public static List<double[]> zigzag(double startX, double startZ, float yaw, double legLength, double gap, int rows) {
      double radians = Math.toRadians(yaw);
      double forwardX = -Math.sin(radians);
      double forwardZ = Math.cos(radians);
      double sideX = -forwardZ;
      double sideZ = forwardX;
      List<double[]> points = new ArrayList<>(rows * 2 + 1);
      points.add(new double[]{startX, startZ});
      double half = legLength / 2.0;

      for (int row = 0; row < rows; row++) {
         double along = row * gap;
         double side = row % 2 == 0 ? half : -half;
         double baseX = startX + forwardX * along;
         double baseZ = startZ + forwardZ * along;
         points.add(new double[]{baseX + sideX * side, baseZ + sideZ * side});
         double nextAlong = along + gap;
         points.add(new double[]{startX + forwardX * nextAlong + sideX * side, startZ + forwardZ * nextAlong + sideZ * side});
      }

      return points;
   }
}
