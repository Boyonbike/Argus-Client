package dev.argus.client.settings.options;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.argus.client.ClientContext;
import dev.argus.client.config.JsonUtils;
import dev.argus.client.managers.ConfigurationManager;
import dev.argus.client.managers.MacroRegistry;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.modules.Keybind;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.ColorsModule;
import dev.argus.client.modules.client.FontsModule;
import dev.argus.client.modules.client.HUDModule;
import dev.argus.client.modules.client.NotificationsModule;
import dev.argus.client.modules.client.UIModule;
import dev.argus.client.modules.misc.KillEffectsModule;
import dev.argus.client.modules.misc.SwingModule;
import dev.argus.client.modules.render.LogoutSpotsModule;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.JsonConfigurable;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.Setting;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

public enum PresetCategory implements JsonConfigurable, NamedEntry {
   MODULES("Modules") {
      @Override
      public void fromJson(JsonElement json) {
         ModulePresetFilter filter = ModulePresetFilter.INSTANCE;
         JsonObject object = json.getAsJsonObject();
         filter.retainScalarSettings(object);
         super.fromJson(json);
      }

      @Override
      public JsonElement toJson() {
         JsonObject preset = new JsonObject();
         this.writeMetadata(preset);
         ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;

         for (Module module : (List<Module>)moduleRegistry.getRegistry()) {
            if (!(module instanceof HudWidgetModule) && !(module instanceof FontsModule)) {
               JsonObject moduleJson = new JsonObject();
               moduleJson.addProperty("enabled", module.isToggled());
               JsonObject settingsJson = new JsonObject();

               for (Setting<?> setting : module.getSettings()) {
                  if (!(setting instanceof ColorSetting) && !setting.isGroupOnly() && !setting.isExcludedFromConfig()) {
                     String settingName = setting.getConfigName();
                     JsonElement settingJson = setting.toJson();
                     settingsJson.add(settingName, settingJson);
                  }
               }

               if (!JsonUtils.isRecursivelyEmpty(settingsJson)) {
                  moduleJson.add("settings", settingsJson);
               }

               String moduleName = module.getConfigName();
               preset.add(moduleName, moduleJson);
            }
         }

         return preset;
      }
   },
   BINDS("Binds") {
      @Override
      public void fromJson(JsonElement json) {
         ModulePresetFilter filter = ModulePresetFilter.INSTANCE;
         JsonObject object = json.getAsJsonObject();
         filter.retainKeyBindings(object);
         super.fromJson(json);
      }

      @Override
      public JsonElement toJson() {
         JsonObject preset = new JsonObject();
         this.writeMetadata(preset);
         ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;

         for (Module module : (List<Module>)moduleRegistry.getRegistry()) {
            if (!(module instanceof HudWidgetModule)) {
               JsonObject moduleJson = new JsonObject();
               Keybind keybind = module.getKeybind();
               moduleJson.addProperty("key", keybind.code());
               keybind = module.getKeybind();
               Keybind.Mode keyMode = keybind.mode();
               String modeName = keyMode.name();
               String modeValue = modeName.toLowerCase();
               moduleJson.addProperty("state", modeValue);
               keybind = module.getKeybind();
               moduleJson.addProperty("mouse", keybind.mouse());
               String moduleName = module.getConfigName();
               preset.add(moduleName, moduleJson);
            }
         }

         return preset;
      }
   },
   HUD("Hud") {
      @Override
      public void fromJson(JsonElement json) {
         // ImportedPresetMixin handles HUD preset application.
      }

      @Override
      public JsonElement toJson() {
         JsonObject preset = new JsonObject();
         this.writeMetadata(preset);
         ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;

         for (Module module : (List<Module>)moduleRegistry.getRegistry()) {
            if (this.isHudModule(module)) {
               JsonElement moduleElement = module.toJson();
               JsonObject moduleJson = moduleElement.getAsJsonObject();
               String[] bindingKeys = new String[]{"key", "state", "mouse"};
               JsonUtils.removeProperties(moduleJson, bindingKeys);
               String moduleName = module.getConfigName();
               preset.add(moduleName, moduleJson);
            }
         }

         return preset;
      }

      private boolean isHudModule(Module module) {
         return module instanceof HudWidgetModule || module instanceof HUDModule;
      }
   },
   COLORS("Colors") {
      @Override
      public void fromJson(JsonElement json) {
         ModulePresetFilter filter = ModulePresetFilter.INSTANCE;
         JsonObject object = json.getAsJsonObject();
         Predicate<Module> includeModule = module -> false;
         filter.retainModuleSettings(object, includeModule);
         super.fromJson(json);
      }

      @Override
      public JsonElement toJson() {
         JsonObject preset = new JsonObject();
         this.writeMetadata(preset);
         ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;

         for (Module module : (List<Module>)moduleRegistry.getRegistry()) {
            if (!(module instanceof HudWidgetModule)) {
               JsonObject moduleJson = new JsonObject();
               JsonObject settingsJson = new JsonObject();

               for (Setting<?> setting : module.getSettings()) {
                  if (setting instanceof ColorSetting && !setting.isExcludedFromConfig()) {
                     String settingName = setting.getConfigName();
                     JsonElement settingJson = setting.toJson();
                     settingsJson.add(settingName, settingJson);
                  }
               }

               moduleJson.add("settings", settingsJson);
               if (!JsonUtils.isRecursivelyEmpty(moduleJson)) {
                  String moduleName = module.getConfigName();
                  preset.add(moduleName, moduleJson);
               }
            }
         }

         return preset;
      }
   },
   VISUALS("Visuals") {
      private static final Set<Class<?>> VISUAL_MODULE_CLASSES;

      @Override
      public void fromJson(JsonElement json) {
         ModulePresetFilter filter = ModulePresetFilter.INSTANCE;
         JsonObject object = json.getAsJsonObject();
         Predicate<Module> includeModule = this::isVisualModule;
         filter.retainModuleSettings(object, includeModule);
         super.fromJson(json);
      }

      @Override
      public JsonElement toJson() {
         JsonObject preset = new JsonObject();
         this.writeMetadata(preset);
         ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;

         for (Module module : (List<Module>)moduleRegistry.getRegistry()) {
            if (!(module instanceof HudWidgetModule)) {
               JsonObject moduleJson = new JsonObject();
               boolean visualModule = this.isVisualModule(module);
               boolean logoutSpots = module instanceof LogoutSpotsModule;
               if (visualModule && !logoutSpots) {
                  moduleJson.addProperty("enabled", module.isToggled());
               }

               JsonObject settingsJson = new JsonObject();

               for (Setting<?> setting : module.getSettings()) {
                  if (!setting.isExcludedFromConfig() && (this.isVisualSetting(setting) || visualModule)) {
                     String settingName = setting.getConfigName();
                     JsonElement settingJson = setting.toJson();
                     settingsJson.add(settingName, settingJson);
                  }
               }

               moduleJson.add("settings", settingsJson);
               if (!JsonUtils.isRecursivelyEmpty(moduleJson)) {
                  String moduleName = module.getConfigName();
                  preset.add(moduleName, moduleJson);
               }
            }
         }

         return preset;
      }

      boolean isVisualModule(Module module) {
         if (module instanceof HudWidgetModule) {
            return true;
         } else {
            return VISUAL_MODULE_CLASSES.contains(module.getClass())
               ? true
               : module.getCategory() == ModuleCategory.RENDER || module.getCategory() == ModuleCategory.HUD;
         }
      }

      boolean isVisualSetting(Setting<?> setting) {
         String settingName = setting.getName();
         if (settingName.equalsIgnoreCase("render")) {
            return true;
         }

         settingName = setting.getName();
         if (settingName.equalsIgnoreCase("linewidth")) {
            return true;
         }

         settingName = setting.getName();
         if (!settingName.equalsIgnoreCase("colormode")) {
            settingName = setting.getName();
            if (!settingName.equalsIgnoreCase("rendermode")) {
               return setting instanceof ColorSetting;
            }
         }

         return true;
      }

      static {
         Class<KillEffectsModule> killEffectsClass = KillEffectsModule.class;
         Class<SwingModule> swingClass = SwingModule.class;
         Class<NotificationsModule> notificationsClass = NotificationsModule.class;
         Class<ColorsModule> colorsClass = ColorsModule.class;
         Class<HUDModule> hudClass = HUDModule.class;
         Class<FontsModule> fontsClass = FontsModule.class;
         Class<UIModule> uiClass = UIModule.class;
         VISUAL_MODULE_CLASSES = new HashSet<>(List.of(uiClass, fontsClass, hudClass, colorsClass, notificationsClass, swingClass, killEffectsClass));
      }
   },
   MACRO("Macro") {
      @Override
      public void fromJson(JsonElement json) {
         MacroRegistry registryForLock = ClientContext.macroRegistry;
         synchronized ((List)registryForLock.getRegistry()) {
            MacroRegistry registryForClear = ClientContext.macroRegistry;
            ((List)registryForClear.getRegistry()).clear();
            MacroRegistry registryForLoad = ClientContext.macroRegistry;
            registryForLoad.fromJson(json);
         }
      }

      @Override
      public JsonElement toJson() {
         MacroRegistry macroRegistry = ClientContext.macroRegistry;
         return macroRegistry.toJson();
      }
   },
   ALL("All") {
      @Override
      public JsonElement toJson() {
         ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
         JsonElement modulesJson = moduleRegistry.toJson();
         return modulesJson.getAsJsonObject();
      }
   };

   private final String displayName;
   private final Path directory;

   public Path getDirectory() {
      return this.directory;
   }

   @Override
   public String getName() {
      return this.displayName;
   }

   PresetCategory(String displayName) {
      this.displayName = displayName;
      Path presetsDirectory = ConfigurationManager.PRESETS_DIRECTORY;
      this.directory = presetsDirectory.resolve(displayName.toLowerCase());
   }

   @Override
   public void fromJson(JsonElement json) {
      JsonObject var3 = json.getAsJsonObject().deepCopy();
      var3.remove("HUD");
      var3.remove("UI");
      var3.remove("Colors");
      var3.remove("Fonts");
      ModuleRegistry var2 = ClientContext.moduleRegistry;
      var2.fromJson(var3);
   }

   public void writeMetadata(JsonObject json) {
      json.addProperty("client", "argusclient");
      String categoryName = this.getName();
      Locale locale = Locale.ROOT;
      String categoryValue = categoryName.toLowerCase(locale);
      json.addProperty("category", categoryValue);
   }

   public static PresetCategory findByName(String name) {
      for (PresetCategory category : values()) {
         String categoryName = category.getName();
         if (categoryName.equalsIgnoreCase(name)) {
            return category;
         }
      }

      return null;
   }
}
