package dev.argus.client.modules.render.targets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CellIndexTest {
   @Test
   void packRoundTripsNegativeAndLargeCoordinates() {
      int[][] positions = {{0, 0, 0}, {-1, -64, -1}, {29_999_999, 319, -29_999_999}, {-30_000_000, -2048, 12345}, {7, 2047, -8}};
      for (int[] p : positions) {
         long packed = CellIndex.pack(p[0], p[1], p[2]);
         assertEquals(p[0], CellIndex.unpackX(packed));
         assertEquals(p[1], CellIndex.unpackY(packed));
         assertEquals(p[2], CellIndex.unpackZ(packed));
      }
   }

   @Test
   void equalValuesDoNotDirtyOrBumpRevision() {
      CellIndex<String> index = new CellIndex<>();
      long pos = CellIndex.pack(3, 70, -5);
      assertTrue(index.put(pos, "chest"));
      CellIndex.CellKey key = index.pollDirty();
      assertEquals(CellIndex.CellKey.ofBlock(3, 70, -5), key);
      long revision = index.cell(key).revision();

      assertFalse(index.put(pos, "chest"));
      assertNull(index.pollDirty());
      assertEquals(revision, index.cell(key).revision());

      index.put(pos, "open chest");
      assertEquals(key, index.pollDirty());
      assertTrue(index.cell(key).revision() > revision);
      assertEquals(1, index.size());
   }

   @Test
   void cellsAreEightCubedAndNegativeCoordinatesFloor() {
      assertEquals(new CellIndex.CellKey(0, 0, 0), CellIndex.CellKey.ofBlock(7, 7, 7));
      assertEquals(new CellIndex.CellKey(1, 0, 0), CellIndex.CellKey.ofBlock(8, 0, 0));
      assertEquals(new CellIndex.CellKey(-1, -1, -1), CellIndex.CellKey.ofBlock(-1, -1, -8));
      assertEquals(-8, CellIndex.CellKey.ofBlock(-8, 0, 0).minX());
   }

   @Test
   void removeChunkDropsOnlyThatChunkAndQueuesCleanup() {
      CellIndex<String> index = new CellIndex<>();
      index.put(CellIndex.pack(1, 1, 1), "a");
      index.put(CellIndex.pack(15, 100, 15), "b");
      index.put(CellIndex.pack(16, 1, 1), "c");
      while (index.pollDirty() != null) {
      }

      index.removeChunk(0, 0);
      assertEquals(1, index.size());
      assertNull(index.get(CellIndex.pack(1, 1, 1)));
      assertEquals("c", index.get(CellIndex.pack(16, 1, 1)));
      int dirty = 0;
      while (index.pollDirty() != null) {
         dirty++;
      }

      assertEquals(2, dirty);
   }

   @Test
   void removeKeepsEmptyCellUntilRebuilt() {
      CellIndex<String> index = new CellIndex<>();
      long pos = CellIndex.pack(-20, 5, 40);
      index.put(pos, "x");
      index.pollDirty();
      assertTrue(index.remove(pos));
      assertFalse(index.remove(pos));
      CellIndex.CellKey key = index.pollDirty();
      assertEquals(0, index.cell(key).size());
      assertEquals(0, index.size());
   }
}
