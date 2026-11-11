package dev.argus.client.utils.world;

import java.util.ArrayList;
import java.util.List;

public enum Season {
   WINTER(1, 2, 12),
   SPRING(3, 4, 5),
   SUMMER(6, 7, 8),
   AUTUMN(9, 10, 11);

   private final List<Integer> months = new ArrayList<>();

   Season(int... monthValues) {
      for (int month : monthValues) {
         this.months.add(month);
      }
   }

   public boolean containsMonth(int month) {
      return this.months.contains(month);
   }

   public static Season fromMonth(int month) {
      for (Season season : values()) {
         if (season.containsMonth(month)) {
            return season;
         }
      }

      throw new IllegalArgumentException("Unknown month");
   }
}
