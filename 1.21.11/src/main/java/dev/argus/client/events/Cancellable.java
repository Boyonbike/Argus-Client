package dev.argus.client.events;

public interface Cancellable {
   boolean isCancelled();

   default void cancel() {
      this.setCancelled(true);
   }

   void setCancelled(boolean var1);
}
