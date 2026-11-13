package dev.argus.client.modules.basehunting;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Since 1.13 caves are carved with cave_air, so plain air underground usually means something was mined or removed.
 * This finds connected pockets of plain air and judges their shape, without touching the game so it can be tested.
 */
public final class AirPockets {
   private static final int[][] NEIGHBOURS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

   private AirPockets() {
   }

   public enum Kind {
      AIR,
      CAVE_AIR,
      OTHER
   }

   @FunctionalInterface
   public interface Lookup {
      Kind kind(int x, int y, int z);
   }

   /** Whether a plain-air block borders cave air, which is what makes it worth a closer look. */
   public static boolean touchesCaveAir(Lookup lookup, int x, int y, int z) {
      for (int[] offset : NEIGHBOURS) {
         if (lookup.kind(x + offset[0], y + offset[1], z + offset[2]) == Kind.CAVE_AIR) {
            return true;
         }
      }

      return false;
   }

   /**
    * The plain-air blocks connected to a start block, as {x, y, z} triples.
    *
    * @return the pocket, or null when it grows past {@code limit} blocks (a mined-out area, not a small disturbance)
    */
   public static List<int[]> pocket(Lookup lookup, int x, int y, int z, int limit) {
      return pocket(lookup, x, y, z, limit, new LongOpenHashSet());
   }

   /**
    * As {@link #pocket(Lookup, int, int, int, int)}, also adding every block it walked to {@code walked} (in
    * {@link #pack} form) so a caller can skip them, even when the pocket was too big.
    */
   public static List<int[]> pocket(Lookup lookup, int x, int y, int z, int limit, LongOpenHashSet walked) {
      LongOpenHashSet seen = walked;
      LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
      List<int[]> blocks = new ArrayList<>();
      long start = pack(x, y, z);
      seen.add(start);
      queue.enqueue(start);

      while (!queue.isEmpty()) {
         long packed = queue.dequeueLong();
         int bx = unpackX(packed);
         int by = unpackY(packed);
         int bz = unpackZ(packed);
         blocks.add(new int[]{bx, by, bz});
         if (blocks.size() > limit) {
            return null;
         }

         for (int[] offset : NEIGHBOURS) {
            int nx = bx + offset[0];
            int ny = by + offset[1];
            int nz = bz + offset[2];
            long next = pack(nx, ny, nz);
            if (!seen.contains(next) && lookup.kind(nx, ny, nz) == Kind.AIR) {
               seen.add(next);
               queue.enqueue(next);
            }
         }
      }

      return blocks;
   }

   /**
    * Whether a pocket has the outline of a removed Nether portal: one block thick, flat along X or Z, and filling a
    * rectangle at least 2 wide and 3 tall (the inside of the smallest frame) up to the given size. The four corners may
    * be missing, since frames are often built without them.
    *
    * @return {minX, minY, minZ, maxX, maxY, maxZ} of the pocket, or null
    */
   public static int[] portalShape(List<int[]> pocket, int maxWidth, int maxHeight) {
      return portalShape(pocket, 2, 3, maxWidth, maxHeight, true);
   }

   /** As above with the smallest size and whether missing corners are allowed chosen by the caller. */
   public static int[] portalShape(List<int[]> pocket, int minWidth, int minHeight, int maxWidth, int maxHeight, boolean allowMissingCorners) {
      if (pocket == null || pocket.size() < 6) {
         return null;
      }

      int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
      for (int[] block : pocket) {
         minX = Math.min(minX, block[0]);
         minY = Math.min(minY, block[1]);
         minZ = Math.min(minZ, block[2]);
         maxX = Math.max(maxX, block[0]);
         maxY = Math.max(maxY, block[1]);
         maxZ = Math.max(maxZ, block[2]);
      }

      boolean flatX = minX == maxX;
      boolean flatZ = minZ == maxZ;
      if (flatX == flatZ) {
         return null;
      }

      int width = flatX ? maxZ - minZ + 1 : maxX - minX + 1;
      int height = maxY - minY + 1;
      if (width < minWidth || height < minHeight || width > maxWidth || height > maxHeight) {
         return null;
      }

      int area = width * height;
      int missing = area - pocket.size();
      if (missing > (allowMissingCorners ? 4 : 0)) {
         return null;
      }

      // Anything missing must be a corner, or the pocket is not a clean rectangle.
      if (missing > 0) {
         LongOpenHashSet present = new LongOpenHashSet();
         for (int[] block : pocket) {
            present.add(pack(block[0], block[1], block[2]));
         }

         int across = flatX ? minZ : minX;
         int acrossMax = flatX ? maxZ : maxX;
         for (int a = across; a <= acrossMax; a++) {
            for (int b = minY; b <= maxY; b++) {
               boolean corner = (a == across || a == acrossMax) && (b == minY || b == maxY);
               long key = flatX ? pack(minX, b, a) : pack(a, b, minZ);
               if (!corner && !present.contains(key)) {
                  return null;
               }
            }
         }
      }

      return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
   }

   public static long pack(int x, int y, int z) {
      return ((long)(x & 0x3FFFFFF) << 38) | ((long)(z & 0x3FFFFFF) << 12) | (y & 0xFFF);
   }

   private static int unpackX(long packed) {
      return (int)(packed << 0 >> 38);
   }

   private static int unpackY(long packed) {
      return (int)(packed << 52 >> 52);
   }

   private static int unpackZ(long packed) {
      return (int)(packed << 26 >> 38);
   }
}
