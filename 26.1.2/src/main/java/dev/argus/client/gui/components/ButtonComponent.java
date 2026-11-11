package dev.argus.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.GuiDrawUtils;
import dev.argus.client.render.RenderUtils;
import java.awt.Color;
import java.util.function.Supplier;

public class ButtonComponent extends AbstractComponent {
   private final Supplier<String> labelSupplier;
   private boolean hoverOffsetEnabled;

   public ButtonComponent(Panel parentPanel, Supplier<String> labelSupplier) {
      super(parentPanel, 0);
      this.labelSupplier = labelSupplier;
   }

   public ButtonComponent(Panel parentPanel, String label) {
      super(parentPanel, 0);
      this.labelSupplier = () -> label;
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      String[] lines = this.getLabel().split("\n");

      for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
         float hoverOffset = this.hoverOffsetEnabled && this.isMouseOver(mouseX, mouseY) ? 1.0F : 0.0F;
         FontRenderer.INSTANCE
            .drawWithShadow(
               poseStack,
               lines[lineIndex],
               this.panel.getX() + 3,
               this.panel.getY() + this.yOffset + this.getTextYOffset() + lineIndex * FontRenderer.INSTANCE.getHeight() + 1.0F - hoverOffset,
               Color.white
            );
      }

      super.render(poseStack, mouseX, mouseY);
   }

   @Override
   public int getHeight() {
      return super.getHeight() + FontRenderer.INSTANCE.getHeight() * (this.getLabel().split("\n").length - 1) + 1;
   }

   protected void renderButtonBackground(PoseStack poseStack) {
      Color buttonColor = this.getUiModule().buttonSetting.getValue();
      buttonColor = ColorUtils.withAlpha(buttonColor, buttonColor.getAlpha() / 255.0F * 0.1F);
      if (this.getUiModule().elementGradientSetting.getValue()) {
         GuiDrawUtils.fillVerticalGradient(
            poseStack,
            this.panel.getX() + 1,
            this.panel.getY() + this.yOffset + 0.5F,
            this.panel.getWidth() - 1,
            this.getHeight() - 0.5F,
            buttonColor.hashCode(),
            ColorUtils.toArgb(buttonColor, 0)
         );
      } else {
         RenderUtils.INSTANCE
            .fillRect(
               poseStack,
               this.panel.getX() + 1,
               this.panel.getY() + this.yOffset + 0.5F,
               this.panel.getX() + this.panel.getWidth() - 1,
               this.panel.getY() + this.yOffset + this.getHeight() - 0.5F,
               this.getUiModule().buttonSetting.getValue()
            );
      }
   }

   public String getLabel() {
      return this.labelSupplier.get();
   }

   public void setHoverOffsetEnabled(boolean enabled) {
      this.hoverOffsetEnabled = enabled;
   }
}
