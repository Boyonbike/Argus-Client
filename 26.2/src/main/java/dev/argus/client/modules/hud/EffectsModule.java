package dev.argus.client.modules.hud;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.HudAnchor;
import dev.argus.client.settings.options.ListFilterMode;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

public class EffectsModule extends HudWidgetModule {
   private final Setting<ListFilterMode> selectionSetting = this.add(new EnumSetting<>("Selection", ListFilterMode.ANY));
   private final Setting<Set<MobEffect>> effectsSetting = this.add(new RegistrySetSetting<>("Effects", BuiltInRegistries.MOB_EFFECT));
   private final Setting<Boolean> vanillaSetting;
   private final List<EffectHudRow> effectRows;

   @Override
   public void onEnable() {
      this.effectRows.clear();
      BuiltInRegistries.MOB_EFFECT.listElements().forEach(effectReference -> this.effectRows.add(new EffectHudRow(effectReference, () -> {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         if (!player.getActiveEffectsMap().containsKey(effectReference)) {
            return false;
         }

         Setting<ListFilterMode> selectionOption = this.selectionSetting;
         ListFilterMode selection = selectionOption.getValue();
         MobEffect effect = effectReference.value();
         Setting<Set<MobEffect>> effectsOption = this.effectsSetting;
         return ListFilterMode.matches(selection, effect, effectsOption);
      })));
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent ignoredEvent) {
      this.effectRows.sort(Comparator.comparing(row -> {
         MobEffect effect = row.getEffectHolder().value();
         return effect.getDisplayName().getString();
      }));
   }

   public EffectsModule() {
      super("Effects", "potions");
      BooleanSetting vanillaOption = new BooleanSetting("Vanilla", false);
      this.vanillaSetting = this.add(vanillaOption);
      this.effectRows = new ArrayList<>();
      HudTextListRenderer rowRenderer = new HudTextListRenderer(this, this.effectRows) {
         @Override
         public Color getRowColor(float rowPosition, HudTextRow row) {
            EffectHudRow effectRow = (EffectHudRow)row;
            MobEffect effect = effectRow.getEffectHolder().value();
            Setting<Boolean> vanillaOptionx = EffectsModule.this.vanillaSetting;
            Boolean useVanillaColor = vanillaOptionx.getValue();
            return useVanillaColor ? new Color(this.getVanillaEffectColor(effect), false) : super.getRowColor(rowPosition, row);
         }

         private int getVanillaEffectColor(MobEffect effect) {
            return effect.getColor();
         }
      };
      this.setRenderer(rowRenderer);
      this.getRenderer().setAnchor(HudAnchor.BOTTOM_RIGHT);
      Setting<Set<MobEffect>> effectsForVisibility = this.effectsSetting;
      Predicate<Object> visibilityPredicate = ignoredValue -> this.selectionSetting.getValue() != ListFilterMode.ANY;
      effectsForVisibility.addVisibilityPredicate(visibilityPredicate);
   }
}
