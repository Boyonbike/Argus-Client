package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum MinesweeperDifficulty implements NamedEntry {
   EASY("Easy", 10, 10, 15, 600),
   MEDIUM("Medium", 16, 16, 40, 2400),
   HARD("Hard", 16, 30, 99, 5940);

   private final String name;
   private final int columns;
   private final int rows;
   private final int mineCount;
   private final int initialScore;

   MinesweeperDifficulty(String name, int columns, int rows, int mineCount, int initialScore) {
      this.name = name;
      this.columns = columns;
      this.rows = rows;
      this.mineCount = mineCount;
      this.initialScore = initialScore;
   }

   public int getColumns() {
      return this.columns;
   }

   public int getRows() {
      return this.rows;
   }

   public int getMineCount() {
      return this.mineCount;
   }

   public int getInitialScore() {
      return this.initialScore;
   }

   @Override
   public String getName() {
      return this.name;
   }
}
