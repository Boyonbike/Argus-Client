package dev.argus.client.events;

public final class MinecraftRunTickEvent extends CancellableEvent {
   private final boolean hasPendingTicks;

   public boolean hasPendingTicks() {
      return this.hasPendingTicks;
   }

   public MinecraftRunTickEvent(boolean hasPendingTicks) {
      this.hasPendingTicks = hasPendingTicks;
   }
}
