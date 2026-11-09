package dev.argus.client.utils.collections;

public record IntPair(int first, int second) {
   public double squaredDistanceTo(IntPair other) {
      double firstDifference = this.first - other.first;
      double firstSquared = Math.pow(firstDifference, 2.0);
      double secondDifference = this.second - other.second;
      return firstSquared + Math.pow(secondDifference, 2.0);
   }

   public double distanceTo(IntPair other) {
      return Math.sqrt(this.squaredDistanceTo(other));
   }

   public IntPair add(IntPair other) {
      return new IntPair(this.first + other.first, this.second + other.second);
   }
}
