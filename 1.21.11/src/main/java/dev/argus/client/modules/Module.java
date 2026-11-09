package dev.argus.client.modules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.NamedFeature;
import dev.argus.client.config.LegacyBranding;
import dev.argus.client.events.ModuleToggleEvent;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.managers.AliasRegistry;
import dev.argus.client.render.text.LanguageTable;
import dev.argus.client.settings.JsonConfigurable;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.Toggleable;
import dev.argus.client.utils.inventory.RegistryContainer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.ChatFormatting;

public class Module extends NamedFeature implements Toggleable, ClientAccess, RegistryContainer, JsonConfigurable {
   private final String descriptionTranslationKey = "descriptions.modules." + this.getName();
   private final ModuleCategory category;
   private final List<Setting<?>> settings = new ArrayList<>();
   private final String[] aliases;
   private Keybind keybind = Keybind.UNBOUND;
   private boolean enabled;
   private boolean drawn = true;
   private boolean workInProgress;
   private boolean ignoreEnabledState;

   public Module(String name, String description, ModuleCategory category, String... aliases) {
      super(name);
      this.category = category;
      this.setDescription(description);
      String[] suppliedAliases = Objects.requireNonNullElseGet(aliases, () -> new String[0]);
      this.aliases = Arrays.copyOf(suppliedAliases, suppliedAliases.length + 1);
      this.aliases[suppliedAliases.length] = name;
   }

   public Module(String name, ModuleCategory category, String... aliases) {
      this(name, "", category, aliases);
   }

   public <S extends Setting<?>> S add(S setting) {
      this.register(setting);
      return setting;
   }

   public <S extends Setting<?>> S add(S setting, Setting<?> precedingSetting) {
      int insertionIndex = this.settings.indexOf(precedingSetting) + 1;
      this.settings.add(insertionIndex, setting);
      return setting;
   }

   public boolean register(Setting<?> setting) {
      return this.settings.add(setting);
   }

   @Override
   public void enable() {
      if (!this.isToggled()) {
         this.enabled = true;
         ClientAccess.eventBus.subscribe(this);
         EventBus eventBus = ClientAccess.eventBus;
         ModuleToggleEvent toggleEvent = new ModuleToggleEvent(this);
         eventBus.post(toggleEvent);
         this.onToggle();
         this.onEnable();
      }
   }

   public boolean unregister(Setting<?> setting) {
      return this.settings.remove(setting);
   }

   public String[] getAliases() {
      return this.aliases;
   }

   public String getInfo() {
      return null;
   }

   @Override
   public boolean isToggled() {
      return this.enabled;
   }

   public void onEnable() {
   }

   public void onToggle() {
   }

   public boolean isWip() {
      return this.workInProgress;
   }

   public boolean isDrawn() {
      return this.drawn;
   }

   public void setKeybind(Keybind keybind) {
      this.keybind = keybind;
   }

   protected void setWip(boolean workInProgress) {
      this.workInProgress = workInProgress;
   }

   public Keybind getKeybind() {
      return this.keybind;
   }

   public void onDisable() {
   }

   public void setDrawn(boolean drawn) {
      this.drawn = drawn;
   }

   @Override
   public String getDescription() {
      LanguageTable languageTable = ClientContext.languageTable;
      String translationKey = this.descriptionTranslationKey;
      String defaultDescription = super.getDescription();
      return languageTable.getTranslationOrDefault(translationKey, defaultDescription);
   }

   @Override
   public void fromJson(JsonElement json) {
      if (LegacyBranding.migrateModule(this.getName(), json) instanceof JsonObject moduleJson) {
         if (moduleJson.has("settings") && moduleJson.get("settings") instanceof JsonObject settingsJson) {
            for (Setting<?> setting : this.getSettings()) {
               if (!setting.isExcludedFromConfig() && settingsJson.has(setting.getConfigName())) {
                  try {
                     setting.fromJson(settingsJson.get(setting.getConfigName()));
                  } catch (Exception var7) {
                  }
               }
            }
         }

         if (moduleJson.has("drawn")) {
            this.setDrawn(moduleJson.get("drawn").getAsBoolean());
         }

         if (moduleJson.has("key")) {
            Function<Keybind, Keybind> keybindModifier = keybind -> {
               int keyCode = moduleJson.get("key").getAsInt();
               Keybind codedKeybind = keybind.withCode(keyCode);
               Keybind.Mode mode = moduleJson.has("state") ? Keybind.Mode.fromSerializedName(moduleJson.get("state").getAsString()) : Keybind.Mode.TOGGLE;
               return codedKeybind.withMode(mode);
            };
            this.modifyKeybind(keybindModifier);
         }

         if (moduleJson.has("mouse")) {
            Function<Keybind, Keybind> mouseModifier = keybind -> keybind.withMouse(moduleJson.get("mouse").getAsBoolean());
            this.modifyKeybind(mouseModifier);
         }

         if (moduleJson.has("enabled") && !this.ignoreEnabledState) {
            this.setToggled(moduleJson.get("enabled").getAsBoolean());
         }
      }
   }

   @Override
   public JsonElement toJson() {
      JsonObject moduleJson = new JsonObject();
      if (!this.ignoreEnabledState) {
         moduleJson.addProperty("enabled", this.enabled);
      }

      moduleJson.addProperty("key", this.keybind.code());
      moduleJson.addProperty("state", this.keybind.mode().getSerializedName());
      moduleJson.addProperty("mouse", this.keybind.mouse());
      moduleJson.addProperty("drawn", this.drawn);
      JsonObject settingsJson = new JsonObject();

      for (Setting<?> setting : this.getSettings()) {
         if (!setting.isGroupOnly() && !setting.isExcludedFromConfig()) {
            String configName = setting.getConfigName();
            JsonElement settingJson = setting.toJson();
            settingsJson.add(configName, settingJson);
         }
      }

      moduleJson.add("settings", settingsJson);
      return moduleJson;
   }

   public ModuleCategory getCategory() {
      return this.category;
   }

   @Override
   public void disable() {
      if (this.isToggled()) {
         ClientAccess.eventBus.unsubscribe(this);
         this.enabled = false;
         EventBus eventBus = ClientAccess.eventBus;
         ModuleToggleEvent toggleEvent = new ModuleToggleEvent(this);
         eventBus.post(toggleEvent);
         this.onToggle();
         this.onDisable();
      }
   }

   public String getInfoString() {
      AliasRegistry aliasRegistry = ClientContext.aliasRegistry;
      String moduleAlias = AliasRegistry.getModuleAlias(aliasRegistry, this);
      String infoSuffix;
      if (this.getInfo() != null) {
         String openingColor = String.valueOf(ChatFormatting.GRAY);
         String infoColor = String.valueOf(ChatFormatting.WHITE);
         String info = this.getInfo();
         String closingColor = String.valueOf(ChatFormatting.GRAY);
         infoSuffix = openingColor + " [" + infoColor + info + closingColor + "]";
      } else {
         infoSuffix = "";
      }

      return moduleAlias + infoSuffix;
   }

   public void modifyKeybind(Function modifier) {
      this.keybind = (Keybind)modifier.apply(this.keybind);
   }

   @Override
   public String getConfigName() {
      return this.getName();
   }

   protected void ignoreState() {
      this.ignoreEnabledState = true;
   }

   public List<Setting<?>> getSettings() {
      return this.settings;
   }

   @Override
   public boolean unregister(Object entry) {
      return this.unregister((Setting<?>)entry);
   }

   @Override
   public boolean register(Object entry) {
      return this.register((Setting<?>)entry);
   }

   @Override
   public Collection getRegistry() {
      return this.getSettings();
   }
}
