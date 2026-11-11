package dev.argus.client.gui.components;

import dev.argus.client.utils.KeyUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.utils.collections.IntPair;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class SnakeGameComponent extends GridGameComponent {
   private SnakeDirection direction = SnakeDirection.UP;
   private SnakeDirection pendingDirection = null;
   private final List<IntPair> segments = new ArrayList<>();
   private IntPair food;
   private int score;
   private boolean gameOver;

   public SnakeGameComponent(Panel panel, int yOffset) {
      super(panel, yOffset);
      this.resetGame();
   }

   @Override
   protected void tickGame() {
      if (!this.gameOver) {
         if (this.pendingDirection != null) {
            this.direction = this.pendingDirection;
            this.pendingDirection = null;
         }

         if (!this.segments.isEmpty()) {
            IntPair head = this.segments.getFirst();
            IntPair nextHead = head.add(this.direction.getOffset());
            if (nextHead.first() > this.gridColumns - 1) {
               nextHead = new IntPair(0, nextHead.second());
            } else if (nextHead.first() < 0) {
               nextHead = new IntPair(this.gridColumns - 1, nextHead.second());
            } else if (nextHead.second() > this.gridRows - 1) {
               nextHead = new IntPair(nextHead.first(), 0);
            } else if (nextHead.second() < 0) {
               nextHead = new IntPair(nextHead.first(), this.gridRows - 1);
            }

            if (this.segments.contains(nextHead)) {
               this.gameOver = true;
            } else {
               this.segments.addFirst(nextHead);
               if (this.segments.getFirst().equals(this.food)) {
                  this.food = this.findFoodPosition();
                  this.score++;
               } else {
                  this.segments.removeLast();
               }
            }
         }
      }
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      super.render(poseStack, mouseX, mouseY);
      this.drawCell(poseStack, this.food.first(), this.food.second(), Color.red);
      boolean headRendered = false;

      for (IntPair segment : this.segments) {
         if (!headRendered) {
            this.drawCell(poseStack, segment.first(), segment.second(), this.gameOver ? Color.GRAY.brighter() : Color.GREEN);
            headRendered = true;
         } else {
            this.drawCell(poseStack, segment.first(), segment.second(), this.gameOver ? Color.GRAY : new Color(0, 150, 0));
         }
      }

      if (this.gameOver) {
         int displayedScore = this.score;
         String gameOverText = "Score " + displayedScore + ". Press R.";
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
            .drawWithShadow(poseStack, String.valueOf(this.score), this.getPanel().getX() + 1.5F, this.getPanel().getY() + this.yOffset + 1, 0.75F, Color.white);
      }
   }

   @Override
   public void keyPressed(int keyCode) {
      SnakeDirection requestedDirection = null;
      switch (keyCode) {
         case 82:
            this.resetGame();
            break;
         case 262:
            requestedDirection = SnakeDirection.RIGHT;
            break;
         case 263:
            requestedDirection = SnakeDirection.LEFT;
            break;
         case 264:
            requestedDirection = SnakeDirection.DOWN;
            break;
         case 265:
            requestedDirection = SnakeDirection.UP;
      }

      if (requestedDirection != null && requestedDirection != this.direction.opposite()) {
         this.pendingDirection = requestedDirection;
      }
   }

   @Override
   protected int getTickDelayMillis() {
      return KeyUtils.isPhysicalKeyDown(32) ? 200 : 500;
   }

   @Override
   public int getHeight() {
      return this.getPanel().getWidth();
   }

   private void resetGame() {
      this.segments.clear();
      this.segments.add(new IntPair(11, 11));
      this.food = this.findFoodPosition();
      this.score = 0;
      this.gameOver = false;
   }

   private IntPair findFoodPosition() {
      IntPair candidate = this.segments.getFirst();

      while (this.segments.contains(candidate)) {
         candidate = new IntPair((int)(Math.random() * this.gridColumns), (int)(Math.random() * this.gridRows));
      }

      return candidate;
   }
}
