package dev.argus.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shares the GUI extractor captured during Gui.extractRenderState with GameRenderer's HUD hooks. */
public final class GuiCapture {
   public static GuiGraphicsExtractor hudGraphics;

   private GuiCapture() {
   }
}
