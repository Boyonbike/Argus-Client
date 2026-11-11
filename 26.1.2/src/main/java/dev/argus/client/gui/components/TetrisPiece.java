package dev.argus.client.gui.components;

import java.util.Arrays;
import java.util.Random;

public class TetrisPiece {
   private static final int[][][] SHAPES = new int[][][]{
      {{0, 0}, {0, 0}, {0, 0}, {0, 0}},
      {{0, -1}, {0, 0}, {-1, 0}, {-1, 1}},
      {{0, -1}, {0, 0}, {1, 0}, {1, 1}},
      {{0, -1}, {0, 0}, {0, 1}, {0, 2}},
      {{-1, 0}, {0, 0}, {1, 0}, {0, 1}},
      {{0, 0}, {1, 0}, {0, 1}, {1, 1}},
      {{-1, -1}, {0, -1}, {0, 0}, {0, 1}},
      {{1, -1}, {0, -1}, {0, 0}, {0, 1}}
   };
   private final int[][] coordinates = new int[4][2];
   private final Random random = new Random();
   private TetrisShape shape;

   public TetrisPiece() {
      this.setShape(TetrisShape.NoShape);
   }

   public void setShape(TetrisShape shape) {
      this.shape = shape;
      int[][] source = SHAPES[shape.ordinal()];

      for (int block = 0; block < this.coordinates.length; block++) {
         System.arraycopy(source[block], 0, this.coordinates[block], 0, 2);
      }
   }

   public void setX(int blockIndex, int x) {
      this.coordinates[blockIndex][0] = x;
   }

   public void setY(int blockIndex, int y) {
      this.coordinates[blockIndex][1] = y;
   }

   public int getX(int blockIndex) {
      return this.coordinates[blockIndex][0];
   }

   public int getY(int blockIndex) {
      return this.coordinates[blockIndex][1];
   }

   public TetrisShape getShape() {
      return this.shape;
   }

   public void setRandomShape() {
      TetrisShape[] shapes = TetrisShape.values();
      this.setShape(shapes[this.random.nextInt(1, shapes.length)]);
   }

   public int getMinimumY() {
      return Arrays.stream(this.coordinates).mapToInt(point -> point[1]).min().orElse(0);
   }

   public TetrisPiece rotateLeft() {
      return this.rotated(true);
   }

   public TetrisPiece rotateRight() {
      return this.rotated(false);
   }

   private TetrisPiece rotated(boolean left) {
      if (this.shape == TetrisShape.SquareShape) {
         return this;
      }

      TetrisPiece result = new TetrisPiece();
      result.shape = this.shape;

      for (int block = 0; block < this.coordinates.length; block++) {
         int x = this.getX(block);
         int y = this.getY(block);
         result.setX(block, left ? y : -y);
         result.setY(block, left ? -x : x);
      }

      return result;
   }
}
