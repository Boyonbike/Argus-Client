package dev.argus.client.utils.movement;

import dev.argus.client.ClientContext;
import dev.argus.client.modules.player.FreecamModule;
import dev.argus.client.utils.KeyUtils;
import net.minecraft.client.KeyMapping;

/**
 * Key reads for code that moves the player directly from key bindings instead of
 * going through ClientInput. While Freecam is on the keyboard drives the camera,
 * so the user's physical presses are ignored here; presses set in software (for
 * example by an autopilot calling KeyMapping.setDown) still count.
 */
public final class PlayerMovementKeys {
   private PlayerMovementKeys() {
   }

   public static boolean isFreecamActive() {
      FreecamModule freecam = ClientContext.getModule(FreecamModule.class);
      return freecam != null && freecam.isToggled();
   }

   public static boolean isDown(KeyMapping mapping) {
      if (!mapping.isDown()) {
         return false;
      }

      return !isFreecamActive() || !KeyUtils.isPhysicallyDown(mapping);
   }
}
