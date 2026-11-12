package dev.argus.client.integrations.xaero;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import net.minecraft.core.BlockPos;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.MinimapWorldManager;
import xaero.hud.module.HudModule;

public final class XaeroWaypointAdapter implements WaypointAdapter {
   /** Stash Finder's own set: kept out of the sync so found stashes don't flood the waypoint list. */
   public static final String WAYPOINT_SET_NAME = "Argus Client Waypoints";
   /** The worlds handed out by the last {@link #syncWorlds()}, by Argus dimension name. */
   private final Map<String, MinimapWorld> syncedWorlds = new HashMap<>();

   private static String dimensionName(ResourceKey<Level> key) {
      if (Level.OVERWORLD.equals(key)) return "overworld";
      if (Level.NETHER.equals(key)) return "nether";
      if (Level.END.equals(key)) return "end";
      return null;
   }

   private static MinimapSession session() {
      HudModule<MinimapSession> module = BuiltInHudModules.MINIMAP;
      return module.getCurrentSession();
   }

   @Override
   public List<WaypointSyncPlanner.World> syncWorlds() {
      syncedWorlds.clear();
      MinimapSession session = session();
      if (session == null) return List.of();
      MinimapWorldManager manager = session.getWorldManager();
      MinimapWorld current = manager.getCurrentWorld();
      if (current == null || current.getContainer() == null) return List.of();
      String currentDimension = dimensionName(current.getDimId());
      if (currentDimension != null) syncedWorlds.put(currentDimension, current);
      // The other dimensions: the world Xaero links to the current one (what it shows for "other dimension" waypoints).
      for (MinimapWorldContainer container : current.getContainer().getRoot().getSubContainers()) {
         String dimension = dimensionName(container.getEquivalentDimKey());
         if (dimension == null || syncedWorlds.containsKey(dimension)) continue;
         MinimapWorld world = container.getFirstWorldConnectedTo(current);
         if (world == null) {
            List<MinimapWorld> all = container.getWorldsCopy();
            if (all.size() == 1) world = all.get(0);
         }
         if (world != null) syncedWorlds.put(dimension, world);
      }

      List<WaypointSyncPlanner.World> worlds = new ArrayList<>();
      for (Map.Entry<String, MinimapWorld> entry : syncedWorlds.entrySet()) {
         List<WaypointSyncPlanner.ExternalPoint> points = new ArrayList<>();
         for (WaypointSet set : entry.getValue().getIterableWaypointSets()) {
            if (WAYPOINT_SET_NAME.equals(set.getName())) continue;
            for (Waypoint waypoint : set.getWaypoints()) {
               if (!isSyncable(waypoint)) continue;
               points.add(new WaypointSyncPlanner.ExternalPoint(waypoint.getName(), set.getName(), waypoint.getX(), waypoint.getY(), waypoint.getZ(), !waypoint.isDisabled(), waypoint));
            }
         }
         worlds.add(new WaypointSyncPlanner.World(entry.getKey(), entry.getValue().getFullPath().toString(), points));
      }
      return worlds;
   }

   /** Only the player's own waypoints: not deathpoints, destinations, temporary, server-sent or other mods' ones. */
   private static boolean isSyncable(Waypoint waypoint) {
      return waypoint.getName() != null && !waypoint.getName().isBlank() && !waypoint.isTemporary()
         && waypoint.getPurpose() == xaero.hud.minimap.waypoint.WaypointPurpose.NORMAL && !waypoint.isOneoffDestination()
         && !waypoint.isServerWaypoint() && !waypoint.isThirdParty();
   }

   @Override
   public String createSynced(String dimension, String name, int x, int y, int z, boolean enabled) {
      MinimapWorld world = syncedWorlds.get(dimension);
      if (world == null) return null;
      WaypointSet set = world.getCurrentWaypointSet();
      if (set == null || WAYPOINT_SET_NAME.equals(set.getName())) set = world.getWaypointSet(MinimapWorld.DEFAULT_SET);
      if (set == null) return null;
      Waypoint waypoint = new Waypoint(x, y, z, name, getWaypointSymbol(name).toUpperCase(java.util.Locale.ROOT), WaypointColor.getRandom());
      waypoint.setYIncluded(true);
      waypoint.setDisabled(!enabled);
      set.add(waypoint);
      return set.getName();
   }

   @Override
   public void updateSynced(Object handle, int x, int y, int z, boolean enabled) {
      Waypoint waypoint = (Waypoint) handle;
      waypoint.setX(x);
      waypoint.setY(y);
      waypoint.setZ(z);
      waypoint.setYIncluded(true);
      waypoint.setDisabled(!enabled);
   }

   @Override
   public void removeSynced(String dimension, Object handle) {
      MinimapWorld world = syncedWorlds.get(dimension);
      if (world == null) return;
      for (WaypointSet set : world.getIterableWaypointSets()) set.remove((Waypoint) handle);
   }

   @Override
   public void saveSynced(Collection<String> dimensions) {
      MinimapSession session = session();
      if (session == null) return;
      for (String dimension : dimensions) {
         MinimapWorld world = syncedWorlds.get(dimension);
         if (world == null) continue;
         try {
            session.getWorldManagerIO().saveWorld(world);
         } catch (Exception failure) {
            failure.printStackTrace();
         }
      }
   }

   @Override
   public void addWaypoint(String name, BlockPos position) {
      WaypointSet waypointSet = getCurrentWaypointSet();
      if (waypointSet != null) {
         Waypoint waypoint = new Waypoint(waypointX(position), position.getY(), position.getZ(), name, getWaypointSymbol(name), WaypointColor.getRandom());
         waypointSet.add(waypoint);
      }
   }

   @Override
   public void removeWaypoints(String name) {
      WaypointSet waypointSet = getCurrentWaypointSet();
      if (waypointSet != null) {
         ArrayList matchingWaypoints = new ArrayList();

         for (Waypoint waypoint : waypointSet.getWaypoints()) {
            if (waypoint.getName().equals(name)) {
               matchingWaypoints.add(waypoint);
            }
         }

         waypointSet.removeAll(matchingWaypoints);
      }
   }

   @Override
   public boolean isAvailable() {
      return true;
   }

   private static int waypointX(BlockPos position) {
      return position.getX();
   }

   private static String getWaypointSymbol(String name) {
      int nameLength = name.length();
      byte maximumLength = 1;
      int symbolLength = Math.min(maximumLength, nameLength);
      byte startIndex = 0;
      return name.substring(startIndex, symbolLength);
   }

   private static WaypointSet getCurrentWaypointSet() {
      MinimapWorld world = getCurrentWorld();
      if (world == null) {
         return null;
      }

      String setName = "Argus Client Waypoints";
      return getOrCreateWaypointSet(world, setName);
   }

   private static MinimapWorld getCurrentWorld() {
      HudModule minimapModule = BuiltInHudModules.MINIMAP;
      MinimapSession session = (MinimapSession)minimapModule.getCurrentSession();
      if (session == null) {
         return null;
      }

      MinimapWorldManager worldManager = session.getWorldManager();
      return worldManager.getCurrentWorld();
   }

   private static WaypointSet getOrCreateWaypointSet(MinimapWorld world, String name) {
      WaypointSet waypointSet = world.getWaypointSet(name);
      if (waypointSet == null) {
         world.addWaypointSet(name);
         waypointSet = world.getWaypointSet(name);
      }

      return waypointSet;
   }
}
