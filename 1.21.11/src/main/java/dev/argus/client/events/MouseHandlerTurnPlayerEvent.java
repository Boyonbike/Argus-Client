package dev.argus.client.events;

public final class MouseHandlerTurnPlayerEvent extends CancellableEvent {
   private final double yawDelta;
   private final double pitchDelta;

   public double getPitchDelta() {
      return this.pitchDelta;
   }

   public double getYawDelta() {
      return this.yawDelta;
   }

   public MouseHandlerTurnPlayerEvent(double yawDelta, double pitchDelta) {
      this.yawDelta = yawDelta;
      this.pitchDelta = pitchDelta;
   }
}
