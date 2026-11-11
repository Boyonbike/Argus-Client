package dev.argus.client.settings.options;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.argus.client.ClientContext;
import dev.argus.client.config.LegacyBranding;
import dev.argus.client.managers.AbstractRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.render.LogoutSpotsModule;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Predicate;

public final class ModulePresetFilter {
   public static final ModulePresetFilter INSTANCE = new ModulePresetFilter();

   private ModulePresetFilter() {
   }

   public void retainScalarSettings(JsonObject modulesJson) {
      for (JsonElement moduleElement : modulesJson.asMap().values()) {
         if (moduleElement instanceof JsonObject moduleJson) {
            moduleJson.remove("key");
            if (moduleJson.has("settings")) {
               JsonElement settingsElement = moduleJson.get("settings");
               if (settingsElement.isJsonObject()) {
                  ArrayList<String> nestedSettingKeys = new ArrayList();
                  JsonObject settingsJson = settingsElement.getAsJsonObject();

                  for (Entry settingEntry : settingsJson.asMap().entrySet()) {
                     JsonElement settingValue = (JsonElement)settingEntry.getValue();
                     if (settingValue.isJsonObject()) {
                        nestedSettingKeys.add((String)settingEntry.getKey());
                     }
                  }

                  for (String removalKey : nestedSettingKeys) {
                     JsonObject removalSettingsJson = settingsElement.getAsJsonObject();
                     removalSettingsJson.remove(removalKey);
                  }
               }
            }
         }
      }
   }

   public void retainModuleSettings(JsonObject modulesJson, Predicate includedModules) {
      for (String moduleName : modulesJson.asMap().keySet()) {
         if (modulesJson.get(moduleName) instanceof JsonObject moduleJson) {
            AbstractRegistry modules = ClientContext.moduleRegistry;
            Predicate<Module> modulePredicate = module -> module.getConfigName().equals(LegacyBranding.canonicalModuleName(moduleName));
            Optional matchingModule = modules.findFirst(modulePredicate);
            if (!matchingModule.isEmpty()) {
               boolean includeModule = includedModules.test((Module)matchingModule.get());
               if (!includeModule || matchingModule.orElse(null) instanceof LogoutSpotsModule) {
                  moduleJson.remove("enabled");
               }

               moduleJson.remove("key");
               if (moduleJson.has("settings")) {
                  JsonElement settingsElement = moduleJson.get("settings");
                  if (settingsElement.isJsonObject()) {
                     ArrayList<String> filteredSettingKeys = new ArrayList();
                     JsonObject settingsJson = settingsElement.getAsJsonObject();

                     for (Entry settingEntry : settingsJson.asMap().entrySet()) {
                        if (includeModule) {
                           break;
                        }

                        String settingName = (String)settingEntry.getKey();
                        JsonElement settingValue = (JsonElement)settingEntry.getValue();
                        if (!this.isVisualSetting(settingName, settingValue)) {
                           filteredSettingKeys.add((String)settingEntry.getKey());
                        }
                     }

                     for (String removalKey : filteredSettingKeys) {
                        JsonObject removalSettingsJson = settingsElement.getAsJsonObject();
                        removalSettingsJson.remove(removalKey);
                     }
                  }
               }
            }
         }
      }
   }

   public void retainKeyBindings(JsonObject modulesJson) {
      for (JsonElement moduleElement : modulesJson.asMap().values()) {
         if (moduleElement instanceof JsonObject moduleJson) {
            moduleJson.remove("enabled");
            moduleJson.remove("settings");
         }
      }
   }

   public boolean isVisualSetting(String settingName, JsonElement settingValue) {
      return settingName.equalsIgnoreCase("render") || settingName.equalsIgnoreCase("linewidth") || settingValue.isJsonObject();
   }
}
