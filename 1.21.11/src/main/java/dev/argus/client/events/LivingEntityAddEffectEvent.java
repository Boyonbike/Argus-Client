package dev.argus.client.events;

import net.minecraft.world.effect.MobEffectInstance;

public final class LivingEntityAddEffectEvent extends CancellableEvent {
   private final MobEffectInstance effect;

   public LivingEntityAddEffectEvent(MobEffectInstance effect) {
      this.effect = effect;
   }

   public MobEffectInstance getEffect() {
      return this.effect;
   }
}
