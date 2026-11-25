package dev.argus.client.modules.movement.elytratrip;

import dev.argus.client.ClientContext;
import dev.argus.client.chat.SetHomeStore;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.KeyboardInputTickEvent;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.MinecraftTickEvent;
import dev.argus.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.StringSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Flies unattended under the Nether roof to a chosen X/Z: Baritone elytra flight, eating, in-flight elytra swaps, a rolling
 * checkpoint home, station refills, death and disconnect recovery, Discord updates, and a disconnect on arrival. Every part
 * is ElytraTrip's own; the trip logic lives in {@link TripCoordinator}.
 */
public final class ElytraTripModule extends Module implements StationChests.Host {
   static final String TAB_TRIP = "Trip";
   static final String TAB_SURVIVAL = "Survival";
   static final String TAB_REFILL = "Refill";
   static final String TAB_HOMES = "Homes";
   static final String TAB_CONNECTION = "Connection";
   static final String TAB_DISCORD = "Discord";

   /** A saved destination. */
   public record Destination(String name, int x, int z) {
   }

   private final Minecraft mc = Minecraft.getInstance();

   // Trip
   private final Setting<Boolean> tripGroup = group("Trip", TAB_TRIP);
   final Setting<Float> speed = add(new NumberSetting<>("Speed", 40.0F, 5.0F, 120.0F).withUnit("b/s"));
   final Setting<Boolean> strictRoute = add(new BooleanSetting("Strict route", false).markVisibilityDependency());
   final Setting<Float> strictTolerance = add(new NumberSetting<>("Strict tolerance", 0.2F, 0.05F, 4.0F, value -> strictRoute.getValue())
      .withUnit("m"));
   final Setting<Integer> escapeDistance = add(new NumberSetting<>("Escape distance", 48, 8, 512).withUnit("m"));
   final Setting<Boolean> flyThroughLava = add(new BooleanSetting("Fly through lava", true).markVisibilityDependency());
   final Setting<Boolean> thinLavaOnly = add(new BooleanSetting("Thin lava only", true, value -> flyThroughLava.getValue()));
   final Setting<Boolean> drinkFireResistance = add(new BooleanSetting("Drink Fire Resistance", true));
   final Setting<Float> lavaMinHealth = add(new NumberSetting<>("Lava min health", 14.0F, 0.0F, 20.0F, value -> flyThroughLava.getValue()));
   final Setting<Boolean> showRoute = add(new BooleanSetting("Show route", true).markVisibilityDependency());
   final Setting<java.awt.Color> routeColor = add(new dev.argus.client.settings.ColorSetting("Route colour",
      new java.awt.Color(80, 200, 255, 220), color -> showRoute.getValue()));
   final Setting<Integer> arriveWithin = add(new NumberSetting<>("Arrive within", 50, 10, 500).withUnit("m"));
   final Setting<Integer> swapBelow = add(new NumberSetting<>("Swap below", 30, 5, 200));
   final Setting<Boolean> setupMode = add(new BooleanSetting("Setup mode", false));
   final Setting<String> destination = add(new StringSetting("Destination", "", value -> false));
   final Setting<String> saved = add(new StringSetting("Saved destinations", "", value -> false));
   // Survival
   private final Setting<Boolean> survivalGroup = group("Survival", TAB_SURVIVAL);
   final Setting<Integer> eatAtHealth = add(new NumberSetting<>("Eat at health", 14, 1, 19));
   final Setting<Integer> eatAtHunger = add(new NumberSetting<>("Eat at hunger", 14, 1, 19));
   // Refill
   private final Setting<Boolean> refillGroup = group("Refill", TAB_REFILL);
   final Setting<Integer> spareMin = add(new NumberSetting<>("Spare elytra minimum", 1, 0, 10));
   final Setting<Integer> totemMin = add(new NumberSetting<>("Totem minimum", 1, 0, 10));
   final Setting<Integer> gappleMin = add(new NumberSetting<>("Gapple minimum", 2, 0, 64));
   final Setting<Integer> foodMin = add(new NumberSetting<>("Food minimum", 16, 0, 256));
   final Setting<Integer> armourMin = add(new NumberSetting<>("Armour minimum", 15, 0, 90).withUnit("%"));
   final Setting<Integer> elytraTarget = add(new NumberSetting<>("Elytra target", 6, 2, 30));
   final Setting<Boolean> fillEmpty = add(new BooleanSetting("Fill empty slots with elytras", true));
   final Setting<Integer> totemTarget = add(new NumberSetting<>("Totem target", 4, 0, 36));
   final Setting<Integer> gappleTarget = add(new NumberSetting<>("Gapple target", 16, 0, 256));
   final Setting<Integer> foodTarget = add(new NumberSetting<>("Food target", 64, 0, 512));
   final Setting<Integer> wornBelow = add(new NumberSetting<>("Dump elytras below", 100, 11, 400));
   final Setting<Integer> freshAbove = add(new NumberSetting<>("Take elytras above", 75, 10, 100).withUnit("%"));
   final Setting<Boolean> dumpJunk = add(new BooleanSetting("Dump everything else", false));
   final Setting<String> loadout = add(new StringSetting("Saved loadout", "", value -> false));
   // Homes
   private final Setting<Boolean> homesGroup = group("Homes", TAB_HOMES);
   final Setting<String> stationHome = add(new StringSetting("Station home", "ely"));
   final Setting<String> checkpointHome = add(new StringSetting("Checkpoint home", "checkpoint"));
   final Setting<Integer> checkpointEvery = add(new NumberSetting<>("Checkpoint every", 5, 1, 60).withUnit("min"));
   // Connection
   private final Setting<Boolean> connectionGroup = group("Connection", TAB_CONNECTION);
   final Setting<Float> reconnectDelay = add(new NumberSetting<>("Reconnect delay", 3.5F, 0.0F, 60.0F).withUnit("s"));
   final Setting<Integer> maxReconnects = add(new NumberSetting<>("Max reconnects", 10, 1, 100));
   final Setting<Integer> deathLoopWindow = add(new NumberSetting<>("Death loop window", 3, 1, 30).withUnit("min"));
   final Setting<Boolean> enterPortal = add(new BooleanSetting("Enter lobby portal", true));
   final Setting<String> loginAccount = add(new StringSetting("Login account", ""));
   final Setting<String> loginPassword = add(new StringSetting("Login password", ""));
   final Setting<Integer> loginTimeout = add(new NumberSetting<>("Login timeout", 200, 20, 1200).withUnit("ticks"));
   // Discord
   private final Setting<Boolean> discordGroup = group("Discord", TAB_DISCORD);
   final Setting<String> webhook = add(new StringSetting("Webhook", ""));
   final Setting<String> ping = add(new StringSetting("Ping", ""));
   final Setting<Boolean> hideCoords = add(new BooleanSetting("Hide coordinates", false));
   final Setting<Integer> updateEvery = add(new NumberSetting<>("Update every", 15, 1, 120).withUnit("min"));

   private final StationStore stations = new StationStore(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("argusclient/elytra-trip-stations.json"));
   private final TripCoordinator coordinator = new TripCoordinator(this);

   public ElytraTripModule() {
      super("ElytraTrip", "Flies to a chosen X/Z under the Nether roof and keeps going: eats, swaps elytras, refills at your station, "
         + "recovers from deaths and disconnects, and logs out on arrival.", ModuleCategory.MOVEMENT, "elytra trip", "nether travel",
         "auto travel");
      StationChests.bind(this);
   }

   public static ElytraTripModule get() {
      return ClientContext.moduleRegistry == null ? null : (ElytraTripModule)ClientContext.moduleRegistry.getModule(ElytraTripModule.class);
   }

   private Setting<Boolean> group(String name, String tab) {
      Setting<Boolean> header = add(new BooleanSetting(name, false)).markAsGroup();
      header.setExcludedFromConfig(true);
      header.groupSection = tab;
      return header;
   }

   TripCoordinator coordinator() {
      return coordinator;
   }

   SupplyLimits limits() {
      SupplyLimits base = new SupplyLimits(spareMin.getValue(), totemMin.getValue(), gappleMin.getValue(), foodMin.getValue(), armourMin.getValue() / 100.0F,
         wornBelow.getValue(), freshAbove.getValue() / 100.0F, elytraTarget.getValue(), fillEmpty.getValue(), totemTarget.getValue(),
         gappleTarget.getValue(), foodTarget.getValue(), swapBelow.getValue());
      TripLoadout saved = savedLoadout();
      return saved == null ? base : saved.apply(base);
   }

   /** The inventory saved in setup mode, or null. */
   public TripLoadout savedLoadout() {
      return TripLoadout.decode(loadout.getValue());
   }

   /** Saves what the player carries now as the quantities to restock to and the slot each item goes back in. */
   public TripLoadout saveLoadout() {
      if (mc.player == null) throw new IllegalStateException("Join a world first.");
      TripLoadout captured = TripInventory.loadout(mc.player);
      loadout.setValue(captured.encode());
      saveConfig();
      return captured;
   }

   public void clearLoadout() {
      loadout.setValue("");
      saveConfig();
   }

   // ---- public API for the settings screen ------------------------------------------------------------------------

   /** Null when a trip can start, otherwise one sentence saying why not. */
   public String startProblem() {
      return coordinator.startProblem();
   }

   public void startTrip(int x, int z) {
      if (!isToggled()) enable();
      destination.setValue(x + "," + z);
      coordinator.start(x, z);
      saveConfig();
   }

   public void pause() {
      coordinator.pause();
   }

   public void resume() {
      coordinator.resume();
   }

   public void stop() {
      coordinator.stop();
   }

   public boolean running() {
      return coordinator.running();
   }

   public boolean paused() {
      return coordinator.pausedOrParked();
   }

   public String statusLine() {
      return coordinator.statusLine();
   }

   public List<String> turnedOff() {
      return coordinator.turnedOff();
   }

   public void testRefill() {
      if (!isToggled()) enable();
      coordinator.testRefill();
   }

   public void testDiscord() {
      coordinator.testDiscord();
   }

   /** The X and Z last typed or started, or null. */
   public int[] typedDestination() {
      String[] parts = destination.getValue().split(",");
      if (parts.length != 2) return null;
      try {
         return new int[]{Integer.parseInt(parts[0].strip()), Integer.parseInt(parts[1].strip())};
      } catch (NumberFormatException e) {
         return null;
      }
   }

   public void typeDestination(String x, String z) {
      destination.setValue(x.strip() + "," + z.strip());
   }

   public List<Destination> savedDestinations() {
      List<Destination> list = new ArrayList<>();
      for (String line : saved.getValue().split("\n")) {
         String[] parts = line.split("\\|");
         if (parts.length != 3) continue;
         try {
            list.add(new Destination(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
         } catch (NumberFormatException ignored) {
            // A damaged line: skip it.
         }
      }
      return list;
   }

   public void saveDestination(String name, int x, int z) {
      String clean = name.replace("|", " ").replace("\n", " ").strip();
      List<Destination> list = savedDestinations();
      list.removeIf(d -> d.name().equalsIgnoreCase(clean.isEmpty() ? x + ", " + z : clean));
      list.add(new Destination(clean.isEmpty() ? x + ", " + z : clean, x, z));
      writeDestinations(list);
   }

   public void deleteDestination(int index) {
      List<Destination> list = savedDestinations();
      if (index >= 0 && index < list.size()) list.remove(index);
      writeDestinations(list);
   }

   private void writeDestinations(List<Destination> list) {
      StringBuilder text = new StringBuilder();
      for (Destination d : list) text.append(text.isEmpty() ? "" : "\n").append(d.name()).append('|').append(d.x()).append('|').append(d.z());
      saved.setValue(text.toString());
      saveConfig();
   }

   public Setting<Boolean> setupModeSetting() {
      return setupMode;
   }

   private static void saveConfig() {
      if (ClientContext.configurationManager != null) ClientContext.configurationManager.saveAsync();
   }

   // ---- StationChests.Host ----------------------------------------------------------------------------------------

   @Override
   public boolean setupMode() {
      return isToggled() && setupMode.getValue();
   }

   @Override
   public StationStore stations() {
      return stations;
   }

   @Override
   public String serverKey() {
      var server = mc.getCurrentServer();
      return server == null ? null : SetHomeStore.normaliseServer(server.ip);
   }

   @Override
   public void stationsChanged() {
      try {
         stations.save();
      } catch (java.io.IOException e) {
         say("Couldn't save the station chest tags: " + e.getMessage());
      }
   }

   void say(String text) {
      if (mc.player != null) mc.gui.getChat().addClientSystemMessage(Component.literal("[ElytraTrip] " + text));
   }

   // ---- lifecycle and events --------------------------------------------------------------------------------------

   @Override
   public String getInfo() {
      return coordinator.info();
   }

   @Override
   public void onEnable() {
      coordinator.enabled();
   }

   @Override
   public void onDisable() {
      coordinator.disabled();
   }

   @EventHandler
   private void onTick(MinecraftTickEvent event) {
      coordinator.clientTick();
   }

   @EventHandler
   private void onPreTick(LocalPlayerPreTickEvent event) {
      coordinator.playerTick();
   }

   @EventHandler(priority = -400)
   private void onMove(LocalPlayerMoveEvent event) {
      coordinator.onMove(event);
   }

   @EventHandler
   private void onKeyboard(KeyboardInputTickEvent event) {
      coordinator.onKeyboard(event);
   }

   @EventHandler
   private void onReceive(PacketEvent.Receive event) {
      coordinator.onPacket(event);
   }

   @EventHandler
   private void onUseItemOn(MultiPlayerGameModeUseItemOnEvent event) {
      StationChests.clicked(event.getHitResult().getBlockPos());
   }

   @EventHandler
   private void onRender(WorldRenderEvent.Main event) {
      StationChests.render(event.getPoseStack(), mc);
      if (showRoute.getValue()) coordinator.renderRoute(event, routeColor.getValue());
   }
}
