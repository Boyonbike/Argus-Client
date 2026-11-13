package dev.argus.client.modules.basehunting;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * One remembered find per line: key x,y,z, the box corners, its own colour as #AARRGGBB (or a bare # for the module's
 * colours), then what it was (which may itself hold commas). Lines without the colour field still load.
 */
final class MarkCodec {
   private MarkCodec() {
   }

   static String encode(BlockPos key, HuntMarkers.Mark mark) {
      AABB box = mark.box();
      return key.getX() + "," + key.getY() + "," + key.getZ() + ","
         + box.minX + "," + box.minY + "," + box.minZ + ","
         + box.maxX + "," + box.maxY + "," + box.maxZ + ","
         + (mark.outline() == null ? "#" : String.format("#%08X", mark.outline().getRGB())) + ","
         + mark.what().replace('\n', ' ');
   }

   /** The key part of an encoded line, used to merge saved files without duplicates. */
   static String keyOf(String line) {
      int third = nthComma(line, 3);
      return third < 0 ? line : line.substring(0, third);
   }

   static Entry decode(String line) {
      String[] parts = line.split(",", 11);
      if (parts.length < 10) {
         return null;
      }

      java.awt.Color outline = null;
      String what;
      if (parts[9].startsWith("#") && parts.length == 11) {
         what = parts[10];
         if (parts[9].length() == 9) {
            try {
               outline = new java.awt.Color((int)Long.parseLong(parts[9].substring(1), 16), true);
            } catch (NumberFormatException malformed) {
               return null;
            }
         }
      } else {
         what = line.split(",", 10)[9];
      }

      try {
         BlockPos key = new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
         AABB box = new AABB(
            Double.parseDouble(parts[3]), Double.parseDouble(parts[4]), Double.parseDouble(parts[5]),
            Double.parseDouble(parts[6]), Double.parseDouble(parts[7]), Double.parseDouble(parts[8])
         );
         return new Entry(key, new HuntMarkers.Mark(box, what, outline == null ? null : HuntMarkers.fillFor(outline), outline));
      } catch (NumberFormatException malformed) {
         return null;
      }
   }

   private static int nthComma(String line, int n) {
      int index = -1;
      for (int i = 0; i < n; i++) {
         index = line.indexOf(',', index + 1);
         if (index < 0) {
            return -1;
         }
      }

      return index;
   }

   record Entry(BlockPos key, HuntMarkers.Mark mark) {
   }
}
