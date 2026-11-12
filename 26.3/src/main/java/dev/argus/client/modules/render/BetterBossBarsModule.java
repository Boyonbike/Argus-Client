package dev.argus.client.modules.render;

import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;

/**
 * Stacks boss bars that share a name into one bar with a counter, and can hide names or change the bar spacing.
 * Ported from Meteor Client's BossStack module (GPL-3.0); see THIRD_PARTY_NOTICES.md.
 */
public class BetterBossBarsModule extends Module {
   public final Setting<Boolean> stackSetting = this.add(new BooleanSetting("Stack", true));
   public final Setting<Boolean> hideNameSetting = this.add(new BooleanSetting("HideName", false));
   public final Setting<Integer> spacingSetting = this.add(new NumberSetting<>("Spacing", 19, 9, 40).withUnit("px"));
   /** How many bars each shown bar stands for this frame, for bars that stand for more than one. */
   private final Map<LerpingBossEvent, Integer> counts = new IdentityHashMap<>();

   public BetterBossBarsModule() {
      super("BetterBossBars", "Stacks boss bars with the same name and adds a counter.", ModuleCategory.RENDER);
   }

   /** The bars to draw this frame: one per name when stacking, counting how many each stands for. */
   public Iterator<LerpingBossEvent> filter(Iterator<LerpingBossEvent> bars) {
      this.counts.clear();
      if (!this.stackSetting.getValue()) {
         return bars;
      }

      Map<String, LerpingBossEvent> chosen = new LinkedHashMap<>();
      while (bars.hasNext()) {
         LerpingBossEvent bar = bars.next();
         LerpingBossEvent first = chosen.putIfAbsent(bar.getName().getString(), bar);
         if (first != null) {
            this.counts.merge(first, 2, (count, ignored) -> count + 1);
         }
      }

      return chosen.values().iterator();
   }

   /** The label drawn above a bar: hidden, or with " x<count>" when it stands for several. */
   public Component label(LerpingBossEvent bar, Component name) {
      if (this.hideNameSetting.getValue()) {
         return Component.empty();
      }

      Integer count = this.counts.get(bar);
      return count == null ? name : name.copy().append(" x" + count);
   }

   /** Pixels between the tops of two bars (vanilla is 19). */
   public int spacing() {
      return this.spacingSetting.getValue();
   }
}
