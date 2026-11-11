package dev.argus.client.modules.client;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftTickEvent;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.PermanentModule;
import dev.argus.client.modules.movement.SafeWalkModule;
import dev.argus.client.modules.movement.StepModule;
import dev.argus.client.modules.player.AutoToolModule;
import dev.argus.client.render.ColorUtils;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.Setting;
import java.awt.Color;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class BaritoneModule extends PermanentModule {
   private static StepModule step = ClientContext.getModule(StepModule.class);
   private static AutoToolModule autoTool = ClientContext.getModule(AutoToolModule.class);
   private static SafeWalkModule safeWalk = ClientContext.getModule(SafeWalkModule.class);
   private final Setting<Boolean> freeLookSetting = this.add(new BooleanSetting("FreeLook", true));
   private final Setting<Boolean> backFillSetting = this.add(new BooleanSetting("BackFill", false));
   private final Setting<Boolean> logOnArrivalSetting = this.add(new BooleanSetting("LogOnArrival", false));
   private final Setting<Boolean> assumeEnabledSetting = this.add(new BooleanSetting("AssumeEnabled", false));
   private final Setting<Boolean> censorCoordsSetting = this.add(new BooleanSetting("CensorCoords", false));
   private final Setting<Boolean> replaceColorsSetting;
   private final Setting<Color> colorSetting;
   private final Setting<Color> goalSetting;
   private List<BaritoneModule.AssumedSetting> assumedSettings;

   private List<BaritoneModule.AssumedSetting> getAssumedSettings() {
      if (this.assumedSettings == null) {
         this.assumedSettings = BaritoneModule.AssumedSetting.create(step, autoTool, safeWalk);
      }

      return this.assumedSettings;
   }

   @EventHandler
   private void onMinecraftTick(MinecraftTickEvent event) {
      if (ClientContext.baritoneBridge.isAvailable()) {
         Settings settings = BaritoneAPI.getSettings();
         baritone.api.Settings.Setting targetSetting = settings.disconnectOnArrival;
         targetSetting.value = this.logOnArrivalSetting.getValue();
         targetSetting = settings.backfill;
         Setting<Boolean> backFillSetting = this.backFillSetting;
         targetSetting.value = backFillSetting.getValue();
         targetSetting = settings.freeLook;
         Setting<Boolean> freeLookSetting = this.freeLookSetting;
         targetSetting.value = freeLookSetting.getValue();
         targetSetting = settings.censorCoordinates;
         targetSetting.value = this.censorCoordsSetting.getValue();
         targetSetting = settings.censorRanCommands;
         targetSetting.value = this.censorCoordsSetting.getValue();
         Setting<Boolean> replaceColorsSetting = this.replaceColorsSetting;
         Boolean replaceColors = replaceColorsSetting.getValue();
         if (replaceColors) {
            Setting<Color> pathColorSetting = this.colorSetting;
            Color configuredPathColor = pathColorSetting.getValue();
            Color pathColor = ColorUtils.withAlpha(configuredPathColor, 255);
            Setting<Color> goalColorSetting = this.goalSetting;
            Color configuredGoalColor = goalColorSetting.getValue();
            Color goalColor = ColorUtils.withAlpha(configuredGoalColor, 255);
            settings.colorBestPathSoFar.value = pathColor;
            settings.colorGoalBox.value = goalColor;
            settings.colorInvertedGoalBox.value = goalColor;
            settings.colorCurrentPath.value = pathColor;
            settings.colorMostRecentConsidered.value = pathColor;
            settings.colorBlocksToBreak.value = pathColor;
            settings.colorBlocksToPlace.value = pathColor;
            settings.colorBlocksToWalkInto.value = pathColor;
            settings.colorNextPath.value = pathColor;
            targetSetting = settings.pathRenderLineWidthPixels;
            targetSetting.value = 1.5F;
            targetSetting = settings.goalRenderLineWidthPixels;
            targetSetting.value = 1.5F;
         }

         Setting<Boolean> assumeEnabledSetting = this.assumeEnabledSetting;
         Boolean assumeEnabled = assumeEnabledSetting.getValue();
         if (assumeEnabled) {
            this.getAssumedSettings().forEach(BaritoneModule.AssumedSetting::sync);
         }
      }
   }

   public static void bindModuleDependencies(ModuleRegistry registry) {
      step = (StepModule)registry.getModule(StepModule.class);
      autoTool = (AutoToolModule)registry.getModule(AutoToolModule.class);
      safeWalk = (SafeWalkModule)registry.getModule(SafeWalkModule.class);
   }

   public BaritoneModule() {
      super("Baritone", "Manages baritone settings.", ModuleCategory.CLIENT);
      BooleanSetting replaceColorsSetting = new BooleanSetting("ReplaceColors", true);
      replaceColorsSetting.markVisibilityDependency();
      this.replaceColorsSetting = this.add(replaceColorsSetting);
      this.colorSetting = this.add(new ColorSetting("Color", new Color(-4351489, true)).dependsOn(this.replaceColorsSetting));
      this.goalSetting = this.add(new ColorSetting("Goal", new Color(-4351489, true)).dependsOn(this.replaceColorsSetting));
      if (ClientContext.baritoneBridge.isAvailable()) {
         baritone.api.Settings.Setting chatControlSetting = BaritoneAPI.getSettings().chatControl;
         chatControlSetting.value = false;
      }

      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   private static class AssumedSetting {
      private final Function<Settings, baritone.api.Settings.Setting<Boolean>> settingSelector;
      private final Supplier<Boolean> moduleEnabled;
      private boolean lastEnabledValue;

      private static List<BaritoneModule.AssumedSetting> create(StepModule step, AutoToolModule autoTool, SafeWalkModule safeWalk) {
         return List.of(
            new BaritoneModule.AssumedSetting(settings -> settings.assumeStep, step::isToggled),
            new BaritoneModule.AssumedSetting(settings -> settings.assumeExternalAutoTool, autoTool::isToggled),
            new BaritoneModule.AssumedSetting(settings -> settings.assumeSafeWalk, safeWalk::isToggled)
         );
      }

      private AssumedSetting(Function<Settings, baritone.api.Settings.Setting<Boolean>> settingSelector, Supplier<Boolean> moduleEnabled) {
         this.settingSelector = settingSelector;
         this.moduleEnabled = moduleEnabled;
         this.lastEnabledValue = (Boolean)this.getSetting().value;
      }

      public void sync() {
         boolean enabled = this.moduleEnabled.get();
         if (enabled) {
            this.getSetting().value = true;
            this.lastEnabledValue = true;
         } else if (this.lastEnabledValue) {
            this.getSetting().value = false;
            this.lastEnabledValue = false;
         }
      }

      private baritone.api.Settings.Setting<Boolean> getSetting() {
         return this.settingSelector.apply(BaritoneAPI.getSettings());
      }
   }
}
