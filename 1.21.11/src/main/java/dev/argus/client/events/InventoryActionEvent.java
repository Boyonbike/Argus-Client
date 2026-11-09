package dev.argus.client.events;

public final class InventoryActionEvent extends CancellableEvent {
   private final EventStage stage;

   public InventoryActionEvent(EventStage stage) {
      this.stage = stage;
   }

   public EventStage getStage() {
      return this.stage;
   }
}
