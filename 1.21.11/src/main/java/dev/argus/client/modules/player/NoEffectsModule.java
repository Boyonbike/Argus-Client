package dev.argus.client.modules.player;

import dev.argus.client.events.EventHandler;
import dev.argus.client.events.StatusEffectQueryEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

public class NoEffectsModule extends Module {
   private final Setting<Set<MobEffect>> effectsSetting = this.add(new RegistrySetSetting<>("Effects", BuiltInRegistries.MOB_EFFECT));

   public NoEffectsModule() {
      super("NoEffects", "Allows you to cancel certain effects.", ModuleCategory.PLAYER);
      List<MobEffect> defaults = List.of(MobEffects.LEVITATION.value(), MobEffects.SLOW_FALLING.value());
      this.selectedEffects().addAll(defaults);
      this.defaultEffects().addAll(defaults);
   }

   public boolean blocksEffect(Holder<MobEffect> effect) {
      return this.selectedEffects().contains(effect.value());
   }

   @EventHandler
   private void onEffectQuery(StatusEffectQueryEvent event) {
      if (this.blocksEffect(event.getEffect())) {
         event.cancel();
      }
   }

   private Set<MobEffect> selectedEffects() {
      return this.effectsSetting.getValue();
   }

   private Set<MobEffect> defaultEffects() {
      return this.effectsSetting.getDefaultValue();
   }
}
