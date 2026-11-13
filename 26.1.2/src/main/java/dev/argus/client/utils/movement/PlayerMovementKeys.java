package dev.argus.client.utils.movement;

import dev.argus.client.ClientAccess;
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

   /** Modules holding the forward key down for you, such as AutoWalk or Spiral's autopilot. */
   private static final java.util.Set<Object> FORWARD_HOLDERS = java.util.concurrent.ConcurrentHashMap.newKeySet();

   /**
    * Lets a module hold forward for movement code that reads keys rather than the player's input, such as the elytra
    * fly modes. Call with {@code held} false (or on disable) to let go.
    */
   public static void holdForward(Object owner, boolean held) {
      if (held) {
         FORWARD_HOLDERS.add(owner);
      } else {
         FORWARD_HOLDERS.remove(owner);
      }
   }

   public static boolean isForwardHeld() {
      return !FORWARD_HOLDERS.isEmpty();
   }

   /** Modules holding jump or sneak, which elytra fly modes such as Volytra Fly read as climb and descend. */
   private static final java.util.Set<Object> UP_HOLDERS = java.util.concurrent.ConcurrentHashMap.newKeySet();
   private static final java.util.Set<Object> DOWN_HOLDERS = java.util.concurrent.ConcurrentHashMap.newKeySet();

   /** Holds climb (+1), descend (-1) or neither (0) for movement code that reads the jump and sneak keys. */
   public static void holdVertical(Object owner, int direction) {
      if (direction > 0) {
         UP_HOLDERS.add(owner);
      } else {
         UP_HOLDERS.remove(owner);
      }

      if (direction < 0) {
         DOWN_HOLDERS.add(owner);
      } else {
         DOWN_HOLDERS.remove(owner);
      }
   }

   public static boolean isDown(KeyMapping mapping) {
      if (!FORWARD_HOLDERS.isEmpty() && mapping == ClientAccess.argusclientMinecraft.options.keyUp) {
         return true;
      }

      // Your own keys win: holding one physically always counts, and a module's hold never overrides the opposite key.
      if (!UP_HOLDERS.isEmpty() && mapping == ClientAccess.argusclientMinecraft.options.keyJump
         && !KeyUtils.isPhysicallyDown(ClientAccess.argusclientMinecraft.options.keyShift)) {
         return true;
      }

      if (!DOWN_HOLDERS.isEmpty() && mapping == ClientAccess.argusclientMinecraft.options.keyShift
         && !KeyUtils.isPhysicallyDown(ClientAccess.argusclientMinecraft.options.keyJump)) {
         return true;
      }

      if (!mapping.isDown()) {
         return false;
      }

      return !isFreecamActive() || !KeyUtils.isPhysicallyDown(mapping);
   }
}
