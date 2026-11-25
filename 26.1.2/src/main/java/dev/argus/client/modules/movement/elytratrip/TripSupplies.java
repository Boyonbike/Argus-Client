package dev.argus.client.modules.movement.elytratrip;

import java.util.EnumSet;
import java.util.List;

/** Pure decisions about what the player carries: what is worn out, what is fresh, what is short, when to swap. */
public final class TripSupplies {
   private TripSupplies() {
   }

   public enum Supply {
      ELYTRAS("elytras"), TOTEMS("totems"), GAPPLES("gapples"), FOOD("food"), ARMOUR("armour");

      private final String label;

      Supply(String label) {
         this.label = label;
      }

      public String label() {
         return label;
      }
   }

   public enum SwapAction { NONE, SWAP, REFILL_NOW }

   /**
    * What the player carries.
    *
    * @param wornElytraDurability durability left on the elytra worn on the chest, or -1 when none is worn
    * @param spareElytraDurabilities durability left on each elytra in the inventory, not counting the worn one
    * @param armourPercents helmet, chest, legs and boots as fractions of max durability, -1 for an empty slot; the chest
    *                       slot is ignored while an elytra is worn
    */
   public record SupplyCounts(int wornElytraDurability, int wornElytraMax, List<Integer> spareElytraDurabilities, int elytraMax,
                              int totems, int gapples, int food, List<Float> armourPercents, int freeSlots) {
      public boolean wearingElytra() {
         return wornElytraDurability >= 0;
      }

      public int spares() {
         return spares(SupplyLimits.SPARE_ABOVE);
      }

      /** Spare elytras with more than {@code above} durability left. */
      public int spares(int above) {
         return (int)spareElytraDurabilities.stream().filter(d -> d > above).count();
      }
   }

   public static EnumSet<Supply> belowMinimum(SupplyCounts counts, SupplyLimits limits) {
      EnumSet<Supply> result = EnumSet.noneOf(Supply.class);
      if (counts.spares(spareAbove(limits)) < limits.spareMin()) result.add(Supply.ELYTRAS);
      if (counts.totems() < limits.totemMin()) result.add(Supply.TOTEMS);
      if (counts.gapples() < limits.gappleMin()) result.add(Supply.GAPPLES);
      if (counts.food() < limits.foodMin()) result.add(Supply.FOOD);
      if (armourShort(counts, limits)) result.add(Supply.ARMOUR);
      return result;
   }

   private static boolean armourShort(SupplyCounts counts, SupplyLimits limits) {
      List<Float> armour = counts.armourPercents();
      for (int i = 0; i < armour.size(); i++) {
         if (i == 1 && counts.wearingElytra()) continue;
         float percent = armour.get(i);
         if (percent >= 0.0F && percent < limits.armourMinPercent()) return true;
      }
      return false;
   }

   /**
    * Less than {@code wornBelow}, capped at the fresh threshold so a freshly taken elytra is never worn out straight away,
    * and the limit is never at or below {@link SupplyLimits#SPARE_ABOVE}.
    */
   public static boolean isWornOut(int durability, int max, SupplyLimits limits) {
      int limit = Math.min(limits.wornBelow(), (int)Math.floor(limits.freshAbove() * max));
      limit = Math.max(limit, SupplyLimits.SPARE_ABOVE + 1);
      return durability < limit;
   }

   /**
    * A spare only counts when it has more left than the in-flight swap point; otherwise the elytra just swapped out would
    * count, and be swapped straight back in.
    */
   public static int spareAbove(SupplyLimits limits) {
      return Math.max(SupplyLimits.SPARE_ABOVE, limits.swapBelow());
   }

   public static boolean isFresh(int durability, int max, SupplyLimits limits) {
      return durability > limits.freshAbove() * max;
   }

   public static SwapAction swapAction(SupplyCounts counts, SupplyLimits limits, int baritoneMinimum) {
      int threshold = Math.max(limits.swapBelow(), baritoneMinimum + 1);
      if (counts.wearingElytra() && counts.wornElytraDurability() > threshold) return SwapAction.NONE;
      return counts.spares(threshold) > 0 ? SwapAction.SWAP : SwapAction.REFILL_NOW;
   }
}
