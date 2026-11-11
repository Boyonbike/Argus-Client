package dev.argus.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.utils.ElapsedTimer;
import java.awt.Color;

public abstract class GridGameComponent extends AbstractComponent {
   protected final int gridColumns;
   protected final int gridRows;
   private final ElapsedTimer tickTimer = new ElapsedTimer();

   public GridGameComponent(Panel panel, int yOffset, int gridColumns, int gridRows) {
      super(panel, yOffset);
      this.gridColumns = gridColumns;
      this.gridRows = gridRows;
   }

   public GridGameComponent(Panel panel, int yOffset) {
      this(panel, yOffset, 22, 22);
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      super.render(poseStack, mouseX, mouseY);
      if (this.tickTimer.hasElapsed(this.getTickDelayMillis())) {
         this.tickGame();
         this.tickTimer.reset();
      }
   }

   protected abstract void tickGame();

   protected abstract int getTickDelayMillis();

   public void drawCell(PoseStack poseStack, int column, int row, Color color) {
      GuiDrawUtils.fillRect(
         poseStack,
         this.getPanel().getX() + column * this.getCellWidth(),
         this.getPanel().getY() + this.yOffset + row * this.getCellHeight(),
         this.getPanel().getX() + column * this.getCellWidth() + this.getCellWidth(),
         this.getPanel().getY() + this.yOffset + row * this.getCellHeight() + this.getCellHeight(),
         color
      );
   }

   public int getCellWidth() {
      return this.getPanel().getWidth() / this.gridColumns;
   }

   public int getCellHeight() {
      return this.getHeight() / this.gridRows;
   }
}
