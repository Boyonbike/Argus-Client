package dev.argus.client.gui.components;

class MinesweeperCell {
   MinesweeperCellContent content;
   MinesweeperCellState state = MinesweeperCellState.COVERED;

   public MinesweeperCell(MinesweeperCellContent content) {
      this.content = content;
   }

   public MinesweeperSprite getSprite() {
      return switch (this.state) {
         case COVERED -> MinesweeperSprite.UNCOVERED;
         case FLAGGED -> MinesweeperSprite.FLAG;
         case BLOWN -> MinesweeperSprite.BOMB_BLEW;
         case OPEN -> {
            switch (this.content) {
               case BOMB:
                  yield MinesweeperSprite.BOMB;
               case NUMBER_0:
                  yield MinesweeperSprite.EMPTY;
               case NUMBER_1:
                  yield MinesweeperSprite.ONE;
               case NUMBER_2:
                  yield MinesweeperSprite.TWO;
               case NUMBER_3:
                  yield MinesweeperSprite.THREE;
               case NUMBER_4:
                  yield MinesweeperSprite.FOUR;
               case NUMBER_5:
                  yield MinesweeperSprite.FIVE;
               case NUMBER_6:
                  yield MinesweeperSprite.SIX;
               case NUMBER_7:
                  yield MinesweeperSprite.SEVEN;
               case NUMBER_8:
                  yield MinesweeperSprite.EIGHT;
               default:
                  throw new MatchException(null, null);
            }
         }
      };
   }
}
