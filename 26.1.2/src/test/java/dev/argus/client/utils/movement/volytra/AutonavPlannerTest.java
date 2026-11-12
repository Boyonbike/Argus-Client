package dev.argus.client.utils.movement.volytra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AutonavPlannerTest {
   /** A world of air with a solid wall, loaded within {@code loadedChunks} chunks of the origin. */
   private record Wall(int wallX, int minZ, int maxZ, int minY, int maxY, int loadedChunks) implements AutonavPlanner.Grid {
      boolean solid(int x, int y, int z) {
         return x == this.wallX && z >= this.minZ && z <= this.maxZ && y >= this.minY && y <= this.maxY;
      }

      @Override
      public boolean isChunkUnloaded(int chunkX, int chunkZ) {
         return Math.abs(chunkX) > this.loadedChunks || Math.abs(chunkZ) > this.loadedChunks;
      }

      @Override
      public boolean isPassable(int x, int y, int z, int clearance) {
         if (this.isChunkUnloaded(x >> 4, z >> 4)) return false;
         for (int dx = -clearance; dx <= clearance; dx++) {
            for (int dz = -clearance; dz <= clearance; dz++) {
               if (this.solid(x + dx, y, z + dz)) return false;
            }
         }
         return true;
      }

      @Override
      public boolean lineClear(int x0, int y0, int z0, int x1, int y1, int z1) {
         int steps = Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0))) * 4;
         for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            int x = (int) Math.floor(x0 + 0.5 + (x1 - x0) * t);
            int y = (int) Math.floor(y0 + 0.5 + (y1 - y0) * t);
            int z = (int) Math.floor(z0 + 0.5 + (z1 - z0) * t);
            if (this.solid(x, y, z)) return false;
         }
         return true;
      }

      @Override
      public int escapeCost(int x, int y, int z) {
         if (this.isChunkUnloaded(x >> 4, z >> 4)) return -1;
         return this.solid(x, y, z) ? 12 : 0;
      }

      @Override
      public int minY() {
         return 0;
      }

      @Override
      public int maxY() {
         return 200;
      }
   }

   @Test
   void routesAroundAWallToTheDestination() {
      Wall grid = new Wall(5, -6, 6, 50, 90, 4);
      AutonavPlanner.Result result = AutonavPlanner.planTwoPass(grid, new int[]{0, 70, 0}, 20.5, 0.5, 1.0, 70, 0, 50_000);

      assertEquals(AutonavPlanner.Status.REACHED_DESTINATION, result.status);
      for (int[] cell : result.cells) assertFalse(grid.solid(cell[0], cell[1], cell[2]));
      int[] end = result.cells.getLast();
      assertTrue(Math.hypot(end[0] + 0.5 - 20.5, end[2] + 0.5 - 0.5) <= 1.0);
   }

   @Test
   void headsForTheEdgeOfTheLoadedWorldWhenTheDestinationIsBeyondIt() {
      Wall grid = new Wall(5, -6, 6, 50, 90, 1);
      AutonavPlanner.Result result = AutonavPlanner.planTwoPass(grid, new int[]{0, 70, 0}, 1000.5, 0.5, 30.0, 70, 0, 50_000);

      assertEquals(AutonavPlanner.Status.REACHED_FRONTIER, result.status);
      int[] end = result.cells.getLast();
      assertTrue(AutonavPlanner.isFrontier(grid, end[0], end[2]));
   }

   @Test
   void smoothedRouteOnlyCutsCornersThroughAir() {
      Wall grid = new Wall(5, -6, 6, 50, 90, 4);
      AutonavPlanner.Result result = AutonavPlanner.planTwoPass(grid, new int[]{0, 70, 0}, 20.5, 0.5, 1.0, 70, 0, 50_000);
      List<int[]> smooth = AutonavPlanner.smooth(grid, result.cells, 64);

      assertTrue(smooth.size() < result.cells.size());
      for (int i = 1; i < smooth.size(); i++) {
         int[] a = smooth.get(i - 1);
         int[] b = smooth.get(i);
         assertTrue(grid.lineClear(a[0], a[1], a[2], b[0], b[1], b[2]));
      }
   }

   @Test
   void escapesFromInsideAWall() {
      Wall grid = new Wall(5, -6, 6, 50, 90, 4);
      AutonavPlanner.Result result = AutonavPlanner.escape(grid, new int[]{5, 70, 0}, 16, 30_000);

      assertEquals(AutonavPlanner.Status.ESCAPING, result.status);
      int[] end = result.cells.getLast();
      assertFalse(grid.solid(end[0], end[1], end[2]));
   }
}
