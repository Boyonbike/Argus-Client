package dev.argus.client.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyKeyCodesTest {
   @Test
   void translatesGlfwKeyCodesToSdlScancodes() {
      assertEquals(4, LegacyKeyCodes.toScancode(65));    // A
      assertEquals(19, LegacyKeyCodes.toScancode(80));   // P
      assertEquals(39, LegacyKeyCodes.toScancode(48));   // 0
      assertEquals(44, LegacyKeyCodes.toScancode(32));   // Space
      assertEquals(40, LegacyKeyCodes.toScancode(257));  // Enter
      assertEquals(41, LegacyKeyCodes.toScancode(256));  // Escape
      assertEquals(43, LegacyKeyCodes.toScancode(258));  // Tab
      assertEquals(42, LegacyKeyCodes.toScancode(259));  // Backspace
      assertEquals(58, LegacyKeyCodes.toScancode(290));  // F1
      assertEquals(68, LegacyKeyCodes.toScancode(300));  // F11
      assertEquals(57, LegacyKeyCodes.toScancode(280));  // Caps Lock
      assertEquals(224, LegacyKeyCodes.toScancode(341)); // Left Control
      assertEquals(225, LegacyKeyCodes.toScancode(340)); // Left Shift
      assertEquals(228, LegacyKeyCodes.toScancode(345)); // Right Control
      assertEquals(82, LegacyKeyCodes.toScancode(265));  // Up
      assertEquals(98, LegacyKeyCodes.toScancode(320));  // Keypad 0
   }

   @Test
   void roundTripsEveryMappedKey() {
      for (int glfw = 32; glfw <= 348; glfw++) {
         int sdl = LegacyKeyCodes.toScancode(glfw);
         if (sdl != LegacyKeyCodes.UNMAPPED) {
            assertEquals(glfw, LegacyKeyCodes.fromScancode(sdl), "glfw " + glfw);
         }
      }
   }

   @Test
   void translatesMouseButtonSpaces() {
      assertEquals(1, LegacyMouseButtons.toSdl(0));   // left
      assertEquals(3, LegacyMouseButtons.toSdl(1));   // right
      assertEquals(2, LegacyMouseButtons.toSdl(2));   // middle
      assertEquals(4, LegacyMouseButtons.toSdl(3));   // back
      assertEquals(0, LegacyMouseButtons.toGlfw(1));  // left
      assertEquals(1, LegacyMouseButtons.toGlfw(3));  // right
      assertEquals(2, LegacyMouseButtons.toGlfw(2));  // middle
      assertEquals(3, LegacyMouseButtons.toGlfw(4));  // back
   }
}
