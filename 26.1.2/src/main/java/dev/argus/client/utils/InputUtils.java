package dev.argus.client.utils;

import dev.argus.mixin.InputAccessor;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;

public final class InputUtils {
   private InputUtils() {
   }

   public static void setSneak(ClientInput input, boolean pressed) {
      getInputAccessor(input).argusclient$setSneak(pressed);
   }

   public static void setJump(ClientInput input, boolean pressed) {
      getInputAccessor(input).argusclient$setJump(pressed);
   }

   public static void setForward(ClientInput input, boolean pressed) {
      getInputAccessor(input).argusclient$setForward(pressed);
   }

   public static void setBack(ClientInput input, boolean pressed) {
      getInputAccessor(input).argusclient$setBack(pressed);
   }

   public static void setLeft(ClientInput input, boolean pressed) {
      getInputAccessor(input).argusclient$setLeft(pressed);
   }

   public static void setRight(ClientInput input, boolean pressed) {
      getInputAccessor(input).argusclient$setRight(pressed);
   }

   public static void setSprint(ClientInput input, boolean pressed) {
      getInputAccessor(input).argusclient$setSprint(pressed);
   }

   public static void setForwardMovement(ClientInput input, float forward) {
      Vec2 movement = input.moveVector;
      input.moveVector = new Vec2(movement.x, forward);
   }

   public static void setSidewaysMovement(ClientInput input, float sideways) {
      Vec2 movement = input.moveVector;
      input.moveVector = new Vec2(sideways, movement.y);
   }

   public static void clearKeyPresses(ClientInput input) {
      setSneak(input, false);
      setSprint(input, false);
      setJump(input, false);
      setForward(input, false);
      setBack(input, false);
      setLeft(input, false);
      setRight(input, false);
   }

   public static ClientInput createInput() {
      ClientInput input = new ClientInput();
      input.moveVector = new Vec2(0.0F, 1.0F);
      return input;
   }

   private static InputAccessor getInputAccessor(ClientInput input) {
      return (InputAccessor)(Object)input.keyPresses;
   }
}
