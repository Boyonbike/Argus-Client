package dev.argus.client.gui.components;

public enum MinesweeperCellContent {
   BOMB,
   NUMBER_0,
   NUMBER_1,
   NUMBER_2,
   NUMBER_3,
   NUMBER_4,
   NUMBER_5,
   NUMBER_6,
   NUMBER_7,
   NUMBER_8;

   public static MinesweeperCellContent fromAdjacentMines(int adjacentMines) {
      return values()[adjacentMines + 1];
   }
}
