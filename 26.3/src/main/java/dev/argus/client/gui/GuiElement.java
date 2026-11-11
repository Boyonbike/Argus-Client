package dev.argus.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.modules.client.UIModule;
import dev.argus.client.render.FontRenderer;

public interface GuiElement {
   default UIModule getUiModule() {
      return UIModule.INSTANCE;
   }

   default int getBaseHeight() {
      return UIModule.INSTANCE.styleSetting.getValue().getHeightPadding() + FontRenderer.INSTANCE.getHeight();
   }

   default int getYOffset() {
      return 0;
   }

   default void setYOffset(int yOffset) {
   }

   default float getTextYOffset() {
      return (this.getBaseHeight() - FontRenderer.INSTANCE.getHeight() + 1) / 2.0F;
   }

   default int getHeight() {
      return this.getBaseHeight();
   }

   default void keyPressed(int keyCode) {
   }

   default void init() {
   }

   default void render(PoseStack stack, double mouseX, double mouseY) {
   }

   default void mouseClicked(double mouseX, double mouseY, int button) {
   }

   default boolean isMouseOver(double mouseX, double mouseY) {
      return false;
   }

   default void update(double mouseX, double mouseY) {
   }

   default void mouseScrolled(double mouseX, double mouseY, double amount) {
   }

   default void mouseReleased(double mouseX, double mouseY, int button) {
   }

   default void charTyped(char character) {
   }
}
