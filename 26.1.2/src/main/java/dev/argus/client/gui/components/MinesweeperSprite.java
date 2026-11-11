package dev.argus.client.gui.components;

public enum MinesweeperSprite {
   ONE,
   TWO,
   THREE,
   FOUR,
   UNCOVERED,
   EMPTY,
   BOMB,
   BOMB_HIDDEN,
   FIVE,
   SIX,
   SEVEN,
   EIGHT,
   FLAG,
   HOVERED,
   BOMB_BLEW,
   FLAG_NO_BG;

   private static final float U_STEP = 0.125F;
   private static final float V_STEP = 0.5F;
   private final float minU = this.ordinal() % 8 * 0.125F;
   private final float maxU = this.minU + 0.125F;
   private final float minV = this.ordinal() / 8 * 0.5F;
   private final float maxV = this.minV + 0.5F;

   public float getMinU() {
      return this.minU;
   }

   public float getMaxU() {
      return this.maxU;
   }

   public float getMinV() {
      return this.minV;
   }

   public float getMaxV() {
      return this.maxV;
   }
}
