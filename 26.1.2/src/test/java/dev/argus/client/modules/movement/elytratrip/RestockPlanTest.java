package dev.argus.client.modules.movement.elytratrip;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.argus.client.modules.movement.elytratrip.RestockPlan.ItemSummary;
import dev.argus.client.modules.movement.elytratrip.RestockPlan.Kind;
import dev.argus.client.modules.movement.elytratrip.RestockPlan.Outcome;
import dev.argus.client.modules.movement.elytratrip.TripSupplies.SupplyCounts;
import java.util.List;
import org.junit.jupiter.api.Test;

class RestockPlanTest {
   private static final int MAX = 432;
   private static final SupplyLimits LIMITS = SupplyLimits.defaults();

   private static ItemSummary elytra(int slot, int durability) {
      return new ItemSummary(slot, Kind.ELYTRA, durability, MAX, durability / (float)MAX);
   }

   private static ItemSummary armour(int slot, float percent) {
      return new ItemSummary(slot, Kind.ARMOUR, (int)(percent * 500), 500, percent);
   }

   private static SupplyCounts counts(int worn, List<Integer> spares, int totems, int gapples, int food, List<Float> armour, int free) {
      return new SupplyCounts(worn, MAX, spares, MAX, totems, gapples, food, armour, free);
   }

   @Test
   void dumpsWornElytrasAndWornArmourOnly() {
      List<ItemSummary> inventory = List.of(elytra(0, 50), elytra(1, 400), armour(2, 0.10F), armour(3, 0.90F),
         new ItemSummary(4, Kind.OTHER, 0, 0, 0), new ItemSummary(5, Kind.TOTEM, 0, 0, 0));
      assertEquals(List.of(0, 2), RestockPlan.dump(inventory, LIMITS, false));
   }

   @Test
   void dumpJunkAddsOther() {
      List<ItemSummary> inventory = List.of(elytra(0, 50), new ItemSummary(4, Kind.OTHER, 0, 0, 0), new ItemSummary(5, Kind.FOOD, 0, 0, 0));
      assertEquals(List.of(0, 4), RestockPlan.dump(inventory, LIMITS, true));
   }

   @Test
   void elytraWantIsTheTargetEvenWithFillEmpty() {
      // Worn plus one spare is 2 of 6; filling every slot comes last, after the other supplies.
      assertEquals(4, RestockPlan.want(counts(400, List.of(400), 4, 16, 64, List.of(), 12), LIMITS, ChestRole.ELYTRAS));
   }

   @Test
   void fillWantTakesEveryFreeSlotOnlyWithFillEmpty() {
      SupplyCounts counts = counts(400, List.of(400), 4, 16, 64, List.of(), 12);
      assertEquals(12, RestockPlan.fillWant(counts, LIMITS));
      SupplyLimits noFill = new SupplyLimits(1, 1, 2, 16, 0.15F, 100, 0.75F, 6, false, 4, 16, 64, 30);
      assertEquals(0, RestockPlan.fillWant(counts, noFill));
   }

   @Test
   void elytraWantToTarget() {
      SupplyLimits noFill = new SupplyLimits(1, 1, 2, 16, 0.15F, 100, 0.75F, 6, false, 4, 16, 64, 30);
      // Worn plus two usable spares is 3 of 6.
      assertEquals(3, RestockPlan.want(counts(400, List.of(400, 300, 5), 4, 16, 64, List.of(), 20), noFill, ChestRole.ELYTRAS));
      assertEquals(0, RestockPlan.want(counts(400, List.of(400, 400, 400, 400, 400, 400), 4, 16, 64, List.of(), 20), noFill, ChestRole.ELYTRAS));
   }

   @Test
   void consumableWantsToTarget() {
      SupplyCounts counts = counts(400, List.of(), 1, 10, 40, List.of(), 20);
      assertEquals(3, RestockPlan.want(counts, LIMITS, ChestRole.TOTEMS));
      assertEquals(6, RestockPlan.want(counts, LIMITS, ChestRole.GAPPLES));
      assertEquals(24, RestockPlan.want(counts, LIMITS, ChestRole.FOOD));
      assertEquals(0, RestockPlan.want(counts, LIMITS, ChestRole.DUMP));
   }

   @Test
   void armourWantSkipsChestplateUnderElytra() {
      SupplyCounts counts = counts(400, List.of(), 4, 16, 64, List.of(-1.0F, -1.0F, 0.10F, 0.90F), 20);
      assertEquals(2, RestockPlan.want(counts, LIMITS, ChestRole.ARMOUR));
      SupplyCounts noElytra = counts(-1, List.of(), 4, 16, 64, List.of(-1.0F, -1.0F, 0.10F, 0.90F), 20);
      assertEquals(3, RestockPlan.want(noElytra, LIMITS, ChestRole.ARMOUR));
   }

   @Test
   void outcomeOkWhenStocked() {
      assertEquals(Outcome.OK, RestockPlan.outcome(counts(432, List.of(432, 432), 4, 16, 64, List.of(1F, -1F, 1F, 1F), 0), LIMITS, false));
   }

   @Test
   void outcomeShortWhenTotemsStillBelowMinimum() {
      assertEquals(Outcome.SHORT, RestockPlan.outcome(counts(432, List.of(432, 432), 0, 16, 64, List.of(1F, -1F, 1F, 1F), 0), LIMITS, false));
   }

   @Test
   void outcomeNoElytraNeedsTwo() {
      assertEquals(Outcome.NO_ELYTRA, RestockPlan.outcome(counts(-1, List.of(432, 432), 4, 16, 64, List.of(), 0), LIMITS, false));
      assertEquals(Outcome.NO_ELYTRA, RestockPlan.outcome(counts(432, List.of(), 4, 16, 64, List.of(), 0), LIMITS, false));
   }

   @Test
   void outcomeNoElytraAfterDeathNeedsOne() {
      // One worn elytra is enough after a death; the spare minimum still makes it short.
      assertEquals(Outcome.SHORT, RestockPlan.outcome(counts(432, List.of(), 4, 16, 64, List.of(), 0), LIMITS, true));
   }
}
