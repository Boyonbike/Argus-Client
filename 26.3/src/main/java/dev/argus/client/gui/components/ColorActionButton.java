package dev.argus.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.render.FontRenderer;

public abstract class ColorActionButton extends AbstractComponent {
   protected final ColorSettingComponent editor;
   protected final String label;
   protected Runnable action;

   public abstract float getLabelOffsetX();

   public String getName() {
      return this.label;
   }

   public ColorActionButton(int yOffset, ColorSettingComponent editor, String label, Runnable action) {
      super(editor.getPanel(), yOffset);
      this.editor = editor;
      this.label = label;
      this.action = action;
   }

   @Override
   public void render(PoseStack poseStack, double mouseX, double mouseY) {
      if (!this.editor.isUnavailable()) {
         super.render(poseStack, mouseX, mouseY);
         FontRenderer.INSTANCE
            .drawWithShadow(
               poseStack,
               this.getName(),
               this.panel.getX() + this.getLabelOffsetX(),
               this.panel.getY() + this.yOffset + 0.0F + this.getTextYOffset() - this.getHoverAnimationValue(),
               this.getUiModule().textColorSetting.getValue()
            );
      }
   }

   @Override
   public void mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.editor.isUnavailable()) {
         super.mouseClicked(mouseX, mouseY, button);
         if (this.isMouseOver(mouseX, mouseY) && button == 0) {
            this.action.run();
         }
      }
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      return mouseX > this.panel.getX() + this.getLabelOffsetX()
         && mouseX < this.panel.getX() + this.getLabelOffsetX() + FontRenderer.INSTANCE.getWidth(this.getName())
         && mouseY > this.panel.getY() + this.yOffset + 0.0F
         && mouseY < this.panel.getY() + this.yOffset + 0.0F + FontRenderer.INSTANCE.getHeight() + this.getTextYOffset() * 2.0F;
   }
}
