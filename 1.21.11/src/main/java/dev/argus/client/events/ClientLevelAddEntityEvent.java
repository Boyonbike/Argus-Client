package dev.argus.client.events;

import net.minecraft.world.entity.Entity;

public final class ClientLevelAddEntityEvent extends CancellableEvent {
   private final Entity entity;

   public Entity getEntity() {
      return this.entity;
   }

   public ClientLevelAddEntityEvent(Entity entity) {
      this.entity = entity;
   }
}
