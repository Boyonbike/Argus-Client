/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Part of Argus Client.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.argus.client.managers;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.ClientInitializationEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.hud.ArmorModule;
import dev.argus.client.modules.hud.ChatModule;
import dev.argus.client.modules.hud.CryptoModule;
import dev.argus.client.modules.hud.DirectionModule;
import dev.argus.client.modules.hud.EffectsModule;
import dev.argus.client.modules.hud.EntityListModule;
import dev.argus.client.modules.hud.GraphModule;
import dev.argus.client.modules.hud.IndicatorModule;
import dev.argus.client.modules.hud.InventoryModule;
import dev.argus.client.modules.hud.LagOMeterModule;
import dev.argus.client.modules.hud.MapModule;
import dev.argus.client.modules.hud.MetricsModule;
import dev.argus.client.modules.hud.ModuleListModule;
import dev.argus.client.modules.hud.MusicModule;
import dev.argus.client.modules.hud.PlayerModelModule;
import dev.argus.client.modules.hud.PositionModule;
import dev.argus.client.modules.hud.CoordsModule;
import dev.argus.client.modules.hud.TextRadarModule;
import dev.argus.client.modules.hud.TotemsModule;
import dev.argus.client.modules.hud.WatermarkModule;
import dev.argus.client.modules.hud.WelcomerModule;
import dev.argus.client.modules.hud.ChatWaitingHudModule;
import dev.argus.client.modules.hud.VolytraSpeedHudModule;
import dev.argus.client.modules.hud.BoatSpeedHudModule;
import dev.argus.client.settings.JsonConfigurable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.function.Predicate;

public final class HudModuleRegistry implements ClientAccess, JsonConfigurable {
   public HudModuleRegistry() {
      EventBus events = ClientAccess.eventBus;
      events.subscribe(this);
      ModuleRegistry watermarkRegistry = ClientContext.moduleRegistry;
      WatermarkModule watermark = new WatermarkModule();
      watermarkRegistry.registerModule(watermark);
      ClientContext.moduleRegistry.registerModule(new ModuleListModule());
      ClientContext.moduleRegistry.registerModule(new ArmorModule());
      ClientContext.moduleRegistry.registerModule(new GraphModule());
      ClientContext.moduleRegistry.registerModule(new EffectsModule());
      ClientContext.moduleRegistry.registerModule(new WelcomerModule());
      ClientContext.moduleRegistry.registerModule(new PositionModule());
      ClientContext.moduleRegistry.registerModule(new CoordsModule());
      ClientContext.moduleRegistry.registerModule(new DirectionModule());
      ClientContext.moduleRegistry.registerModule(new TextRadarModule());
      ClientContext.moduleRegistry.registerModule(new MetricsModule());
      ClientContext.moduleRegistry.registerModule(new VolytraSpeedHudModule());
      ClientContext.moduleRegistry.registerModule(new BoatSpeedHudModule());
      ClientContext.moduleRegistry.registerModule(new ChatWaitingHudModule());
      ClientContext.moduleRegistry.registerModule(new TotemsModule());
      ClientContext.moduleRegistry.registerModule(new LagOMeterModule());
      ClientContext.moduleRegistry.registerModule(new InventoryModule());
      ClientContext.moduleRegistry.registerModule(new MusicModule());
      ClientContext.moduleRegistry.registerModule(new PlayerModelModule());
      ClientContext.moduleRegistry.registerModule(new EntityListModule());
      ClientContext.moduleRegistry.registerModule(new ChatModule());
      ClientContext.moduleRegistry.registerModule(new MapModule());
      ClientContext.moduleRegistry.registerModule(new CryptoModule());
      ClientContext.moduleRegistry.registerModule(new IndicatorModule());
   }

   @Override
   public void fromJson(JsonElement json) {
      JsonObject configuration = json.getAsJsonObject();
      configuration.entrySet().stream()
         .sorted(Comparator.comparing(entry -> {
            try {
               JsonElement moduleJson = entry.getValue();
               JsonObject moduleConfiguration = moduleJson.getAsJsonObject();
               String hudKey = "hud";
               JsonObject hudConfiguration = moduleConfiguration.getAsJsonObject(hudKey);
               String indexKey = "index";
               JsonElement indexJson = hudConfiguration.get(indexKey);
               int index = indexJson.getAsInt();
               return index;
            } catch (Throwable failure) {
               return Integer.valueOf(0);
            }
         }))
         .map(entry -> new SimpleEntry<>(findConfiguredModule(entry), entry.getValue()))
         .filter(entry -> entry.getKey() instanceof HudWidgetModule)
         .forEach(entry -> {
            try {
               Module module = (Module)entry.getKey();
               JsonElement moduleJson = (JsonElement)entry.getValue();
               module.fromJson(moduleJson);
            } catch (Exception failure) {
               failure.printStackTrace();
            }
         });
   }

   private static Module findConfiguredModule(Entry configurationEntry) {
      ModuleRegistry registry = ClientContext.moduleRegistry;
      Predicate<Module> matchesName = candidate -> {
         String configName = candidate.getConfigName();
         String entryName = (String)configurationEntry.getKey();
         return configName.equalsIgnoreCase(entryName);
      };
      Optional match = registry.findFirst(matchesName);
      return (Module)match.orElse(null);
   }

   @Override
   public JsonElement toJson() {
      JsonObject configuration = new JsonObject();
      ModuleRegistry registry = ClientContext.moduleRegistry;
      Iterator modules = ((List)registry.getRegistry()).iterator();

      while (modules.hasNext()) {
         Module module = (Module)modules.next();
         if (module instanceof HudWidgetModule) {
            try {
               String configName = module.getConfigName();
               JsonElement moduleJson = module.toJson();
               configuration.add(configName, moduleJson);
            } catch (Exception failure) {
               failure.printStackTrace();
            }
         }
      }

      return configuration;
   }

   @Override
   public String getConfigName() {
      return "hud.json";
   }

   @EventHandler
   private void refreshHudAnchors(ClientInitializationEvent event) {
      ModuleRegistry registry = ClientContext.moduleRegistry;
      Iterator modules = ((List)registry.getRegistry()).iterator();

      while (modules.hasNext()) {
         Module module = (Module)modules.next();
         if (module instanceof HudWidgetModule widget) {
            widget.setAnchor(widget.getAnchor());
         }
      }
   }

}
