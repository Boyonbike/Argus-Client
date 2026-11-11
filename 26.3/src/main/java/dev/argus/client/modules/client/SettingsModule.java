package dev.argus.client.modules.client;

import dev.argus.client.audio.BuiltinSound;
import dev.argus.client.gui.screens.PresetScreen;
import dev.argus.client.managers.CommandRegistry;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.PermanentModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.IdentifierSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.StringSetting;
import dev.argus.client.utils.collections.NamedIdentifier;

public class SettingsModule extends PermanentModule {
   public final Setting<String> prefixSetting = this.add(new StringSetting("Prefix", ";"));
   private final Setting<NamedIdentifier> startupSetting = this.add(new IdentifierSetting("Startup", BuiltinSound.BLIP.getIdentifier()));
   private final Setting<Boolean> presetsSetting = this.add(new BooleanSetting("Presets", true));

   private static void updateCommandPrefix(SettingsModule owner, String prefixValue) {
      if (!prefixValue.equals(CommandRegistry.getPrefix())) {
         String updatedPrefix = owner.prefixSetting.getValue();
         CommandRegistry.setPrefix(updatedPrefix);
      }
   }

   public NamedIdentifier getStartupSound() {
      return this.startupSetting.getValue();
   }

   public SettingsModule() {
      super("Settings", "Client's global settings.", ModuleCategory.CLIENT);
      this.prefixSetting.setChangeCallback(() -> {
         String prefixValue = this.prefixSetting.getValue();
         updateCommandPrefix(this, prefixValue);
      });
      this.presetsSetting.setChangeCallback(() -> {
         if (!this.presetsSetting.getValue()) {
            new PresetScreen().open();
            this.presetsSetting.setValue(true);
         }
      });
      this.prefixSetting.setExcludedFromConfig(true);
   }

   public enum MultiplierOption implements NamedEntry {
      NONE("None", 0),
      X2("X2", 2),
      X4("X4", 4),
      X8("X8", 8);

      private final String displayName;
      private final int multiplier;

      MultiplierOption(String displayName, int multiplier) {
         this.displayName = displayName;
         this.multiplier = multiplier;
      }

      @Override
      public String getName() {
         return this.displayName;
      }

      public int getMultiplier() {
         return this.multiplier;
      }
   }
}
