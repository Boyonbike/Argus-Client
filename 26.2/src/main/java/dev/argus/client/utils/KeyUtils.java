package dev.argus.client.utils;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import dev.argus.client.ClientAccess;
import dev.argus.client.modules.Keybind;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

public final class KeyUtils implements ClientAccess {
   private static final Map<String, Integer> KEY_ALIASES = Map.ofEntries(
      Map.entry("rshift", 344),
      Map.entry("rctrl", 345),
      Map.entry("ralt", 346),
      Map.entry("shift", 340),
      Map.entry("alt", 342),
      Map.entry("ctrl", 341),
      Map.entry("capslock", 280)
   );

   public static boolean isControlDown() {
      return isPhysicalKeyDown(341);
   }

   public static Map<String, Integer> getKeyAliases() {
      return KEY_ALIASES;
   }

   public static boolean isAltDown() {
      return isPhysicalKeyDown(342);
   }

   private KeyUtils() {
   }

   public static String getKeyName(Keybind binding) {
      int keyCode = binding.code();
      boolean mouse = binding.mouse();
      return getKeyName(keyCode, mouse);
   }

   public static boolean isPhysicallyDown(KeyMapping mapping) {
      Key key = mapping.key;
      if (key.getType() == Type.MOUSE) {
         int button = key.getValue();
         return isPhysicalMouseButtonDown(button);
      } else {
         int keyCode = key.getValue();
         return isPhysicalKeyDown(keyCode);
      }
   }

   public static Keybind parseKeybind(String bindingText) {
      Locale rootLocale = Locale.ROOT;
      String lowerText = bindingText.toLowerCase(rootLocale);
      String mousePrefix = "mouse";
      if (lowerText.startsWith(mousePrefix)) {
         String digits = lowerText.substring(mousePrefix.length());

         try {
            int mouseButton = Integer.parseInt(digits);
            return Keybind.toggle(mouseButton, true);
         } catch (NumberFormatException malformedMouse) {
            throw new IllegalArgumentException("Unknown mouse key: " + digits);
         }
      } else {
         Map<String, Integer> aliases = KEY_ALIASES;
         Integer aliasCode = aliases.get(lowerText);
         if (aliasCode != null) {
            int aliasKeyCode = aliasCode;
            return Keybind.toggle(aliasKeyCode, false);
         } else {
            String normalizedKeyName = lowerText.replace("_", ".");
            String translationKey = "key.keyboard." + normalizedKeyName;
            Key key = InputConstants.getKey(translationKey);
            int resolvedCode = key.getValue();
            return Keybind.toggle(resolvedCode, false);
         }
      }
   }

   public static boolean isPhysicallyDown(Keybind binding) {
      if (binding.mouse()) {
         int button = binding.code();
         return isPhysicalMouseButtonDown(button);
      } else {
         int keyCode = binding.code();
         return isPhysicalKeyDown(keyCode);
      }
   }

   public static String getKeyName(int keyCode, boolean mouse) {
      if (keyCode < 0) {
         return "NONE";
      }

      if (mouse) {
         return "MOUSE" + keyCode;
      }

      KeyEvent keyEvent = new KeyEvent(keyCode, 0, 0);
      Key key = InputConstants.getKey(keyEvent);
      String translationKey = key.getName();
      String displayName = translationKey.replaceAll("key.keyboard.", "").replace(".", "_");
      return displayName.toUpperCase();
   }

   public static boolean isPhysicalMouseButtonDown(int button) {
      Window window = ClientAccess.argusclientMinecraft.getWindow();
      long handle = window.handle();
      return GLFW.glfwGetMouseButton(handle, button) == 1;
   }

   public static boolean isPhysicalKeyDown(int keyCode) {
      if (keyCode <= 7) {
         return false;
      }

      Window window = ClientAccess.argusclientMinecraft.getWindow();
      long handle = window.handle();
      return GLFW.glfwGetKey(handle, keyCode) == 1;
   }

   public static boolean isShiftDown() {
      return isPhysicalKeyDown(340);
   }
}
