package dev.argus.client.events;

import net.minecraft.core.Holder;

public final class StatusEffectQueryEvent extends CancellableEvent {
   private final Holder effect;

   public StatusEffectQueryEvent(Holder effect) {
      this.effect = effect;
   }

   public Holder getEffect() {
      return this.effect;
   }
}
