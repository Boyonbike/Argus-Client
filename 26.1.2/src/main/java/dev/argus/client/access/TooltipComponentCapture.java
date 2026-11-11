package dev.argus.client.access;

import dev.argus.client.ClientAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class TooltipComponentCapture implements ClientAccess {
   private static boolean captureEnabled = false;
   private static List capturedComponents;

   private TooltipComponentCapture() {
      throw new AssertionError();
   }

   public static List captureComponents(GuiGraphicsExtractor graphics, List text, Optional tooltipComponent) {
      captureEnabled = true;
      Font font = ClientAccess.argusclientMinecraft.font;
      graphics.setTooltipForNextFrame(font, text, tooltipComponent, 0, 0);
      captureEnabled = false;
      return capturedComponents == null ? new ArrayList() : new ArrayList(capturedComponents);
   }

   public static boolean isCaptureEnabled() {
      return captureEnabled;
   }

   public static void setCapturedComponents(List components) {
      capturedComponents = components;
   }
}
