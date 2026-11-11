package dev.argus.client.gui.components;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.PrimitiveTopology;
import dev.argus.client.gui.panels.GamePanel;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.managers.RenderStateManager;
import dev.argus.client.render.ClientRenderTypes;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.MeshBuffer;
import dev.argus.client.render.MeshDrawContext;
import dev.argus.client.settings.options.GameType;
import dev.argus.client.settings.options.MinesweeperDifficulty;
import dev.argus.client.utils.math.FloatBounds;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.joml.Matrix4f;

public class MinesweeperGameComponent extends GridGameComponent {
   private static final Identifier SPRITE_ATLAS = Identifier.fromNamespaceAndPath("argusclient", "textures/game/atlas.png");
   private static final MeshBuffer SPRITE_BUFFER = MeshBuffer.create();
   private final MinesweeperDifficulty difficulty;
   private MinesweeperCell[][] board = new MinesweeperCell[this.gridRows - 1][this.gridColumns];
   private boolean boardGenerated = false;
   private boolean won = false;
   private long resetAtMillis = 0L;
   private int score = 0;
   private int flagCount = 0;

   public MinesweeperGameComponent(Panel panel, MinesweeperDifficulty difficulty) {
      super(panel, 0, difficulty.getColumns(), difficulty.getRows());
      this.difficulty = difficulty;
   }

   @Override
   protected void tickGame() {
      if (this.resetAtMillis >= 0L && System.currentTimeMillis() > this.resetAtMillis) {
         this.resetBoard();
      }

      if (!this.isResetPending() && this.boardGenerated) {
         this.score = Math.max(this.score - 1, 0);
      }
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      super.render(poseStack, mouseX, mouseY);
      this.renderHeader(poseStack);
      this.renderBoard(poseStack, mouseX, mouseY);
      if (this.won) {
         this.renderWinOverlay(poseStack);
      }
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      super.mouseClicked(mouseX, mouseY, button);
      if (this.isHeaderHovered(mouseX, mouseY)) {
         MinesweeperDifficulty nextDifficulty = MinesweeperDifficulty.values()[(this.difficulty.ordinal() + 1) % MinesweeperDifficulty.values().length];
         ((GamePanel)this.getPanel()).switchGame(GameType.MINESWEEPER, new MinesweeperGameComponent(this.getPanel(), nextDifficulty));
      } else if (!this.isResetPending()) {
         for (int column = 0; column < this.gridColumns; column++) {
            for (int displayRow = 1; displayRow < this.gridRows; displayRow++) {
               if (this.isCellHovered(column, displayRow, mouseX, mouseY)) {
                  if (!this.boardGenerated) {
                     this.generateBoard(displayRow - 1, column);
                  }

                  if (button == 0) {
                     this.openCell(displayRow - 1, column);
                  } else if (button == 1) {
                     this.toggleFlag(displayRow - 1, column);
                  }
               }
            }
         }

         if (button == 0 && this.hasWon() && !this.isResetPending()) {
            this.resetAtMillis = System.currentTimeMillis() + 3000L;
            this.won = true;
         }
      }
   }

   @Override
   protected int getTickDelayMillis() {
      return 1000;
   }

   @Override
   public int getHeight() {
      return 8 * this.gridRows;
   }

   public int getPreferredWidth() {
      return 8 * this.gridColumns;
   }

   private void renderHeader(PoseStack poseStack) {
      int displayedScore = this.score;
      FontRenderer.INSTANCE
         .drawWithShadow(poseStack, String.valueOf(displayedScore), this.getPanel().getX() + 1, this.getPanel().getY() + this.yOffset + 1.5F, 0.8F, Color.white);
      String remainingMines = String.valueOf(this.difficulty.getMineCount() - this.flagCount);
      float textWidth = FontRenderer.INSTANCE.getWidth(remainingMines) * 0.8F;
      FontRenderer.INSTANCE
         .drawWithShadow(
            poseStack,
            remainingMines,
            this.getPanel().getX() + this.getPanel().getWidth() - textWidth - this.getCellWidth(),
            this.getPanel().getY() + this.yOffset + 1.5F,
            0.8F,
            Color.white
         );
      String difficultyName = this.difficulty.getName();
      textWidth = FontRenderer.INSTANCE.getWidth(difficultyName) * 0.8F;
      FontRenderer.INSTANCE
         .drawWithShadow(
            poseStack,
            difficultyName,
            this.getPanel().getX() + this.getPanel().getWidth() / 2.0F - textWidth / 2.0F,
            this.getPanel().getY() + this.yOffset + 1.5F,
            0.8F,
            Color.white
         );
   }

   private void renderBoard(PoseStack poseStack, double mouseX, double mouseY) {
      RenderStateManager.addStates(RenderStateManager.THROUGH_WALLS);
      SPRITE_BUFFER.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
      this.drawCellSprite(poseStack, MinesweeperSprite.FLAG_NO_BG, this.gridColumns - 1, 0);

      for (int column = 0; column < this.gridColumns; column++) {
         for (int displayRow = 1; displayRow < this.gridRows; displayRow++) {
            int opacity = this.isCellHovered(column, displayRow, mouseX, mouseY) ? 210 : 255;
            if (this.won) {
               opacity = 150;
            }

            this.drawCellSprite(poseStack, this.board[displayRow - 1][column].getSprite(), column, displayRow, opacity);
         }
      }

      MeshDrawContext.setRenderType(ClientRenderTypes.image(SPRITE_ATLAS, false));
      SPRITE_BUFFER.draw();
      RenderStateManager.reset();
   }

   private void renderWinOverlay(PoseStack poseStack) {
      int textY = this.getHeight() / 2;
      String[] lines = new String[]{"You done it!", null};
      int displayedScore = this.score;
      lines[1] = "Score " + displayedScore;

      for (String line : lines) {
         float textX = this.getPanel().getWidth() / 2.0F - FontRenderer.INSTANCE.getWidth(line) / 2.0F;
         FontRenderer.INSTANCE.drawWithShadow(poseStack, line, this.getPanel().getX() + textX, this.getPanel().getY() + textY, Color.white);
         textY += FontRenderer.INSTANCE.getHeight() + 1;
      }
   }

   private void resetBoard() {
      this.board = new MinesweeperCell[this.gridRows - 1][this.gridColumns];

      for (int column = 0; column < this.gridColumns; column++) {
         for (int row = 0; row < this.gridRows - 1; row++) {
            this.board[row][column] = new MinesweeperCell(MinesweeperCellContent.NUMBER_0);
         }
      }

      this.boardGenerated = false;
      this.resetAtMillis = -1L;
      this.flagCount = 0;
      this.won = false;
      this.score = this.difficulty.getInitialScore();
   }

   private void generateBoard(int firstRow, int firstColumn) {
      this.board = new MinesweeperCell[this.gridRows - 1][this.gridColumns];
      HashSet<Integer> minePositions = new HashSet<>();
      int cellCount = (this.gridRows - 1) * this.gridColumns;

      while (minePositions.size() != this.difficulty.getMineCount()) {
         int position = ThreadLocalRandom.current().nextInt(cellCount);
         if (!this.isInFirstClickNeighborhood(firstRow, firstColumn, position)) {
            minePositions.add(position);
         }
      }

      for (int column = 0; column < this.gridColumns; column++) {
         for (int row = 0; row < this.gridRows - 1; row++) {
            int position = row * this.gridColumns + column;
            if (minePositions.contains(position)) {
               this.board[row][column] = new MinesweeperCell(MinesweeperCellContent.BOMB);
            } else {
               this.board[row][column] = new MinesweeperCell(MinesweeperCellContent.NUMBER_0);
            }

            this.board[row][column].state = MinesweeperCellState.COVERED;
         }
      }

      for (int numberRow = 0; numberRow < this.gridRows - 1; numberRow++) {
         for (int numberColumn = 0; numberColumn < this.gridColumns; numberColumn++) {
            if (this.board[numberRow][numberColumn].content != MinesweeperCellContent.BOMB) {
               int adjacentMines = this.countAdjacentMines(numberRow, numberColumn);
               this.board[numberRow][numberColumn].content = MinesweeperCellContent.fromAdjacentMines(adjacentMines);
            }
         }
      }

      this.boardGenerated = true;
   }

   void toggleFlag(int row, int column) {
      MinesweeperCell cell = this.board[row][column];
      if (cell.state != MinesweeperCellState.OPEN) {
         if (cell.state == MinesweeperCellState.FLAGGED) {
            cell.state = MinesweeperCellState.COVERED;
            this.flagCount--;
         } else if (cell.state == MinesweeperCellState.COVERED && this.flagCount < this.difficulty.getMineCount()) {
            cell.state = MinesweeperCellState.FLAGGED;
            this.flagCount++;
         }
      }
   }

   void openCell(int row, int column) {
      MinesweeperCell cell = this.board[row][column];
      if (cell.state == MinesweeperCellState.OPEN) {
         this.openNeighbors(row, column);
      } else if (cell.state == MinesweeperCellState.COVERED) {
         if (cell.content == MinesweeperCellContent.BOMB) {
            this.loseGame(cell);
         } else {
            if (cell.content == MinesweeperCellContent.NUMBER_0) {
               this.openEmptyRegion(row, column);
            }

            cell.state = MinesweeperCellState.OPEN;
         }
      }
   }

   void openNeighbors(int row, int column) {
      int rowCount = this.board.length;
      int columnCount = this.board[0].length;

      for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
         for (int columnOffset = -1; columnOffset <= 1; columnOffset++) {
            int neighborRow = row + rowOffset;
            int neighborColumn = column + columnOffset;
            if (neighborRow >= 0 && neighborRow < rowCount && neighborColumn >= 0 && neighborColumn < columnCount) {
               MinesweeperCell neighbor = this.board[neighborRow][neighborColumn];
               if (neighbor.state == MinesweeperCellState.COVERED) {
                  this.openCell(neighborRow, neighborColumn);
               }
            }
         }
      }
   }

   void loseGame(MinesweeperCell explodedCell) {
      for (MinesweeperCell[] row : this.board) {
         for (MinesweeperCell cell : row) {
            if (cell.content == MinesweeperCellContent.BOMB) {
               cell.state = MinesweeperCellState.OPEN;
            }
         }
      }

      explodedCell.state = MinesweeperCellState.BLOWN;
      this.resetAtMillis = System.currentTimeMillis() + 2000L;
      this.score = 0;
   }

   private void openEmptyRegion(int row, int column) {
      int rowCount = this.board.length;
      int columnCount = this.board[0].length;
      ArrayDeque<int[]> pendingCells = new ArrayDeque<>();
      int[] coordinates = new int[]{row, column};
      pendingCells.add(coordinates);

      while (!pendingCells.isEmpty()) {
         int[] position = pendingCells.remove();
         int currentRow = position[0];
         int currentColumn = position[1];
         if (this.board[currentRow][currentColumn].state != MinesweeperCellState.OPEN) {
            this.board[currentRow][currentColumn].state = MinesweeperCellState.OPEN;
            if (this.board[currentRow][currentColumn].content == MinesweeperCellContent.NUMBER_0) {
               for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
                  for (int columnOffset = -1; columnOffset <= 1; columnOffset++) {
                     if (rowOffset != 0 || columnOffset != 0) {
                        int neighborRow = currentRow + rowOffset;
                        int neighborColumn = currentColumn + columnOffset;
                        if (neighborRow >= 0
                           && neighborRow < rowCount
                           && neighborColumn >= 0
                           && neighborColumn < columnCount
                           && this.board[neighborRow][neighborColumn].state != MinesweeperCellState.FLAGGED
                           && this.board[neighborRow][neighborColumn].state != MinesweeperCellState.OPEN) {
                           coordinates = new int[]{neighborRow, neighborColumn};
                           pendingCells.add(coordinates);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private int countAdjacentMines(int row, int column) {
      int mineCount = 0;

      for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
         for (int columnOffset = -1; columnOffset <= 1; columnOffset++) {
            if (rowOffset != 0 || columnOffset != 0) {
               int neighborRow = row + rowOffset;
               int neighborColumn = column + columnOffset;
               if (neighborRow >= 0
                  && neighborRow < this.gridRows - 1
                  && neighborColumn >= 0
                  && neighborColumn < this.gridColumns
                  && this.board[neighborRow][neighborColumn].content == MinesweeperCellContent.BOMB) {
                  mineCount++;
               }
            }
         }
      }

      return mineCount;
   }

   private boolean isInFirstClickNeighborhood(int firstRow, int firstColumn, int position) {
      for (int columnOffset = -1; columnOffset <= 1; columnOffset++) {
         for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
            int neighborPosition = (firstRow + rowOffset) * this.gridColumns + firstColumn + columnOffset;
            if (neighborPosition == position) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean isResetPending() {
      return this.resetAtMillis != -1L;
   }

   private boolean hasWon() {
      for (MinesweeperCell[] row : this.board) {
         for (MinesweeperCell cell : row) {
            if (cell.state == MinesweeperCellState.COVERED && cell.content != MinesweeperCellContent.BOMB) {
               return false;
            }
         }
      }

      return true;
   }

   private void drawCellSprite(PoseStack poseStack, MinesweeperSprite sprite, int column, int displayRow) {
      this.drawCellSprite(poseStack, sprite, column, displayRow, 255);
   }

   private void drawCellSprite(PoseStack poseStack, MinesweeperSprite sprite, int column, int displayRow, int opacity) {
      FloatBounds bounds = this.getCellBounds(column, displayRow);
      float left = bounds.minX();
      float top = bounds.minY();
      float right = bounds.maxX();
      float bottom = bounds.maxY();
      Matrix4f matrix = poseStack.last().pose();
      int color = ARGB.color(opacity, -1);
      SPRITE_BUFFER.addVertex(matrix, left, bottom, 0.0F).setUv(sprite.getMinU(), sprite.getMaxV()).setColor(color);
      SPRITE_BUFFER.addVertex(matrix, right, bottom, 0.0F).setUv(sprite.getMaxU(), sprite.getMaxV()).setColor(color);
      SPRITE_BUFFER.addVertex(matrix, right, top, 0.0F).setUv(sprite.getMaxU(), sprite.getMinV()).setColor(color);
      SPRITE_BUFFER.addVertex(matrix, left, top, 0.0F).setUv(sprite.getMinU(), sprite.getMinV()).setColor(color);
   }

   private boolean isCellHovered(int column, int displayRow, double mouseX, double mouseY) {
      FloatBounds bounds = this.getCellBounds(column, displayRow);
      return mouseX >= bounds.minX() && mouseX < bounds.maxX() && mouseY >= bounds.minY() && mouseY < bounds.maxY();
   }

   private boolean isHeaderHovered(double mouseX, double mouseY) {
      return mouseX > this.getPanel().getX()
         && mouseX < this.getPanel().getX() + this.getPanel().getWidth()
         && mouseY > this.getPanel().getY() + this.yOffset
         && mouseY < this.getPanel().getY() + this.yOffset + this.getCellWidth();
   }

   private FloatBounds getCellBounds(int column, int displayRow) {
      return new FloatBounds(
         this.getPanel().getX() + column * this.getCellWidth(),
         this.getPanel().getY() + this.yOffset + displayRow * this.getCellHeight(),
         this.getPanel().getX() + column * this.getCellWidth() + this.getCellWidth(),
         this.getPanel().getY() + this.yOffset + displayRow * this.getCellHeight() + this.getCellHeight()
      );
   }
}
