package dev.argus.client.modules.movement.elytratrip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.argus.client.modules.movement.elytratrip.ChestSessionLogic.Action;
import dev.argus.client.modules.movement.elytratrip.ChestSessionLogic.Result;
import dev.argus.client.modules.movement.elytratrip.ChestSessionLogic.SlotView;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChestSessionLogicTest {
   private static final Integer WINDOW = 7;

   private static List<SlotView> slots(int n) {
      List<SlotView> slots = new ArrayList<>();
      for (int i = 0; i < n; i++) slots.add(new SlotView("minecraft:elytra", 1, 0));
      return slots;
   }

   private static List<SlotView> emptied(List<SlotView> slots, int... indices) {
      List<SlotView> copy = new ArrayList<>(slots);
      for (int i : indices) copy.set(i, new SlotView("minecraft:air", 0, 0));
      return copy;
   }

   private static ChestSessionLogic logic() {
      return new ChestSessionLogic(30_000, 1_500, 15_000);
   }

   /** Opens the window and plans the given moves, returning the first move's action. */
   private static Action open(ChestSessionLogic logic, List<SlotView> slots, Integer... moves) {
      assertInstanceOf(Action.Click.class, logic.tick(0, null, slots));
      assertInstanceOf(Action.Plan.class, logic.tick(100, WINDOW, slots));
      logic.wantMoves(List.of(moves));
      return logic.tick(100, WINDOW, slots);
   }

   @Test
   void reclicksEverySecondUntilTimeout() {
      ChestSessionLogic logic = logic();
      List<SlotView> slots = slots(2);
      assertInstanceOf(Action.Click.class, logic.tick(0, null, slots));
      assertInstanceOf(Action.Wait.class, logic.tick(999, null, slots));
      assertInstanceOf(Action.Click.class, logic.tick(1_000, null, slots));
      assertInstanceOf(Action.Click.class, logic.tick(2_000, null, slots));
      assertEquals(Result.RUNNING, logic.result());
      logic.tick(30_000, null, slots);
      assertEquals(Result.OPEN_TIMEOUT, logic.result());
   }

   @Test
   void lateWindowFromEarlierAttemptIsRejected() {
      ChestSessionLogic logic = logic();
      logic.attemptStarted(2);
      assertFalse(logic.acceptWindow(1));
      assertTrue(logic.acceptWindow(2));
   }

   @Test
   void waitsForSlotChangeBeforeNextClick() {
      ChestSessionLogic logic = logic();
      List<SlotView> slots = slots(2);
      assertEquals(new Action.ShiftClick(0), open(logic, slots, 0, 1));
      assertInstanceOf(Action.Wait.class, logic.tick(200, WINDOW, slots));
      assertEquals(new Action.ShiftClick(1), logic.tick(300, WINDOW, emptied(slots, 0)));
   }

   @Test
   void slotUnchangedAfterWaitMovesOn() {
      ChestSessionLogic logic = logic();
      List<SlotView> slots = slots(2);
      open(logic, slots, 0, 1);
      assertInstanceOf(Action.Wait.class, logic.tick(1_599, WINDOW, slots));
      assertEquals(new Action.ShiftClick(1), logic.tick(1_600, WINDOW, slots));
   }

   @Test
   void stallAfterFifteenSeconds() {
      ChestSessionLogic logic = logic();
      List<SlotView> slots = slots(20);
      Integer[] moves = new Integer[20];
      for (int i = 0; i < 20; i++) moves[i] = i;
      open(logic, slots, moves);
      for (long t = 100; t < 15_100; t += 100) {
         logic.tick(t, WINDOW, slots);
         assertEquals(Result.RUNNING, logic.result());
      }
      logic.tick(15_100, WINDOW, slots);
      assertEquals(Result.STALLED, logic.result());
   }

   @Test
   void closedWindowStopsClickingAndReopensOnce() {
      ChestSessionLogic logic = logic();
      List<SlotView> slots = slots(3);
      open(logic, slots, 0, 1, 2);
      assertInstanceOf(Action.Reopen.class, logic.tick(200, null, slots));
      for (long t = 300; t < 5_000; t += 100) {
         Action action = logic.tick(t, null, slots);
         assertFalse(action instanceof Action.ShiftClick, "clicked into the player's inventory at " + t);
      }
      assertInstanceOf(Action.Plan.class, logic.tick(5_000, WINDOW, slots));
      assertEquals(Result.RUNNING, logic.result());
   }

   @Test
   void secondCloseFails() {
      ChestSessionLogic logic = logic();
      List<SlotView> slots = slots(3);
      open(logic, slots, 0, 1, 2);
      logic.tick(200, null, slots);
      logic.tick(300, WINDOW, slots);
      logic.wantMoves(List.of(1, 2));
      logic.tick(300, WINDOW, slots);
      logic.tick(400, null, slots);
      assertEquals(Result.REOPEN_FAILED, logic.result());
   }

   @Test
   void doneWhenAllMovesApplied() {
      ChestSessionLogic logic = logic();
      List<SlotView> slots = slots(2);
      open(logic, slots, 0, 1);
      List<SlotView> after0 = emptied(slots, 0);
      assertEquals(new Action.ShiftClick(1), logic.tick(200, WINDOW, after0));
      logic.tick(300, WINDOW, emptied(slots, 0, 1));
      assertEquals(Result.DONE, logic.result());
   }

   @Test
   void nothingToMoveIsDone() {
      ChestSessionLogic logic = logic();
      List<SlotView> slots = slots(2);
      open(logic, slots);
      assertEquals(Result.DONE, logic.result());
   }

   @Test
   void fullStops() {
      ChestSessionLogic logic = logic();
      List<SlotView> slots = slots(2);
      open(logic, slots, 0, 1);
      logic.markFull();
      logic.tick(200, WINDOW, slots);
      assertEquals(Result.CHEST_FULL, logic.result());
   }
}
