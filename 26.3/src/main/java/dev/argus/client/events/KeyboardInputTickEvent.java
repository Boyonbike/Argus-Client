package dev.argus.client.events;

import dev.argus.client.utils.InputUtils;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;

public class KeyboardInputTickEvent extends CancellableEvent {
   private final ClientInput input;

   public KeyboardInputTickEvent(ClientInput input) {
      this.input = input;
   }

   public ClientInput getInput() {
      return this.input;
   }

   public void reset() {
      InputUtils.clearKeyPresses(this.input);
      this.input.moveVector = Vec2.ZERO;
   }

   public static class Pre extends KeyboardInputTickEvent {
      public Pre(ClientInput input) {
         super(input);
      }
   }
}
