package dev.argus.client.modules.movement.elytratrip;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The laggy-server rules for one chest visit, ported from the mineflayer refill: re-click until the chest opens, wait for
 * each shift-click to really change its slot, give up when nothing moves, and never click while the chest is closed.
 * Pure: the driver passes in time and what the open window shows, and carries out the returned action.
 */
public final class ChestSessionLogic {
   public static final long RECLICK_MS = 1_000;

   public enum Result { RUNNING, DONE, OPEN_TIMEOUT, STALLED, CHEST_FULL, REOPEN_FAILED }

   /** One slot as the window shows it; any change in type, count or wear counts as the slot changing. */
   public record SlotView(String itemId, int count, int damage) {
   }

   public sealed interface Action permits Action.Click, Action.Plan, Action.ShiftClick, Action.Reopen, Action.Wait {
      /** Click the chest to open it. */
      record Click() implements Action {
      }

      /** The window is open: work out which slots to move and pass them to {@link #wantMoves}. */
      record Plan() implements Action {
      }

      /** Shift-click this window slot. */
      record ShiftClick(int slot) implements Action {
      }

      /** The chest closed on its own: click it again to reopen. */
      record Reopen() implements Action {
      }

      record Wait() implements Action {
      }
   }

   private enum State { OPENING, OPEN }

   private final long openTimeoutMs;
   private final long slotWaitMs;
   private final long stallMs;
   private State state = State.OPENING;
   private Result result = Result.RUNNING;
   private long openStart = Long.MIN_VALUE;
   private long lastClick;
   private boolean reopened;
   private boolean planned;
   private boolean full;
   private final Deque<Integer> moves = new ArrayDeque<>();
   private int awaitingSlot = -1;
   private SlotView awaitingBefore;
   private long awaitingSince;
   private long lastProgress;
   private int attempt;

   public ChestSessionLogic(long openTimeoutMs, long slotWaitMs, long stallMs) {
      this.openTimeoutMs = openTimeoutMs;
      this.slotWaitMs = slotWaitMs;
      this.stallMs = stallMs;
   }

   public Result result() {
      return result;
   }

   public void attemptStarted(int attempt) {
      this.attempt = attempt;
   }

   /** False for a window that answers an earlier attempt; the driver closes it instead of using it. */
   public boolean acceptWindow(int windowAttempt) {
      return windowAttempt == attempt;
   }

   public void wantMoves(List<Integer> slots) {
      moves.clear();
      moves.addAll(slots);
      planned = true;
   }

   /** The chest has no room left for what is being put in. */
   public void markFull() {
      full = true;
   }

   /**
    * @param openContainerId the container window that belongs to this chest, or null while none is open
    */
   public Action tick(long now, Integer openContainerId, List<SlotView> slots) {
      if (result != Result.RUNNING) return new Action.Wait();
      if (openStart == Long.MIN_VALUE) openStart = now;
      if (state == State.OPENING) return opening(now, openContainerId);
      if (openContainerId == null) {
         if (reopened) {
            result = Result.REOPEN_FAILED;
            return new Action.Wait();
         }
         reopened = true;
         state = State.OPENING;
         openStart = now;
         lastClick = now;
         planned = false;
         moves.clear();
         awaitingSlot = -1;
         return new Action.Reopen();
      }
      if (full) {
         result = Result.CHEST_FULL;
         return new Action.Wait();
      }
      if (!planned) return new Action.Plan();
      if (awaitingSlot >= 0) {
         boolean changed = awaitingSlot < slots.size() && !slots.get(awaitingSlot).equals(awaitingBefore);
         if (changed) {
            lastProgress = now;
         } else if (now - awaitingSince < slotWaitMs) {
            stalled(now);
            return new Action.Wait();
         }
         awaitingSlot = -1;
      }
      if (stalled(now)) return new Action.Wait();
      Integer next = moves.poll();
      if (next == null) {
         result = Result.DONE;
         return new Action.Wait();
      }
      awaitingSlot = next;
      awaitingBefore = next < slots.size() ? slots.get(next) : null;
      awaitingSince = now;
      return new Action.ShiftClick(next);
   }

   private Action opening(long now, Integer openContainerId) {
      if (openContainerId != null) {
         state = State.OPEN;
         lastProgress = now;
         return planned ? new Action.Wait() : new Action.Plan();
      }
      if (now - openStart >= openTimeoutMs) {
         result = Result.OPEN_TIMEOUT;
         return new Action.Wait();
      }
      if (now == openStart && !reopened || now - lastClick >= RECLICK_MS) {
         lastClick = now;
         return new Action.Click();
      }
      return new Action.Wait();
   }

   private boolean stalled(long now) {
      if (now - lastProgress >= stallMs) {
         result = Result.STALLED;
         return true;
      }
      return false;
   }
}
