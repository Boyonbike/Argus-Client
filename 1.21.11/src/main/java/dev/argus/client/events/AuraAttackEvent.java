package dev.argus.client.events;

public final class AuraAttackEvent extends CancellableEvent {
   private final EventStage stage;
   private final int entityId;

   public AuraAttackEvent(EventStage stage, int entityId) {
      this.stage = stage;
      this.entityId = entityId;
   }

   public int getEntityId() {
      return this.entityId;
   }

   public EventStage getStage() {
      return this.stage;
   }
}
