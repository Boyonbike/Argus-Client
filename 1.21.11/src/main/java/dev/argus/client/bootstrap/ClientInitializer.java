package dev.argus.client.bootstrap;

import dev.argus.client.ArgusClient;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.ClientInitializationEvent;
import dev.argus.client.integrations.baritone.BaritoneAdapter;
import dev.argus.client.integrations.baritone.DisabledBaritoneBridge;
import dev.argus.client.integrations.xaero.DisabledWaypointAdapter;
import dev.argus.client.integrations.xaero.XaeroWaypointAdapter;
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
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.managers.SoundManager;
import dev.argus.client.managers.StashRegistry;
import dev.argus.client.managers.SwingInteractionManager;
import dev.argus.client.managers.TaskScheduler;
import dev.argus.client.managers.TickRateController;
import dev.argus.client.managers.TradeRegistry;
import dev.argus.client.managers.WaypointRegistry;
import dev.argus.client.managers.WorldStateManager;
import dev.argus.client.render.FeatureBoxRenderer;
import dev.argus.client.render.FontSupport;
import dev.argus.client.render.text.LanguageTable;
import dev.argus.client.utils.FrameTiming;
import dev.argus.client.utils.combat.TotemPopTracker;
import dev.argus.client.utils.entity.PlayerStateTracker;
import dev.argus.client.utils.inventory.CustomItemRegistries;
import dev.argus.client.utils.movement.MovementSpeedTracker;
import dev.argus.client.utils.world.BlockBreakTracker;
import dev.argus.client.utils.world.MapDataCache;
import java.util.Locale;
import java.util.Locale.Category;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

public final class ClientInitializer {
   private boolean managersInitialized;
   private boolean clientInitialized;

   public void initialize() {
      requireEnvironment();
      if (!this.managersInitialized) {
         ClientContext.baritoneBridge = FabricLoader.getInstance().isModLoaded("baritone-meteor") ? new BaritoneAdapter() : new DisabledBaritoneBridge();
         ClientContext.waypointAdapter = FabricLoader.getInstance().isModLoaded("xaerominimap")
            ? new XaeroWaypointAdapter() : new DisabledWaypointAdapter();
         FontSupport.initialize();
         ClientContext.languageTable = new LanguageTable();
         ClientContext.inputOverrides = new InputOverrideManager();
         ClientContext.blockColorRegistry = new BlockColorRegistry();
         ClientContext.playerStateTracker = new PlayerStateTracker();
         ClientContext.serverStateManager = new ServerStateManager();
         ClientContext.blockBreakTracker = new BlockBreakTracker();
         ClientContext.tickRateController = new TickRateController();
         ClientContext.playerTickController = new TickRateController.PlayerTicks();
         ClientContext.movementPredictionManager = new MovementPredictionManager();
         ClientContext.holeManager = new HoleManager();
         ClientContext.totemPopTracker = new TotemPopTracker();
         ClientContext.movementSpeedTracker = new MovementSpeedTracker();
         ClientContext.rotationManager = new RotationManager();
         ClientContext.waypointRegistry = new WaypointRegistry();
         ClientContext.featureBoxRenderer = new FeatureBoxRenderer();
         ClientContext.taskScheduler = new TaskScheduler();
         ClientContext.moduleRegistry = new ModuleRegistry();
         ClientContext.hudModuleRegistry = new HudModuleRegistry();
         ClientContext.tradeRegistry = new TradeRegistry();
         ClientContext.commandRegistry = new CommandRegistry();
         ClientContext.frameTiming = new FrameTiming();
         ClientContext.macroRegistry = new MacroRegistry();
         ClientContext.socialRegistry = new SocialRegistry();
         ClientContext.soundManager = new SoundManager();
         ClientContext.inventoryStateManager = new InventoryStateManager();
         ClientContext.stashRegistry = new StashRegistry();
         ClientContext.worldStateManager = new WorldStateManager();
         ClientContext.crystalInteractionManager = new CrystalInteractionManager();
         ClientContext.mapDataCache = new MapDataCache();
         ClientContext.kitRegistry = new KitRegistry();
         ClientContext.aliasRegistry = new AliasRegistry();
         ClientContext.chatFilterRegistry = new ChatFilterRegistry();
         ClientContext.glintRegistry = new GlintRegistry();
         ClientContext.autoPresetRegistry = new AutoPresetRegistry();
         ClientContext.swingManager = new SwingInteractionManager();
         Locale.setDefault(Category.FORMAT, Locale.US);
         this.managersInitialized = true;
         ArgusClient.LOGGER.info("Argus Client main initialization completed.");
      }
   }

   public void initializeClient() {
      requireEnvironment();
      if (!this.managersInitialized) {
         throw new IllegalStateException("Argus Client client initialization requires the main phase.");
      }

      if (!this.clientInitialized) {
         ModuleDependencies.bind(ClientContext.moduleRegistry);
         ClientAccess.eventBus.post(new ClientInitializationEvent());
         ClientContext.soundManager.reload();
         ClientContext.configurationManager = new ConfigurationManager();
         ConfigurationManager configuration = ClientContext.configurationManager;
         CustomItemRegistries.initialize();
         configuration.load();
         this.clientInitialized = true;
         ArgusClient.LOGGER.info("Argus Client client initialization completed.");
      }
   }

   private static void requireEnvironment() {
      FabricLoader fabric = FabricLoader.getInstance();
      if (fabric.getEnvironmentType() == EnvType.CLIENT && Runtime.version().feature() >= 21) {
         String minecraft = ((ModContainer)fabric.getModContainer("minecraft")
               .orElseThrow(() -> new IllegalStateException("Minecraft mod container is missing.")))
            .getMetadata()
            .getVersion()
            .getFriendlyString();
         if (!"1.21.11".equals(minecraft)) {
            throw new IllegalStateException("Argus Client requires Minecraft 1.21.11; found " + minecraft);
         }
      } else {
         throw new IllegalStateException("Argus Client requires the Fabric client environment and Java 21 or newer.");
      }
   }
}
