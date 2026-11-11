package dev.argus.client.events;

import net.minecraft.world.entity.Entity;

public final class PlayerAttackEvent extends CancellableEvent {
   private final EventStage stage;
   private final Entity target;

   public PlayerAttackEvent(EventStage stage, Entity target) {
      this.stage = stage;
      this.target = target;
   }

   public Entity getTarget() {
      return this.target;
   }

   public EventStage getStage() {
      return this.stage;
   }
}
