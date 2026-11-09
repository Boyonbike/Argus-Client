package dev.argus.client.events;

public final class ClientLevelRemoveEntityEvent extends CancellableEvent {
   private final int id;
   private final boolean entityPresent;

   public boolean isEntityPresent() {
      return this.entityPresent;
   }

   public ClientLevelRemoveEntityEvent(int id, boolean entityPresent) {
      this.id = id;
      this.entityPresent = entityPresent;
   }

   public int getId() {
      return this.id;
   }
}
