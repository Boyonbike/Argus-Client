package dev.argus.client.events;

public class CancellableEvent {
   private boolean cancelled;

   public boolean isCancelled() {
      return this.cancelled;
   }

   public void resetCancellation() {
      this.cancelled = false;
   }

   public void cancel() {
      this.cancelled = true;
   }
}
