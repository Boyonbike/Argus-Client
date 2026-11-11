package dev.argus.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.gui.screens.ClientScreen;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public abstract class AbstractItemListComponent<T> extends AbstractComponent {
   protected final List<T> entries;
   protected final Predicate<T> filter;
   private T selection = (T)null;
   private int scrollOffset;

   public AbstractItemListComponent(Panel panel, Predicate<T> filter, int initialCapacity) {
      super(panel);
      this.entries = new ArrayList<>(initialCapacity);
      this.filter = filter;
   }

   public abstract String getEntryLabel(T var1);

   public abstract ItemStack getEntryIcon(T var1);

   @Override
   public void render(PoseStack stack, double mouseX, double mouseY) {
      super.render(stack, mouseX, mouseY);
      stack.pushPose();
      stack.translate(this.panel.getX() + 1, this.panel.getY() + this.yOffset, 0.0F);
      this.clampScrollOffset();
      GuiDrawUtils.flush();

      for (int row = 0; row < 8 && this.scrollOffset + row < this.entries.size(); row++) {
         T entry = this.entries.get(this.scrollOffset + row);
         Color textColor = Color.white;
         String label = this.getEntryLabel(entry);
         int hoverTextOffset = this.isRowHovered(mouseX, mouseY, row) ? 0 : 1;
         GuiDrawUtils.drawItem(stack, this.getEntryIcon(entry), 1.0F, 16 * row, 0.0F);
         FontRenderer.INSTANCE
            .drawWithShadow(stack, this.truncateLabel(label), 20.0F, (16 - FontRenderer.INSTANCE.getHeight()) / 2.0F + hoverTextOffset + 16 * row, textColor);
      }

      stack.popPose();
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      super.mouseClicked(mouseX, mouseY, button);
      if (this.isMouseOver(mouseX, mouseY) && !this.entries.isEmpty()) {
         int relativeMouseY = (int)(mouseY - this.panel.getY() - this.yOffset);
         this.selection = this.entries.get(Mth.clamp(this.scrollOffset + relativeMouseY / 16, 0, this.entries.size() - 1));
      }
   }

   @Override
   public void mouseScrolled(double mouseX, double mouseY, double scrollAmount) {
      super.mouseScrolled(mouseX, mouseY, scrollAmount);
      if (this.isMouseOver(mouseX, mouseY)) {
         this.scrollOffset -= (int)scrollAmount;
         Objects.requireNonNull((ClientScreen)ClientAccess.argusclientMinecraft.screen);
      }
   }

   private boolean isRowHovered(double mouseX, double mouseY, int row) {
      int relativeMouseY = (int)(mouseY - this.panel.getY() - this.yOffset);
      return this.isMouseOver(mouseX, mouseY) && relativeMouseY / 16 == row;
   }

   private void clampScrollOffset() {
      this.scrollOffset = Mth.clamp(this.scrollOffset, 0, Math.max(this.entries.size() - 8, 0));
   }

   private String truncateLabel(String label) {
      if (FontRenderer.INSTANCE.getWidth(label) + 20.0F < this.getPanel().getWidth()) {
         return label;
      }

      for (int endIndex = 0; endIndex < label.length(); endIndex++) {
         FontRenderer font = FontRenderer.INSTANCE;
         String prefix = label.substring(0, endIndex).trim();
         if (font.getWidth(prefix + "...") + 20.0F >= this.getPanel().getWidth()) {
            return label.substring(0, endIndex - 1).trim() + "...";
         }
      }

      return label;
   }

   public T getSelectedEntry() {
      return this.selection;
   }

   @Override
   public int getBaseHeight() {
      return 128;
   }
}
