package dev.argus.client.events;

public final class FireworkRocketEntityTickEvent extends CancellableEvent {
   private float yaw;
   private float pitch;

   public FireworkRocketEntityTickEvent(float yaw, float pitch) {
      this.yaw = yaw;
      this.pitch = pitch;
   }

   public float getPitch() {
      return this.pitch;
   }

   public float getYaw() {
      return this.yaw;
   }

   public void setPitch(float pitch) {
      this.pitch = pitch;
      this.cancel();
   }

   public void setYaw(float yaw) {
      this.yaw = yaw;
      this.cancel();
   }
}
