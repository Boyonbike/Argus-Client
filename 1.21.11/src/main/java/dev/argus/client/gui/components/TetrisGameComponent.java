package dev.argus.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import java.awt.Color;

public class TetrisGameComponent extends GridGameComponent {
   private boolean waitingAfterLineClear = false;
   private boolean gameOver = false;
   private int clearedLines = 0;
   private int pieceX = 0;
   private int pieceY = 0;
   private TetrisPiece currentPiece;
   private TetrisShape[] board;

   public TetrisGameComponent(Panel panel, int yOffset) {
      super(panel, yOffset, 22, 22);
   }

   @Override
   protected void tickGame() {
      if (this.currentPiece == null) {
         this.resetGame();
      }

      if (this.waitingAfterLineClear) {
         this.waitingAfterLineClear = false;
         this.spawnPiece();
      } else {
         this.stepDown();
      }
   }

   @Override
   protected int getTickDelayMillis() {
      return 500;
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      super.render(poseStack, mouseX, mouseY);

      for (int renderRow = 0; renderRow < this.gridRows; renderRow++) {
         for (int column = 0; column < this.gridColumns; column++) {
            TetrisShape shape = this.getBoardShape(column, this.gridRows - renderRow - 1);
            if (shape != TetrisShape.NoShape) {
               this.drawCell(poseStack, column, renderRow, shape.getColor());
            }
         }
      }

      if (this.currentPiece.getShape() != TetrisShape.NoShape) {
         TetrisPiece ghostPiece = new TetrisPiece();
         ghostPiece.setShape(this.currentPiece.getShape());

         for (int blockIndex = 0; blockIndex < 4; blockIndex++) {
            ghostPiece.setX(blockIndex, this.currentPiece.getX(blockIndex));
            ghostPiece.setY(blockIndex, this.currentPiece.getY(blockIndex));
            int blockX = this.pieceX + this.currentPiece.getX(blockIndex);
            int blockY = this.pieceY - this.currentPiece.getY(blockIndex);
            Color pieceColor = this.currentPiece.getShape().getColor();
            this.drawCell(poseStack, blockX, this.gridRows - blockY - 1, pieceColor);
         }

         int ghostY = this.pieceY;

         while (ghostY > 0 && this.canPlacePiece(ghostPiece, this.pieceX, ghostY - 1)) {
            ghostY--;
         }

         for (int ghostBlockIndex = 0; ghostBlockIndex < 4; ghostBlockIndex++) {
            int ghostBlockX = this.pieceX + ghostPiece.getX(ghostBlockIndex);
            int ghostBlockY = ghostY - ghostPiece.getY(ghostBlockIndex);
            Color ghostColor = this.currentPiece.getShape().getColor();
            this.drawCell(poseStack, ghostBlockX, this.gridRows - ghostBlockY - 1, ColorUtils.withAlpha(ghostColor, 50));
         }
      }

      if (this.gameOver) {
         int score = this.clearedLines;
         String gameOverText = "Score " + score + ". Press R.";
         FontRenderer.INSTANCE
            .drawWithShadow(
               poseStack,
               gameOverText,
               (float)(this.getPanel().getX() + this.getPanel().getWidth() * 0.5 - FontRenderer.INSTANCE.getWidth(gameOverText) * 0.5),
               (float)(this.getPanel().getY() + this.yOffset + this.getHeight() * 0.5),
               Color.white
            );
      } else {
         FontRenderer.INSTANCE
            .drawWithShadow(
               poseStack, String.valueOf(this.clearedLines), this.getPanel().getX() + 1.5F, this.getPanel().getY() + this.yOffset + 1, 0.75F, Color.white
            );
      }
   }

   @Override
   public void keyPressed(int keyCode) {
      switch (keyCode) {
         case 32:
            this.hardDrop();
            break;
         case 68:
            this.stepDown();
            break;
         case 82:
            this.resetGame();
            break;
         case 262:
            this.tryMovePiece(this.currentPiece, this.pieceX + 1, this.pieceY);
            break;
         case 263:
            this.tryMovePiece(this.currentPiece, this.pieceX - 1, this.pieceY);
            break;
         case 264:
            this.tryMovePiece(this.currentPiece.rotateRight(), this.pieceX, this.pieceY);
            break;
         case 265:
            this.tryMovePiece(this.currentPiece.rotateLeft(), this.pieceX, this.pieceY);
      }
   }

   private void resetGame() {
      this.gameOver = false;
      this.currentPiece = new TetrisPiece();
      this.board = new TetrisShape[this.gridColumns * this.gridRows];
      this.clearBoard();
      this.spawnPiece();
   }

   private TetrisShape getBoardShape(int column, int row) {
      return this.board[row * this.gridColumns + column];
   }

   private void hardDrop() {
      int dropY = this.pieceY;

      while (dropY > 0 && this.tryMovePiece(this.currentPiece, this.pieceX, dropY - 1)) {
         dropY--;
      }

      this.lockPiece();
   }

   private void stepDown() {
      if (!this.tryMovePiece(this.currentPiece, this.pieceX, this.pieceY - 1)) {
         this.lockPiece();
      }
   }

   private void clearBoard() {
      this.clearedLines = 0;

      for (int cellIndex = 0; cellIndex < this.gridRows * this.gridColumns; cellIndex++) {
         this.board[cellIndex] = TetrisShape.NoShape;
      }
   }

   private void lockPiece() {
      for (int blockIndex = 0; blockIndex < 4; blockIndex++) {
         int blockX = this.pieceX + this.currentPiece.getX(blockIndex);
         int blockY = this.pieceY - this.currentPiece.getY(blockIndex);
         this.board[blockY * this.gridColumns + blockX] = this.currentPiece.getShape();
      }

      this.removeFullLines();
      if (!this.waitingAfterLineClear) {
         this.spawnPiece();
      }
   }

   private void spawnPiece() {
      this.currentPiece.setRandomShape();
      this.pieceX = this.gridColumns / 2 + 1;
      this.pieceY = this.gridRows - 1 + this.currentPiece.getMinimumY();
      if (!this.tryMovePiece(this.currentPiece, this.pieceX, this.pieceY)) {
         this.currentPiece.setShape(TetrisShape.NoShape);
         this.gameOver = true;
      }
   }

   private boolean canPlacePiece(TetrisPiece piece, int targetX, int targetY) {
      for (int blockIndex = 0; blockIndex < 4; blockIndex++) {
         int blockX = targetX + piece.getX(blockIndex);
         int blockY = targetY - piece.getY(blockIndex);
         if (blockX < 0 || blockX >= this.gridColumns || blockY < 0 || blockY >= this.gridRows) {
            return false;
         }

         if (this.getBoardShape(blockX, blockY) != TetrisShape.NoShape) {
            return false;
         }
      }

      return true;
   }

   private boolean tryMovePiece(TetrisPiece piece, int targetX, int targetY) {
      if (!this.canPlacePiece(piece, targetX, targetY)) {
         return false;
      }

      this.currentPiece = piece;
      this.pieceX = targetX;
      this.pieceY = targetY;
      return true;
   }

   private void removeFullLines() {
      int removedLines = 0;

      for (int row = this.gridRows - 1; row >= 0; row--) {
         boolean fullRow = true;

         for (int column = 0; column < this.gridColumns; column++) {
            if (this.getBoardShape(column, row) == TetrisShape.NoShape) {
               fullRow = false;
               break;
            }
         }

         if (fullRow) {
            removedLines++;

            for (int shiftRow = row; shiftRow < this.gridRows - 1; shiftRow++) {
               for (int shiftColumn = 0; shiftColumn < this.gridColumns; shiftColumn++) {
                  this.board[shiftRow * this.gridColumns + shiftColumn] = this.getBoardShape(shiftColumn, shiftRow + 1);
               }
            }
         }
      }

      if (removedLines > 0) {
         this.clearedLines += removedLines;
         this.waitingAfterLineClear = true;
         this.currentPiece.setShape(TetrisShape.NoShape);
      }
   }

   @Override
   public int getHeight() {
      return this.getPanel().getWidth();
   }
}
