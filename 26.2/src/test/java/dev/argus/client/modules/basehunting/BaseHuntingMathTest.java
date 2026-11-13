package dev.argus.client.modules.basehunting;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class BaseHuntingMathTest {
   @Test
   void rectangleRowsCoverBothCornersAndAlternate() {
      List<double[]> points = AreaPaths.rectangle(0, 0, 100, 50, 25);
      assertArrayEquals(new double[]{0, 0}, points.get(0));
      assertArrayEquals(new double[]{100, 0}, points.get(1));
      assertArrayEquals(new double[]{100, 25}, points.get(2));
      assertArrayEquals(new double[]{0, 25}, points.get(3));
      double[] last = points.get(points.size() - 1);
      assertEquals(50, last[1]);
   }

   @Test
   void rectangleReachesTheFarEdgeWhenTheGapDoesNotDivideIt() {
      List<double[]> points = AreaPaths.rectangle(0, 0, 10, 30, 25);
      assertEquals(30, points.get(points.size() - 1)[1]);
   }

   @Test
   void squareSpiralLegsGrowEveryTwoTurns() {
      List<double[]> points = AreaPaths.squareSpiral(0, 0, 10, 4);
      assertArrayEquals(new double[]{10, 0}, points.get(1));
      assertArrayEquals(new double[]{10, 10}, points.get(2));
      assertArrayEquals(new double[]{-10, 10}, points.get(3));
      assertArrayEquals(new double[]{-10, -10}, points.get(4));
   }

   @Test
   void circleSpiralStaysWithinItsRadiusAndKeepsSegmentsShort() {
      List<double[]> points = AreaPaths.circleSpiral(0, 0, 64, 32, 500);
      for (int index = 1; index < points.size(); index++) {
         double[] a = points.get(index - 1);
         double[] b = points.get(index);
         assertTrue(Math.hypot(b[0] - a[0], b[1] - a[1]) < 80, "segment " + index + " too long");
         assertTrue(Math.hypot(b[0], b[1]) < 600);
      }
   }

   @Test
   void zigzagAdvancesAlongTheHeading() {
      // Yaw 0 faces south (+Z).
      List<double[]> points = AreaPaths.zigzag(0, 0, 0.0F, 100, 20, 3);
      double[] lastPoint = points.get(points.size() - 1);
      assertEquals(60, lastPoint[1], 1.0E-6);
      assertEquals(Math.abs(points.get(1)[0]), 50, 1.0E-6);
   }

   @Test
   void bearingsFromTwoPlacesMeetAtTheTarget() {
      // Target at (100, 100). From (0, 0) and (200, 0), aim at it using yaw-style angles.
      double angle1 = Math.atan2(-(100 - 0), 100 - 0);
      double angle2 = Math.atan2(-(100 - 200), 100 - 0);
      double[] point = Triangulation.intersect(0, 0, angle1, 200, 0, angle2);
      assertNotNull(point);
      assertEquals(100, point[0], 1.0E-6);
      assertEquals(100, point[1], 1.0E-6);
   }

   @Test
   void parallelBearingsDoNotTriangulate() {
      assertNull(Triangulation.intersect(0, 0, 0.3, 10, 0, 0.3));
   }

   @Test
   void removedPortalLeavesAFlatRectangleOfAir() {
      Set<Long> air = new HashSet<>();
      // A 4 wide by 5 tall obsidian frame removed along X, corners included.
      for (int x = 0; x < 4; x++) {
         for (int y = 0; y < 5; y++) {
            air.add(key(x, y, 0));
         }
      }

      AirPockets.Lookup lookup = lookup(air);
      List<int[]> pocket = AirPockets.pocket(lookup, 1, 1, 0, 100);
      int[] shape = AirPockets.portalShape(pocket, 23, 23);
      assertNotNull(shape);
      assertArrayEquals(new int[]{0, 0, 0, 3, 4, 0}, shape);
   }

   @Test
   void portalShapeAllowsMissingCornersButNotHoles() {
      Set<Long> air = new HashSet<>();
      for (int z = 0; z < 4; z++) {
         for (int y = 0; y < 5; y++) {
            air.add(key(0, y, z));
         }
      }

      air.remove(key(0, 0, 0));
      air.remove(key(0, 4, 3));
      assertNotNull(AirPockets.portalShape(AirPockets.pocket(lookup(air), 0, 2, 2, 100), 23, 23));
      air.remove(key(0, 2, 1));
      assertNull(AirPockets.portalShape(AirPockets.pocket(lookup(air), 0, 2, 2, 100), 23, 23));
   }

   @Test
   void thickPocketsAreNotPortals() {
      Set<Long> air = new HashSet<>();
      for (int x = 0; x < 4; x++) {
         for (int y = 0; y < 5; y++) {
            air.add(key(x, y, 0));
            air.add(key(x, y, 1));
         }
      }

      assertNull(AirPockets.portalShape(AirPockets.pocket(lookup(air), 0, 0, 0, 100), 23, 23));
   }

   @Test
   void pocketsBiggerThanTheLimitAreDropped() {
      Set<Long> air = new HashSet<>();
      for (int x = 0; x < 20; x++) {
         air.add(key(x, 0, 0));
      }

      assertNull(AirPockets.pocket(lookup(air), 0, 0, 0, 10));
      assertEquals(20, AirPockets.pocket(lookup(air), 0, 0, 0, 50).size());
   }

   @Test
   void plainAirNextToCaveAirIsNoticed() {
      AirPockets.Lookup lookup = (x, y, z) -> x == 1 ? AirPockets.Kind.CAVE_AIR : x == 0 && y == 0 && z == 0 ? AirPockets.Kind.AIR : AirPockets.Kind.OTHER;
      assertTrue(AirPockets.touchesCaveAir(lookup, 0, 0, 0));
   }

   @Test
   void plannerTurnsOntoAnEastboundTrail() {
      // Trail along chunk row z = 0, running east; we start heading a little south of east.
      TrailPlanner.TrailMap map = (chunkX, chunkZ) -> chunkZ == 0 && chunkX >= 0;
      TrailPlanner.Choice choice = TrailPlanner.choose(map, 8, 8, -70.0F, 60.0F, 12, 0.5);
      assertEquals(-90.0F, choice.yaw(), 10.0F);
      assertTrue(choice.score() > 3.0);
   }

   @Test
   void plannerScoresNothingWithoutATrail() {
      TrailPlanner.Choice choice = TrailPlanner.choose((chunkX, chunkZ) -> false, 0, 0, 0.0F, 45.0F, 10, 0.5);
      assertTrue(choice.score() <= 0.0);
   }

   @Test
   void approachTurnsTheShortWayRound() {
      assertEquals(-175.0F, TrailPlanner.approach(175.0F, -170.0F, 10.0F), 1.0E-4);
      assertEquals(15.0F, TrailPlanner.approach(10.0F, 90.0F, 5.0F), 1.0E-4);
   }

   @Test
   void rememberedFindsSurviveARoundTripIncludingCommasInTheirName() {
      net.minecraft.core.BlockPos key = new net.minecraft.core.BlockPos(-120, 64, 3000);
      HuntMarkers.Mark mark = new HuntMarkers.Mark(new net.minecraft.world.phys.AABB(-121, 63.5, 2999, -119, 65, 3001), "a stash: 5 chests, 2 barrels");
      String line = MarkCodec.encode(key, mark);
      MarkCodec.Entry entry = MarkCodec.decode(line);
      assertNotNull(entry);
      assertEquals(key, entry.key());
      assertEquals(mark, entry.mark());
      assertEquals("-120,64,3000", MarkCodec.keyOf(line));
      assertNull(MarkCodec.decode("not,a,find"));
   }

   @Test
   void compassPointsWhereMinecraftDoes() {
      assertEquals("N", HuntMarkers.compass(0, -10));
      assertEquals("E", HuntMarkers.compass(10, 0));
      assertEquals("S", HuntMarkers.compass(0, 10));
      assertEquals("W", HuntMarkers.compass(-10, 0));
      assertEquals("SE", HuntMarkers.compass(10, 10));
   }

   @Test
   void portalShapeHonoursMinimumSizeAndCornerChoice() {
      List<int[]> withoutCorners = new java.util.ArrayList<>();
      for (int x = 0; x < 4; x++) {
         for (int y = 0; y < 5; y++) {
            boolean corner = (x == 0 || x == 3) && (y == 0 || y == 4);
            if (!corner) {
               withoutCorners.add(new int[]{x, y, 0});
            }
         }
      }

      assertNotNull(AirPockets.portalShape(withoutCorners, 2, 3, 8, 8, true));
      assertNull(AirPockets.portalShape(withoutCorners, 2, 3, 8, 8, false));
      assertNull(AirPockets.portalShape(withoutCorners, 5, 3, 8, 8, true));
   }

   @Test
   void smoothSpiralStartsHereFacingTheSameWayAndWidensByOneGapPerTurn() {
      for (float yaw : new float[]{0.0F, 90.0F, -135.0F, 33.0F}) {
         for (boolean right : new boolean[]{true, false}) {
            SmoothSpiral spiral = SmoothSpiral.startingAt(100.0, -50.0, yaw, 128.0, right);
            double[] start = spiral.point(SmoothSpiral.START_THETA);
            assertEquals(100.0, start[0], 1.0E-6);
            assertEquals(-50.0, start[1], 1.0E-6);
            // A point just ahead lies the way the player faces.
            float heading = spiral.yawTowards(100.0, -50.0, spiral.ahead(SmoothSpiral.START_THETA, 0.5));
            assertEquals(0.0, Math.abs(TrailPlanner.wrap(heading - yaw)), 0.5);
            double inner = spiral.radius(10.0);
            assertEquals(128.0, spiral.radius(10.0 + 2.0 * Math.PI) - inner, 1.0E-6);
         }
      }
   }

   @Test
   void smoothSpiralTracksTheSameRingWhenBlownOffCourse() {
      SmoothSpiral spiral = SmoothSpiral.startingAt(0.0, 0.0, 0.0F, 64.0, true);
      double theta = 20.0;
      double[] onPath = spiral.point(theta);
      // 20 blocks off the line is well within one ring, so it stays on this turn.
      double near = spiral.thetaNear(onPath[0] + 20.0, onPath[1], theta);
      assertTrue(Math.abs(near - theta) < Math.PI);
      assertEquals(theta, spiral.thetaNear(onPath[0], onPath[1], -1.0), 1.0E-6);
   }

   @Test
   void rememberedFindsKeepTheirOwnColour() {
      java.awt.Color color = new java.awt.Color(255, 160, 60, 230);
      HuntMarkers.Mark mark = new HuntMarkers.Mark(new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1), "a pot holding 3 Gold", HuntMarkers.fillFor(color), color);
      MarkCodec.Entry entry = MarkCodec.decode(MarkCodec.encode(net.minecraft.core.BlockPos.ZERO, mark));
      assertNotNull(entry);
      assertEquals(mark, entry.mark());
      // Lines saved before colours were added still load.
      assertEquals("old find, with comma", MarkCodec.decode("1,2,3,0,0,0,1,1,1,old find, with comma").mark().what());
   }

   private static AirPockets.Lookup lookup(Set<Long> air) {
      return (x, y, z) -> air.contains(key(x, y, z)) ? AirPockets.Kind.AIR : AirPockets.Kind.CAVE_AIR;
   }

   private static long key(int x, int y, int z) {
      return ((long)x * 31 + y) * 1_000_003L + z;
   }
}
