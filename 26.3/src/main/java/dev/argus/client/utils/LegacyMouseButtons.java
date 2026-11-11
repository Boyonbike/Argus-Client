package dev.argus.client.utils;

/** Translates between Argus's stored 0-based mouse buttons and SDL's 1-based buttons. */
public final class LegacyMouseButtons {
   public static int toSdl(int glfwButton) {
      if (glfwButton < 0) {
         return -1;
      }
      return switch (glfwButton) {
         case 0 -> 1;   // left
         case 1 -> 3;   // right
         case 2 -> 2;   // middle
         default -> glfwButton + 1;
      };
   }

   public static int toGlfw(int sdlButton) {
      if (sdlButton <= 0) {
         return -1;
      }
      return switch (sdlButton) {
         case 1 -> 0;   // left
         case 3 -> 1;   // right
         case 2 -> 2;   // middle
         default -> sdlButton - 1;
      };
   }

   private LegacyMouseButtons() {
   }
}
