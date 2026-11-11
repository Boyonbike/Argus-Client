package dev.argus.client.config;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

public final class PresetUndoState {
   private static final int HISTORY_LIMIT = 5;
   private final Deque<PresetUndoAction> actions = new ArrayDeque<>();

   public void push(PresetUndoAction action) {
      if (action != null) {
         this.actions.addLast(action);

         while (this.actions.size() > 5) {
            this.actions.removeFirst();
         }
      }
   }

   public PresetUndoAction undo() throws IOException {
      PresetUndoAction action = this.actions.peekLast();
      if (action != null) {
         action.undo();
         this.actions.removeLast();
      }

      return action;
   }
}
