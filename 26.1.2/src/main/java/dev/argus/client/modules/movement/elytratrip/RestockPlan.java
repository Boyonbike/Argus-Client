package dev.argus.client.modules.movement.elytratrip;

import dev.argus.client.modules.movement.elytratrip.TripSupplies.SupplyCounts;
import java.util.ArrayList;
import java.util.List;

/** Pure decisions for a station visit: what to dump, how much of each role to take, and whether the result is good enough. */
public final class RestockPlan {
   private RestockPlan() {
   }

   public enum Kind { ELYTRA, ARMOUR, TOTEM, GAPPLE, FOOD, OTHER }

   public enum Outcome {
      OK,
      /** Some supply is still below its minimum: the trip ends at the station. */
      SHORT,
      /** No wearable elytra, or too few to leave with. */
      NO_ELYTRA
   }

   /** One inventory stack. {@code percent} is durability left as a fraction of max, for damageable items. */
   public record ItemSummary(int slot, Kind kind, int durability, int max, float percent) {
   }

   public static List<Integer> dump(List<ItemSummary> inventory, SupplyLimits limits, boolean dumpJunk) {
      List<Integer> slots = new ArrayList<>();
      for (ItemSummary item : inventory) {
         boolean out = switch (item.kind()) {
            case ELYTRA -> TripSupplies.isWornOut(item.durability(), item.max(), limits);
            case ARMOUR -> item.percent() < limits.armourMinPercent();
            case OTHER -> dumpJunk;
            default -> false;
         };
         if (out) slots.add(item.slot());
      }
      return slots;
   }

   /** Usable elytras: the worn one if it has more than the spare threshold left, plus every spare that counts. */
   public static int usableElytras(SupplyCounts counts) {
      boolean wornUsable = counts.wornElytraDurability() > SupplyLimits.SPARE_ABOVE;
      return (wornUsable ? 1 : 0) + counts.spares();
   }

   /** How many more items of this role to take. */
   public static int want(SupplyCounts counts, SupplyLimits limits, ChestRole role) {
      return switch (role) {
         case ELYTRAS -> Math.max(0, limits.elytraTarget() - usableElytras(counts));
         case TOTEMS -> Math.max(0, limits.totemTarget() - counts.totems());
         case GAPPLES -> Math.max(0, limits.gappleTarget() - counts.gapples());
         case FOOD -> Math.max(0, limits.foodTarget() - counts.food());
         case ARMOUR -> armourWanted(counts, limits);
         case DUMP -> 0;
      };
   }

   /** With Fill empty on, the elytras that fill every slot still free once every other supply is restocked. */
   public static int fillWant(SupplyCounts counts, SupplyLimits limits) {
      return limits.fillEmpty() ? counts.freeSlots() : 0;
   }

   private static int armourWanted(SupplyCounts counts, SupplyLimits limits) {
      int wanted = 0;
      List<Float> armour = counts.armourPercents();
      for (int i = 0; i < 4; i++) {
         if (i == 1 && counts.wearingElytra()) continue;
         float percent = i < armour.size() ? armour.get(i) : -1.0F;
         if (percent < 0.0F || percent < limits.armourMinPercent()) wanted++;
      }
      return wanted;
   }

   public static Outcome outcome(SupplyCounts after, SupplyLimits limits, boolean afterDeath) {
      if (!after.wearingElytra() || usableElytras(after) < (afterDeath ? 1 : 2)) return Outcome.NO_ELYTRA;
      return TripSupplies.belowMinimum(after, limits).isEmpty() ? Outcome.OK : Outcome.SHORT;
   }
}
