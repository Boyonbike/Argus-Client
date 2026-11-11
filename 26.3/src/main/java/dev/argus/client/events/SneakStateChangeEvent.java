package dev.argus.client.events;

public final class SneakStateChangeEvent extends CancellableEvent {
   private final boolean sneaking;

   public boolean isSneaking() {
      return this.sneaking;
   }

   public SneakStateChangeEvent(boolean sneaking) {
      this.sneaking = sneaking;
   }
}
