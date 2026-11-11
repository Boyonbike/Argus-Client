package dev.argus.client.utils;

import net.minecraft.client.input.MouseButtonEvent;

/** Converts a 26.3 mouse event to the GLFW button space Argus stores and compares. */
public final class InputButtons {
   public static int glfw(MouseButtonEvent mouseEvent) {
      return LegacyMouseButtons.toGlfw(mouseEvent.button());
   }

   private InputButtons() {
   }
}
