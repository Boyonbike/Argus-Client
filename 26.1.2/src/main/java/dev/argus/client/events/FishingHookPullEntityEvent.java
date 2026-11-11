package dev.argus.client.events;

import net.minecraft.world.entity.Entity;

public final class FishingHookPullEntityEvent extends CancellableEvent {
   private final Entity entity;

   public FishingHookPullEntityEvent(Entity entity) {
      this.entity = entity;
   }

   public Entity getEntity() {
      return this.entity;
   }
}
