package dev.argus.client.modules.movement.elytratrip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A saved inventory: how much of each supply to restock to, and which item goes in which slot. Pure: the module captures
 * it from the player and the refill puts items back where they were.
 *
 * @param slots item id per main-inventory slot 0..35, "" for a slot that was empty
 * @param elytras usable elytras, counting the one worn
 */
public record TripLoadout(List<String> slots, int elytras, int totems, int gapples, int food) {
   public static final int SIZE = 36;
   private static final String VERSION = "v1";

   public TripLoadout {
      List<String> fixed = new ArrayList<>(Collections.nCopies(SIZE, ""));
      for (int i = 0; i < Math.min(SIZE, slots.size()); i++) fixed.set(i, slots.get(i) == null ? "" : slots.get(i));
      slots = List.copyOf(fixed);
   }

   /** "v1|e=7|t=4|g=16|f=64|0=minecraft:elytra|9=minecraft:golden_apple". */
   public String encode() {
      StringBuilder text = new StringBuilder(VERSION).append("|e=").append(elytras).append("|t=").append(totems).append("|g=").append(gapples)
         .append("|f=").append(food);
      for (int i = 0; i < SIZE; i++) {
         if (!slots.get(i).isEmpty()) text.append('|').append(i).append('=').append(slots.get(i));
      }
      return text.toString();
   }

   /** The loadout in {@code text}, or null when there is none or it is damaged. */
   public static TripLoadout decode(String text) {
      if (text == null || !text.startsWith(VERSION + "|")) return null;
      List<String> slots = new ArrayList<>(Collections.nCopies(SIZE, ""));
      int elytras = 0, totems = 0, gapples = 0, food = 0;
      try {
         for (String part : text.substring(VERSION.length() + 1).split("\\|")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String key = part.substring(0, eq), value = part.substring(eq + 1);
            switch (key) {
               case "e" -> elytras = Integer.parseInt(value);
               case "t" -> totems = Integer.parseInt(value);
               case "g" -> gapples = Integer.parseInt(value);
               case "f" -> food = Integer.parseInt(value);
               default -> {
                  int slot = Integer.parseInt(key);
                  if (slot >= 0 && slot < SIZE) slots.set(slot, value);
               }
            }
         }
      } catch (NumberFormatException e) {
         return null;
      }
      return new TripLoadout(slots, elytras, totems, gapples, food);
   }

   /** The limits with this loadout's quantities as targets. Never below a minimum, and Fill empty is off: the loadout says how many. */
   public SupplyLimits apply(SupplyLimits limits) {
      return new SupplyLimits(limits.spareMin(), limits.totemMin(), limits.gappleMin(), limits.foodMin(), limits.armourMinPercent(),
         limits.wornBelow(), limits.freshAbove(), Math.max(elytras, limits.spareMin() + 1), false, Math.max(totems, limits.totemMin()),
         Math.max(gapples, limits.gappleMin()), Math.max(food, limits.foodMin()), limits.swapBelow());
   }

   /**
    * The next swap that puts an item back in its saved slot, as {from, to} inventory indexes, or null when nothing more can
    * move. Never takes from a slot that already holds its saved item, so it settles in at most {@link #SIZE} swaps.
    *
    * @param current item id per main-inventory slot now, "" for empty
    */
   public int[] nextMove(List<String> current) {
      for (int to = 0; to < SIZE; to++) {
         String want = slots.get(to);
         if (want.isEmpty() || want.equals(current.get(to))) continue;
         for (int from = 0; from < SIZE; from++) {
            if (from == to || !want.equals(current.get(from)) || slots.get(from).equals(current.get(from))) continue;
            return new int[]{from, to};
         }
      }
      return null;
   }

   /** "7 elytras, 4 totems, 16 gapples, 64 food". */
   public String summary() {
      return elytras + " elytras, " + totems + " totems, " + gapples + " gapples, " + food + " food";
   }
}
