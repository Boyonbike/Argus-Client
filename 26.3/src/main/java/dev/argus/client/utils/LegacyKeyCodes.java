package dev.argus.client.utils;

/**
 * Translates the GLFW key numbers Argus stores in its config/presets to the SDL
 * scancodes Minecraft 26.3 uses for key state and key events.
 *
 * <p>Stored binds stay in the GLFW space so 26.2 configs keep working unchanged;
 * the translation happens only at the key-state and capture boundaries.</p>
 */
public final class LegacyKeyCodes {
   public static final int UNMAPPED = -1;

   private static final int[] TO_SDL = new int[349];
   private static final int[] TO_GLFW = new int[256];

   static {
      java.util.Arrays.fill(TO_SDL, UNMAPPED);
      java.util.Arrays.fill(TO_GLFW, UNMAPPED);
      map(32, 44);   // Space
      map(39, 52);   // Apostrophe
      map(44, 54);   // Comma
      map(45, 45);   // Minus
      map(46, 55);   // Period
      map(47, 56);   // Slash
      map(48, 39);   // 0
      map(49, 30);   // 1
      map(50, 31);
      map(51, 32);
      map(52, 33);
      map(53, 34);
      map(54, 35);
      map(55, 36);
      map(56, 37);
      map(57, 38);   // 9
      map(59, 51);   // Semicolon
      map(61, 46);   // Equal
      for (int i = 0; i < 26; i++) {
         map(65 + i, 4 + i);   // A-Z
      }
      map(91, 47);    // Left bracket
      map(92, 49);    // Backslash
      map(93, 48);    // Right bracket
      map(96, 53);    // Grave accent
      map(256, 41);   // Escape
      map(257, 40);   // Enter
      map(258, 43);   // Tab
      map(259, 42);   // Backspace
      map(260, 73);   // Insert
      map(261, 76);   // Delete
      map(262, 79);   // Right
      map(263, 80);   // Left
      map(264, 81);   // Down
      map(265, 82);   // Up
      map(266, 75);   // Page up
      map(267, 78);   // Page down
      map(268, 74);   // Home
      map(269, 77);   // End
      map(280, 57);   // Caps lock
      map(281, 71);   // Scroll lock
      map(282, 83);   // Num lock
      map(283, 70);   // Print screen
      map(284, 72);   // Pause
      for (int i = 0; i < 12; i++) {
         map(290 + i, 58 + i);   // F1-F12
      }
      for (int i = 0; i < 12; i++) {
         map(302 + i, 104 + i);  // F13-F24
      }
      map(320, 98);   // Keypad 0
      for (int i = 1; i <= 9; i++) {
         map(320 + i, 88 + i);   // Keypad 1-9
      }
      map(330, 99);   // Keypad decimal
      map(331, 84);   // Keypad divide
      map(332, 85);   // Keypad multiply
      map(333, 86);   // Keypad subtract
      map(334, 87);   // Keypad add
      map(335, 88);   // Keypad enter
      map(336, 103);  // Keypad equal
      map(340, 225);  // Left shift
      map(341, 224);  // Left control
      map(342, 226);  // Left alt
      map(343, 227);  // Left super
      map(344, 229);  // Right shift
      map(345, 228);  // Right control
      map(346, 230);  // Right alt
      map(347, 231);  // Right super
      map(348, 118);  // Menu
   }

   private static void map(int glfw, int sdl) {
      TO_SDL[glfw] = sdl;
      if (TO_GLFW[sdl] == UNMAPPED) {
         TO_GLFW[sdl] = glfw;
      }
   }

   public static int toScancode(int glfwCode) {
      return glfwCode >= 0 && glfwCode < TO_SDL.length ? TO_SDL[glfwCode] : UNMAPPED;
   }

   public static int fromScancode(int sdlScancode) {
      return sdlScancode >= 0 && sdlScancode < TO_GLFW.length ? TO_GLFW[sdlScancode] : UNMAPPED;
   }

   private LegacyKeyCodes() {
   }
}
