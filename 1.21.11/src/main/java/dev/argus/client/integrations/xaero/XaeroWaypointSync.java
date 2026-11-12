package dev.argus.client.integrations.xaero;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.managers.WaypointRegistry;
import dev.argus.client.modules.render.WaypointsModule;
import dev.argus.client.utils.world.Waypoint;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.player.LocalPlayer;

/**
 * Keeps Argus's waypoints and Xaero's Minimap waypoints the same, both ways, for the server you are on, while the
 * Waypoints module's XaeroSync setting is on (whether or not the module itself is drawing). Adding, moving, hiding
 * or deleting a waypoint on either side is copied to the other within a second; {@link WaypointSyncPlanner} decides
 * what to copy. Does nothing when Xaero's Minimap isn't installed.
 */
public final class XaeroWaypointSync implements ClientAccess {
   /** Wait this long after joining or changing dimension, so Xaero has loaded its waypoints first. */
   private static final int SETTLE_TICKS = 100;
   private static final int INTERVAL_TICKS = 20;

   public XaeroWaypointSync() {
      ClientAccess.eventBus.subscribe(this);
   }

   @EventHandler
   private void onTick(LocalPlayerTickEvent event) {
      WaypointAdapter adapter = ClientContext.waypointAdapter;
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (adapter == null || !adapter.isAvailable() || player == null) return;
      if (player.tickCount < SETTLE_TICKS || player.tickCount % INTERVAL_TICKS != 0) return;
      if (!enabled()) return;
      try {
         sync(adapter);
      } catch (RuntimeException | LinkageError failure) {
         ClientAccess.logger.warn("Xaero waypoint sync failed", failure);
      }
   }

   public static boolean enabled() {
      if (ClientContext.waypointAdapter == null || !ClientContext.waypointAdapter.isAvailable() || ClientContext.moduleRegistry == null) return false;
      WaypointsModule module = (WaypointsModule) ClientContext.moduleRegistry.getModule(WaypointsModule.class);
      return module != null && module.xaeroSyncSetting.getValue();
   }

   private static int block(double coordinate) {
      return (int) Math.floor(coordinate);
   }

   static void sync(WaypointAdapter adapter) {
      List<WaypointSyncPlanner.World> worlds = adapter.syncWorlds();
      if (worlds.isEmpty()) return;
      WaypointRegistry registry = ClientContext.waypointRegistry;
      String server = WaypointRegistry.currentServerKey();

      List<WaypointSyncPlanner.ArgusPoint> argus = new ArrayList<>();
      for (Waypoint waypoint : registry.getRegistry()) {
         if (waypoint == null || waypoint.getName() == null || waypoint.getDimension() == null || waypoint.getServer() == null) continue;
         if (!server.equalsIgnoreCase(waypoint.getServer())) continue;
         argus.add(new WaypointSyncPlanner.ArgusPoint(waypoint.getName(), waypoint.getDimension(),
            block(waypoint.getX()), block(waypoint.getY()), block(waypoint.getZ()), waypoint.isToggled()));
      }
      List<WaypointSyncPlanner.Link> others = new ArrayList<>(), current = new ArrayList<>();
      for (WaypointSyncPlanner.Link link : registry.getXaeroLinks()) (server.equalsIgnoreCase(link.server()) ? current : others).add(link);

      WaypointSyncPlanner.Plan plan = WaypointSyncPlanner.plan(server, argus, worlds, current);
      if (plan.isEmpty() && plan.links().equals(current)) return;

      List<WaypointSyncPlanner.Link> links = new ArrayList<>(plan.links());
      Set<String> touchedXaero = new LinkedHashSet<>();
      for (WaypointSyncPlanner.Op op : plan.ops()) {
         switch (op) {
            case WaypointSyncPlanner.ArgusAdd add -> {
               Waypoint waypoint = new Waypoint(add.name(), add.x() + 0.5, add.y(), add.z() + 0.5, add.dimension(), server);
               waypoint.setToggled(add.enabled());
               registry.registerWaypoint(waypoint);
            }
            case WaypointSyncPlanner.ArgusUpdate update -> {
               Waypoint existing = find(registry, update.name(), server);
               if (existing == null) break;
               if (block(existing.getX()) == update.x() && block(existing.getY()) == update.y() && block(existing.getZ()) == update.z()) {
                  existing.setToggled(update.enabled());
               } else {
                  Waypoint moved = new Waypoint(existing.getName(), update.x() + 0.5, update.y(), update.z() + 0.5, existing.getDimension(), server);
                  moved.setToggled(update.enabled());
                  registry.unregisterWaypoint(existing);
                  registry.registerWaypoint(moved);
               }
            }
            case WaypointSyncPlanner.ArgusRemove remove -> {
               Waypoint existing = find(registry, remove.name(), server);
               if (existing != null) registry.unregisterWaypoint(existing);
            }
            case WaypointSyncPlanner.XaeroAdd add -> {
               String set = adapter.createSynced(add.dimension(), add.name(), add.x(), add.y(), add.z(), add.enabled());
               if (set != null) {
                  links.add(new WaypointSyncPlanner.Link(server, add.dimension(), add.world(), add.name(), add.name(), set, add.x(), add.y(), add.z(), add.enabled()));
                  touchedXaero.add(add.dimension());
               }
            }
            case WaypointSyncPlanner.XaeroUpdate update -> {
               adapter.updateSynced(update.handle(), update.x(), update.y(), update.z(), update.enabled());
               touchedXaero.add(update.dimension());
            }
            case WaypointSyncPlanner.XaeroRemove remove -> {
               adapter.removeSynced(remove.dimension(), remove.handle());
               touchedXaero.add(remove.dimension());
            }
         }
      }

      registry.getXaeroLinks().clear();
      registry.getXaeroLinks().addAll(others);
      registry.getXaeroLinks().addAll(links);
      if (!touchedXaero.isEmpty()) adapter.saveSynced(touchedXaero);
      if (ClientContext.configurationManager != null) ClientContext.configurationManager.saveAsync();
   }

   private static Waypoint find(WaypointRegistry registry, String name, String server) {
      for (Waypoint waypoint : registry.getRegistry())
         if (waypoint != null && waypoint.getName() != null && waypoint.getServer() != null
            && waypoint.getName().equalsIgnoreCase(name) && waypoint.getServer().equalsIgnoreCase(server)) return waypoint;
      return null;
   }
}
