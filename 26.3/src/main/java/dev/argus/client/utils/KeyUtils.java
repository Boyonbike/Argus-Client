package dev.argus.client.utils;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import dev.argus.client.modules.Keybind;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.lwjgl.sdl.SDLMouse;

public final class KeyUtils {
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
         return isMouseButtonDownSdl(key.getValue());
      } else {
         return InputConstants.isKeyDown(key.getValue());
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
            int resolvedCode = LegacyKeyCodes.fromScancode(key.getValue());
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

      Key key = Type.KEYBOARD.getOrCreate(LegacyKeyCodes.toScancode(keyCode));
      String translationKey = key.getName();
      String displayName = translationKey.replaceAll("key.keyboard.", "").replace(".", "_");
      return displayName.toUpperCase();
   }

   public static boolean isPhysicalMouseButtonDown(int button) {
      return isMouseButtonDownSdl(LegacyMouseButtons.toSdl(button));
   }

   private static boolean isMouseButtonDownSdl(int sdlButton) {
      if (sdlButton <= 0) {
         return false;
      }
      int state = SDLMouse.nSDL_GetMouseState(0L, 0L);
      return (state & (1 << (sdlButton - 1))) != 0;
   }

   public static boolean isPhysicalKeyDown(int keyCode) {
      if (keyCode <= 7) {
         return false;
      }

      int scancode = LegacyKeyCodes.toScancode(keyCode);
      return scancode != LegacyKeyCodes.UNMAPPED && InputConstants.isKeyDown(scancode);
   }

   public static boolean isShiftDown() {
      return isPhysicalKeyDown(340);
   }
}
