package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum GameType implements NamedEntry {
   TETRIS("Tetris"),
   SNAKE("Snake"),
   MINESWEEPER("Minesweeper");

   private final String name;

   GameType(String name) {
      this.name = name;
   }

   @Override
   public String getName() {
      return this.name;
   }
}
