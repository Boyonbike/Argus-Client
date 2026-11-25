package dev.argus.client.modules.movement.elytratrip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.argus.client.modules.movement.elytratrip.TripSupplies.Supply;
import dev.argus.client.modules.movement.elytratrip.TripSupplies.SupplyCounts;
import dev.argus.client.modules.movement.elytratrip.TripSupplies.SwapAction;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class TripSuppliesTest {
   private static final int MAX = 432;
   private static final SupplyLimits LIMITS = SupplyLimits.defaults();
   private static final List<Float> GOOD_ARMOUR = List.of(1.0F, -1.0F, 1.0F, 1.0F);

   private static SupplyCounts counts(int worn, List<Integer> spares, int totems, int gapples, int food, List<Float> armour) {
      return new SupplyCounts(worn, MAX, spares, MAX, totems, gapples, food, armour, 10);
   }

   private static SupplyCounts stocked(int worn, List<Integer> spares) {
      return counts(worn, spares, 4, 16, 64, GOOD_ARMOUR);
   }

   @Test
   void shortListsEachSupplyBelowMinimum() {
      SupplyCounts counts = counts(400, List.of(), 0, 1, 15, List.of(0.10F, -1.0F, 1.0F, 1.0F));
      assertEquals(EnumSet.allOf(Supply.class), TripSupplies.belowMinimum(counts, LIMITS));
   }

   @Test
   void stockedIsNotShort() {
      assertTrue(TripSupplies.belowMinimum(stocked(400, List.of(432)), LIMITS).isEmpty());
   }

   @Test
   void wornElytraIsNeverASpare() {
      assertEquals(EnumSet.of(Supply.ELYTRAS), TripSupplies.belowMinimum(stocked(400, List.of()), LIMITS));
   }

   @Test
   void spareNeedsMoreThanTheSwapThreshold() {
      assertEquals(EnumSet.of(Supply.ELYTRAS), TripSupplies.belowMinimum(stocked(400, List.of(30)), LIMITS));
      assertTrue(TripSupplies.belowMinimum(stocked(400, List.of(31)), LIMITS).isEmpty());
   }

   @Test
   void wornOutCappedByFreshThreshold() {
      SupplyLimits limits = LIMITS.withWornBelow(400);
      assertTrue(TripSupplies.isWornOut(323, MAX, limits));
      assertFalse(TripSupplies.isWornOut(324, MAX, limits));
   }

   @Test
   void wornOutNeverAtOrBelowTen() {
      SupplyLimits limits = LIMITS.withWornBelow(5);
      assertTrue(TripSupplies.isWornOut(10, MAX, limits));
      assertFalse(TripSupplies.isWornOut(11, MAX, limits));
   }

   @Test
   void freshNeedsMoreThanSeventyFivePercent() {
      assertFalse(TripSupplies.isFresh(324, MAX, LIMITS));
      assertTrue(TripSupplies.isFresh(325, MAX, LIMITS));
   }

   @Test
   void swapAtThreshold() {
      assertEquals(SwapAction.SWAP, TripSupplies.swapAction(stocked(30, List.of(432)), LIMITS, 5));
      assertEquals(SwapAction.NONE, TripSupplies.swapAction(stocked(31, List.of(432)), LIMITS, 5));
   }

   @Test
   void swapStaysAboveBaritoneMinimum() {
      assertEquals(SwapAction.SWAP, TripSupplies.swapAction(stocked(6, List.of(432)), LIMITS.withSwapBelow(3), 5));
   }

   @Test
   void noSpareAtSwapPointMeansRefillNow() {
      assertEquals(SwapAction.REFILL_NOW, TripSupplies.swapAction(stocked(30, List.of()), LIMITS, 5));
      assertEquals(SwapAction.REFILL_NOW, TripSupplies.swapAction(stocked(30, List.of(10)), LIMITS, 5));
   }

   @Test
   void swappedOutElytraIsNotASwapTarget() {
      // The elytra just swapped out (about 30 left) must not be swapped straight back in.
      assertEquals(SwapAction.REFILL_NOW, TripSupplies.swapAction(stocked(30, List.of(30)), LIMITS, 5));
      assertEquals(SwapAction.SWAP, TripSupplies.swapAction(stocked(30, List.of(30, 31)), LIMITS, 5));
   }
}
