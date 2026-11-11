package dev.argus.client.events;

public final class MouseHandlerOnScrollEvent extends CancellableEvent {
   private final double horizontalOffset;
   private final double verticalOffset;

   public MouseHandlerOnScrollEvent(double horizontalOffset, double verticalOffset) {
      this.horizontalOffset = horizontalOffset;
      this.verticalOffset = verticalOffset;
   }

   public double getHorizontalOffset() {
      return this.horizontalOffset;
   }

   public double getVerticalOffset() {
      return this.verticalOffset;
   }
}
