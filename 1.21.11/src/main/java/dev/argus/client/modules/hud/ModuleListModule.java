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
      Setting<HUDModule.ModuleSortMode> sortMode = this.sortSetting;
      if (sortMode.getValue() == HUDModule.ModuleSortMode.ALPHABET) {
         this.moduleRows.sort(Comparator.comparing(row -> {
            Module module = row.getModule();
            return module.getInfoString();
         }));
      } else {
         this.moduleRows.sort(Comparator.comparingDouble(row -> {
            FontRenderer font = FontRenderer.INSTANCE;
            Module module = row.getModule();
            return -font.getWidth(module.getInfoString());
         }));
      }
   }

   public ModuleListModule() {
      super("ModuleList", "arraylist");
      this.setRenderer(new HudTextListRenderer(this, this.moduleRows));
      this.getRenderer().setAnchor(HudAnchor.TOP_RIGHT);
   }
}
