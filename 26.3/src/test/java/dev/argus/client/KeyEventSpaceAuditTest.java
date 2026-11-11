package dev.argus.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Pins the SDL/GLFW key-space boundary in the GUI-open and guiMove hooks. */
class KeyEventSpaceAuditTest {
   @Test
   void screenHandlersTranslateSdlKeyEventsBeforeComparingBinds() throws Exception {
      for (String file : List.of(
         "mixin/TitleScreenMixin.java",
         "mixin/DisconnectedScreenMixin.java",
         "mixin/JoinMultiplayerScreenMixin.java")) {
         String text = Files.readString(Path.of("src/main/java/dev/argus", file));
         assertFalse(text.contains("keyEvent.key() ==") || text.contains("keyEvent.key() !="),
            file + " compares an SDL key event with a GLFW-space bind");
      }
   }

   @Test
   void guiMoveChecksSdlKeyStateWithoutReinterpretingIt() throws Exception {
      String text = Files.readString(Path.of("src/main/java/dev/argus/mixin/KeyboardInputMixin.java"));
      assertFalse(text.contains("KeyUtils.isPhysicalKeyDown(keyCode)"),
         "KeyMapping values are SDL scancodes; isPhysicalKeyDown expects GLFW codes");
   }
}
