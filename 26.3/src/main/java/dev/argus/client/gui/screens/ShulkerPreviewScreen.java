package dev.argus.client.gui.screens;

import dev.argus.client.utils.InputKeys;
import dev.argus.client.ClientAccess;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ShulkerBoxMenu;

public class ShulkerPreviewScreen extends ShulkerBoxScreen implements ClientAccess {
   private static boolean renderingPreview;
   private final Screen parentScreen;

   public ShulkerPreviewScreen(ShulkerBoxMenu menu, Inventory inventory, Component title, Screen parentScreen) {
      super(menu, inventory, title);
      this.parentScreen = parentScreen;
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      renderingPreview = true;
      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
      renderingPreview = false;
   }

   public boolean rejectMouseClick(double mouseX, double mouseY, int button) {
      return false;
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      int keyCode = InputKeys.glfw(event);
      if (keyCode == 256) {
         Screen capturedParent = this.parentScreen;
         ClientAccess.argusclientMinecraft.gui.setScreen(capturedParent);
         return true;
      } else {
         return super.keyPressed(event);
      }
   }

   public static boolean isRenderingPreview() {
      return renderingPreview;
   }
}
