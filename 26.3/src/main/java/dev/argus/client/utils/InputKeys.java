package dev.argus.client.utils;

import net.minecraft.client.input.KeyEvent;

/** Converts a 26.3 key event to the GLFW key space Argus stores and compares. */
public final class InputKeys {
   public static int glfw(KeyEvent keyEvent) {
      return LegacyKeyCodes.fromScancode(keyEvent.key());
   }

   private InputKeys() {
   }
}
