package dev.argus.client.modules.hud;

import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.client.HUDModule;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.HudAnchor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class ModuleListModule extends HudWidgetModule {
   public final Setting<Boolean> onlyBoundSetting = this.add(new BooleanSetting("OnlyBound", false));
   private final java.util.IdentityHashMap<ModuleListHudRow, String> sortNames = new java.util.IdentityHashMap<>();
   private final it.unimi.dsi.fastutil.objects.Reference2FloatOpenHashMap<ModuleListHudRow> sortWidths = new it.unimi.dsi.fastutil.objects.Reference2FloatOpenHashMap<>();
   public final Setting<HUDModule.ModuleSortMode> sortSetting = this.add(new EnumSetting<>("Sort", HUDModule.ModuleSortMode.LENGTH));
   private final List<ModuleListHudRow> moduleRows = new ArrayList<>();

   @Override
   public void onEnable() {
      this.moduleRows.clear();
      ModuleRegistry registry = ClientContext.moduleRegistry;
      Stream<ModuleListHudRow> rowsStream = registry.getRegistry().stream().map(registeredModule -> new ModuleListHudRow(this, registeredModule));
      rowsStream.forEach(this.moduleRows::add);
   }

   @EventHandler
   public void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      // Work out each row's sort key once per tick. The comparators used to rebuild the info string
      // (and measure its width) on every comparison: thousands of string builds a tick for ~200 rows.
      boolean alphabetical = this.sortSetting.getValue() == HUDModule.ModuleSortMode.ALPHABET;
      FontRenderer font = FontRenderer.INSTANCE;
      this.sortNames.clear();
      this.sortWidths.clear();
      for (ModuleListHudRow row : this.moduleRows) {
         String info = row.getModule().getInfoString();
         if (alphabetical) {
            this.sortNames.put(row, info);
         } else {
            this.sortWidths.put(row, font.getWidth(info));
         }
      }

      if (alphabetical) {
         this.moduleRows.sort(Comparator.comparing(this.sortNames::get));
      } else {
         this.moduleRows.sort((left, right) -> Float.compare(this.sortWidths.getFloat(right), this.sortWidths.getFloat(left)));
      }
   }

   public ModuleListModule() {
      super("ModuleList", "arraylist");
      this.setRenderer(new HudTextListRenderer(this, this.moduleRows));
      this.getRenderer().setAnchor(HudAnchor.TOP_RIGHT);
   }
}
