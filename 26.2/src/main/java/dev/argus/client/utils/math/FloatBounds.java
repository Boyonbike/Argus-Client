package dev.argus.client.utils.math;

public record FloatBounds(float minX, float minY, float maxX, float maxY) {
   public float getWidth() {
      return this.maxX - this.minX;
   }

   public FloatBounds expand(float horizontalAmount, float verticalAmount) {
      return new FloatBounds(this.minX() - horizontalAmount, this.minY() - verticalAmount, this.maxX() + horizontalAmount, this.maxY() + verticalAmount);
   }

   public float getHeight() {
      return this.maxY - this.minY;
   }

   public FloatBounds union(FloatBounds... others) {
      float unionMinX = this.minX();
      float unionMaxX = this.maxX();
      float unionMinY = this.minY();
      float unionMaxY = this.maxY();

      for (FloatBounds other : others) {
         float otherMinX = other.minX();
         unionMinX = Math.min(unionMinX, otherMinX);
         float otherMinY = other.minY();
         unionMinY = Math.min(unionMinY, otherMinY);
         float otherMaxX = other.maxX();
         unionMaxX = Math.max(unionMaxX, otherMaxX);
         float otherMaxY = other.maxY();
         unionMaxY = Math.max(unionMaxY, otherMaxY);
      }

      return new FloatBounds(unionMinX, unionMinY, unionMaxX, unionMaxY);
   }

   public boolean intersects(FloatBounds other) {
      return (this.maxX < this.minX || this.maxX > other.minX())
         && (this.maxY < this.minY || this.maxY > other.minY())
         && (other.maxX() < other.minX() || other.maxX() > this.minX)
         && (other.maxY() < other.minY() || other.maxY() > this.minY);
   }

   public static FloatBounds fromPositionAndSize(float x, float y, float width, float height) {
      return new FloatBounds(x, y, x + width, y + height);
   }

   public boolean contains(double x, double y) {
      return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY;
   }

   public FloatBounds expand(float amount) {
      return this.expand(amount, amount);
   }
}
