package dev.argus.client.utils.movement;

import dev.argus.client.ClientAccess;
import net.minecraft.world.entity.player.Input;

public enum MovementDirection {
   FORWARD(1.0F, 0.0F),
   FORWARD_RIGHT(1.0F, 1.0F),
   RIGHT(0.0F, 1.0F),
   BACKWARD_RIGHT(-1.0F, 1.0F),
   BACKWARD(-1.0F, 0.0F),
   BACKWARD_LEFT(-1.0F, -1.0F),
   LEFT(0.0F, -1.0F),
   FORWARD_LEFT(1.0F, -1.0F);

   private final float forwardInput;
   private final float sidewaysInput;

   MovementDirection(float forwardInput, float sidewaysInput) {
      this.forwardInput = forwardInput;
      this.sidewaysInput = sidewaysInput;
   }

   public float getForwardInput() {
      return this.forwardInput;
   }

   public float getSidewaysInput() {
      return this.sidewaysInput;
   }

   public Input createInput(Input input) {
      return new Input(
         this.forwardInput > 0.0F,
         this.forwardInput < 0.0F,
         this.sidewaysInput > 0.0F,
         this.sidewaysInput < 0.0F,
         input.jump(),
         input.shift(),
         this.isSprintInput()
      );
   }

   private boolean isSprintInput() {
      return ClientAccess.argusclientMinecraft.player.isSprinting() && this.forwardInput > 0.0F;
   }
}
