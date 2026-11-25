package dev.argus.client.modules.movement.elytratrip;

/**
 * The refill thresholds ElytraTrip works to: below a minimum starts a refill, a refill takes up to the target.
 *
 * @param armourMinPercent fraction of max durability (0.15 = 15%) below which a worn armour piece needs replacing
 * @param wornBelow an elytra with less durability than this is dumped at the station
 * @param freshAbove fraction of max durability an elytra must exceed to be taken from the station
 * @param swapBelow the worn elytra is swapped in flight at or below this durability
 */
public record SupplyLimits(int spareMin, int totemMin, int gappleMin, int foodMin, float armourMinPercent, int wornBelow,
                           float freshAbove, int elytraTarget, boolean fillEmpty, int totemTarget, int gappleTarget,
                           int foodTarget, int swapBelow) {
   /** A spare elytra must have more than this much durability left to count. */
   public static final int SPARE_ABOVE = 10;

   public static SupplyLimits defaults() {
      return new SupplyLimits(1, 1, 2, 16, 0.15F, 100, 0.75F, 6, true, 4, 16, 64, 30);
   }

   public SupplyLimits withWornBelow(int value) {
      return new SupplyLimits(spareMin, totemMin, gappleMin, foodMin, armourMinPercent, value, freshAbove, elytraTarget, fillEmpty,
         totemTarget, gappleTarget, foodTarget, swapBelow);
   }

   public SupplyLimits withSwapBelow(int value) {
      return new SupplyLimits(spareMin, totemMin, gappleMin, foodMin, armourMinPercent, wornBelow, freshAbove, elytraTarget, fillEmpty,
         totemTarget, gappleTarget, foodTarget, value);
   }
}
