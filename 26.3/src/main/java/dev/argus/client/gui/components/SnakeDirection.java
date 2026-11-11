package dev.argus.client.gui.components;

import dev.argus.client.utils.collections.IntPair;

public enum SnakeDirection {
   UP(0, -1),
   RIGHT(1, 0),
   DOWN(0, 1),
   LEFT(-1, 0);

   private final IntPair offset;

   SnakeDirection(int xOffset, int yOffset) {
      this.offset = new IntPair(xOffset, yOffset);
   }

   public IntPair getOffset() {
      return this.offset;
   }

   public SnakeDirection opposite() {
      return switch (this) {
         case UP -> DOWN;
         case RIGHT -> LEFT;
         case DOWN -> UP;
         case LEFT -> RIGHT;
      };
   }
}
