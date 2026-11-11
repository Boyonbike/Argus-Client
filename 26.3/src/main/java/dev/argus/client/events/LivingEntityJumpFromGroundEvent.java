package dev.argus.client.events;

public final class LivingEntityJumpFromGroundEvent extends CancellableEvent {
   private float jumpPower;

   public LivingEntityJumpFromGroundEvent(float jumpPower) {
      this.jumpPower = jumpPower;
   }

   public float getJumpPower() {
      return this.jumpPower;
   }

   public void setJumpPower(float jumpPower) {
      this.jumpPower = jumpPower;
   }
}
