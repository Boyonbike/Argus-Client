package dev.argus.client.modules.movement.elytratrip;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class TripLoadoutTest {
   private static final String ELYTRA = "minecraft:elytra";
   private static final String TOTEM = "minecraft:totem_of_undying";
   private static final String GAPPLE = "minecraft:enchanted_golden_apple";

   private static List<String> slots(Object... slotThenId) {
      List<String> list = new ArrayList<>(Collections.nCopies(TripLoadout.SIZE, ""));
      for (int i = 0; i < slotThenId.length; i += 2) list.set((Integer)slotThenId[i], (String)slotThenId[i + 1]);
      return list;
   }

   @Test
   void encodesAndDecodes() {
      TripLoadout loadout = new TripLoadout(slots(0, GAPPLE, 1, TOTEM, 20, ELYTRA), 7, 4, 16, 64);
      TripLoadout back = TripLoadout.decode(loadout.encode());
      assertEquals(loadout, back);
   }

   @Test
   void decodeRejectsMissingOrDamaged() {
      assertNull(TripLoadout.decode(""));
      assertNull(TripLoadout.decode(null));
      assertNull(TripLoadout.decode("v1|e=x"));
   }

   @Test
   void targetsComeFromTheLoadoutButNeverBelowMinimum() {
      SupplyLimits base = SupplyLimits.defaults();
      SupplyLimits applied = new TripLoadout(slots(), 9, 0, 32, 10).apply(base);
      assertEquals(9, applied.elytraTarget());
      assertEquals(base.totemMin(), applied.totemTarget());
      assertEquals(32, applied.gappleTarget());
      assertEquals(base.foodMin(), applied.foodTarget());
      assertFalse(applied.fillEmpty());
   }

   @Test
   void movesItemsBackToTheirSlots() {
      TripLoadout loadout = new TripLoadout(slots(0, GAPPLE, 1, TOTEM), 0, 1, 1, 0);
      List<String> current = slots(5, TOTEM, 9, GAPPLE);
      int moves = 0;
      for (int[] move; (move = loadout.nextMove(current)) != null && moves < 10; moves++) {
         String a = current.get(move[0]);
         current.set(move[0], current.get(move[1]));
         current.set(move[1], a);
      }
      assertEquals(2, moves);
      assertEquals(GAPPLE, current.get(0));
      assertEquals(TOTEM, current.get(1));
   }

   @Test
   void neverTakesFromASlotThatIsAlreadyRight() {
      TripLoadout loadout = new TripLoadout(slots(0, ELYTRA, 1, ELYTRA, 2, ELYTRA), 3, 0, 0, 0);
      // Only two elytras: slot 2 stays short rather than robbing slot 0 or 1.
      assertNull(loadout.nextMove(slots(0, ELYTRA, 1, ELYTRA)));
      assertArrayEquals(new int[]{8, 1}, loadout.nextMove(slots(0, ELYTRA, 8, ELYTRA)));
      assertTrue(loadout.summary().startsWith("3 elytras"));
   }
}
