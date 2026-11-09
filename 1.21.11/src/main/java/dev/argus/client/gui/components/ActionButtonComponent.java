package dev.argus.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.render.GuiDrawUtils;
import java.util.function.Supplier;

public class ActionButtonComponent extends ButtonComponent {
   private final Runnable action;

   public ActionButtonComponent(Panel parentPanel, String label, Runnable action) {
      this(parentPanel, () -> label, action);
   }

   public ActionButtonComponent(Panel parentPanel, Supplier<String> labelSupplier, Runnable action) {
      super(parentPanel, labelSupplier);
      this.action = action;
      this.setHoverOffsetEnabled(true);
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      if (this.isHighlighted()) {
         GuiDrawUtils.fillRect(
            poseStack,
            this.panel.getX() + 1,
            this.panel.getY() + this.yOffset + 0.5F,
            this.panel.getX() + this.panel.getWidth() - 1,
            this.panel.getY() + this.yOffset + this.getHeight() - 0.5F,
            this.getUiModule().colorSetting.getValue()
         );
      }

      super.render(poseStack, mouseX, mouseY);
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      super.mouseClicked(mouseX, mouseY, button);
      if (this.isMouseOver(mouseX, mouseY) && button == 0) {
         this.action.run();
      }
   }

   public boolean isHighlighted() {
      return true;
   }
}
