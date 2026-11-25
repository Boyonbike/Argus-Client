package dev.argus.client.utils.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** Ported from Comet's ElytraPathAimTest. */
class ElytraPathAimTest {
   private static List<BlockPos> line(int dx, int dy, int dz, int length) {
      List<BlockPos> path = new ArrayList<>();
      for (int i = 0; i < length; i++) {
         path.add(new BlockPos(dx * i, 100 + dy * i, dz * i));
      }
      return path;
   }

   @Test
   void headsAlongTheRouteUsingMinecraftsAngles() {
      ElytraPathAim.Aim south = ElytraPathAim.aimClear(0.5, 100.5, 0.5, line(0, 0, 1, 20), node -> true);
      assertEquals(0, south.yaw(), 1e-4);
      assertEquals(0, south.pitch(), 1e-4);
      assertEquals(90, ElytraPathAim.aimClear(0.5, 100.5, 0.5, line(-1, 0, 0, 20), node -> true).yaw(), 1e-4);
      // Negative pitch looks up.
      assertEquals(-45, ElytraPathAim.aimClear(0.5, 100.5, 0.5, line(0, 1, 1, 20), node -> true).pitch(), 1e-4);
      assertEquals(45, ElytraPathAim.aimClear(0.5, 100.5, 0.5, line(0, -1, 1, 20), node -> true).pitch(), 1e-4);
   }

   @Test
   void looksAheadOfTheNearestNodeAndStopsAtTheEnd() {
      List<BlockPos> path = line(0, 0, 1, 20);
      assertEquals(0, ElytraPathAim.aimClear(0.5, 100.5, 10.5, path, node -> true).yaw(), 1e-4);
      assertNull(ElytraPathAim.aimClear(0.5, 100.5, 19.5, path, node -> true));
   }

   @Test
   void offTheRouteItSteersBackTowardsIt() {
      ElytraPathAim.Aim aim = ElytraPathAim.aimClear(5.5, 100.5, 10.5, line(0, 0, 1, 40), node -> true);
      // West of the player and further along: yaw between straight south and straight west.
      assertTrue(aim.yaw() > 0 && aim.yaw() < 90, "yaw " + aim.yaw());
   }

   @Test
   void aimsAsFarAheadAsThereIsAClearWay() {
      List<BlockPos> path = List.of(new BlockPos(0, 100, 0), new BlockPos(0, 100, 16), new BlockPos(16, 100, 16),
         new BlockPos(32, 100, 16), new BlockPos(32, 100, 32));
      BlockPos wanted = path.get(2);
      ElytraPathAim.Aim aim = ElytraPathAim.aimClear(0.5, 100.5, 0.5, path, node -> node.equals(wanted));
      assertEquals(-45, aim.yaw(), 1e-4);
   }

   @Test
   void withNoClearWayItAimsAtTheNextNodeRatherThanThroughTheNextWall() {
      ElytraPathAim.Aim aim = ElytraPathAim.aimClear(0.5, 100.5, 0.5, line(0, 0, 16, 10), node -> false);
      assertEquals(0, aim.yaw(), 1e-4);
      assertNull(ElytraPathAim.aimClear(0.5, 100.5, 0.5, List.of(), node -> true));
   }

   @Test
   void aHeadingForAnyNodeAheadHasNotTurnedBack() {
      List<BlockPos> path = line(0, 0, 16, 40);
      assertFalse(ElytraPathAim.turnedBack(0.5, 100.5, 8.5, 0, 0, path), "straight along it");
      assertFalse(ElytraPathAim.turnedBack(0.5, 100.5, 8.5, 80, 0, path), "off to one side, but the nodes ahead are still that way");
   }

   @Test
   void aHeadingAwayFromAllOfTheRouteAheadHasTurnedBack() {
      List<BlockPos> path = line(0, 0, 16, 40);
      assertTrue(ElytraPathAim.turnedBack(0.5, 100.5, 8.5, 180, 0, path));
      assertTrue(ElytraPathAim.turnedBack(0.5, 100.5, 8.5, 100, 0, path));
   }

   /** Open air, nodes 16 apart, and the route's next turn is 80 blocks ahead of a heading going around something. */
   @Test
   void goingAroundSomethingTowardsAnEarlierNodeHasNotTurnedBack() {
      List<BlockPos> path = List.of(new BlockPos(-2, 100, -1), new BlockPos(-12, 100, 1), new BlockPos(-12, 100, -15),
         new BlockPos(-12, 100, -31), new BlockPos(4, 100, -31), new BlockPos(20, 100, -31), new BlockPos(36, 100, -31));
      assertFalse(ElytraPathAim.turnedBack(0.5, 100.5, 0.5, 90, 0, path));
   }

   @Test
   void theEndOfARouteIsNeverTurningBack() {
      assertFalse(ElytraPathAim.turnedBack(0.5, 100.5, 0.5, 180, 0, List.of()));
      assertFalse(ElytraPathAim.turnedBack(0.5, 100.5, 40.5, 180, 0, line(0, 0, 1, 40)));
   }
}
