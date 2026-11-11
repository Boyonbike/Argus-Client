package dev.argus.client;

import dev.argus.client.gui.screens.ClickGuiScreen;
import dev.argus.client.gui.screens.HudEditorScreen;
import dev.argus.client.integrations.baritone.BaritoneBridge;
import dev.argus.client.integrations.xaero.WaypointAdapter;
import dev.argus.client.managers.AliasRegistry;
import dev.argus.client.managers.AutoPresetRegistry;
import dev.argus.client.managers.BlockColorRegistry;
import dev.argus.client.managers.ChatFilterRegistry;
import dev.argus.client.managers.CommandRegistry;
import dev.argus.client.managers.ConfigurationManager;
import dev.argus.client.managers.CrystalInteractionManager;
import dev.argus.client.managers.GlintRegistry;
import dev.argus.client.managers.HoleManager;
import dev.argus.client.managers.HudModuleRegistry;
import dev.argus.client.managers.InputOverrideManager;
import dev.argus.client.managers.InventoryStateManager;
import dev.argus.client.managers.KitRegistry;
import dev.argus.client.managers.MacroRegistry;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.MovementPredictionManager;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.ServerStateManager;
import dev.argus.client.managers.ShaderRegistry;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.managers.SoundManager;
import dev.argus.client.managers.StashRegistry;
import dev.argus.client.managers.SwingInteractionManager;
import dev.argus.client.managers.TaskScheduler;
import dev.argus.client.managers.TickRateController;
import dev.argus.client.managers.TradeRegistry;
import dev.argus.client.managers.WaypointRegistry;
import dev.argus.client.managers.WorldStateManager;
import dev.argus.client.modules.Module;
import dev.argus.client.render.FeatureBoxRenderer;
import dev.argus.client.render.text.LanguageTable;
import dev.argus.client.utils.FrameTiming;
import dev.argus.client.utils.combat.TotemPopTracker;
import dev.argus.client.utils.entity.PlayerStateTracker;
import dev.argus.client.utils.movement.MovementSpeedTracker;
import dev.argus.client.utils.world.BlockBreakTracker;
import dev.argus.client.utils.world.MapDataCache;
import java.util.Objects;

public final class ClientContext {
   public static TickRateController tickRateController;
   public static ConfigurationManager configurationManager;
   public static RotationManager rotationManager;
   public static volatile ModuleRegistry moduleRegistry;
   public static CommandRegistry commandRegistry;
   public static FrameTiming frameTiming;
   public static ServerStateManager serverStateManager;
   public static SocialRegistry socialRegistry;
   public static WaypointRegistry waypointRegistry;
   public static HoleManager holeManager;
   public static SoundManager soundManager;
   public static MacroRegistry macroRegistry;
   public static InventoryStateManager inventoryStateManager;
   public static MovementPredictionManager movementPredictionManager;
   public static TotemPopTracker totemPopTracker;
   public static MovementSpeedTracker movementSpeedTracker;
   public static PlayerStateTracker playerStateTracker;
   public static FeatureBoxRenderer featureBoxRenderer;
   public static TickRateController.PlayerTicks playerTickController;
   public static StashRegistry stashRegistry;
   public static TaskScheduler taskScheduler;
   public static HudModuleRegistry hudModuleRegistry;
   public static WorldStateManager worldStateManager;
   public static CrystalInteractionManager crystalInteractionManager;
   public static MapDataCache mapDataCache;
   public static KitRegistry kitRegistry;
   public static AliasRegistry aliasRegistry;
   public static ChatFilterRegistry chatFilterRegistry;
   public static GlintRegistry glintRegistry;
   public static BlockBreakTracker blockBreakTracker;
   public static ShaderRegistry shaderRegistry;
   public static TradeRegistry tradeRegistry;
   public static BlockColorRegistry blockColorRegistry;
   public static AutoPresetRegistry autoPresetRegistry;
   public static InputOverrideManager inputOverrides;
   public static LanguageTable languageTable;
   public static SwingInteractionManager swingManager;
   public static BaritoneBridge baritoneBridge;
   public static WaypointAdapter waypointAdapter;
   public static ClickGuiScreen clickGui;
   private static HudEditorScreen hudEditor;

   private ClientContext() {
   }

   public static <T extends Module> T getModule(Class<T> moduleType) {
      Objects.requireNonNull(moduleType, "Module type");
      ModuleRegistry registry = moduleRegistry;
      return registry == null ? null : moduleType.cast(registry.getModule(moduleType));
   }

   public static ClickGuiScreen getClickGui() {
      if (clickGui == null) {
         clickGui = new ClickGuiScreen();
      }

      return clickGui;
   }

   public static HudEditorScreen getHudEditor() {
      if (hudEditor == null) {
         hudEditor = new HudEditorScreen();
      }

      return hudEditor;
   }
}
