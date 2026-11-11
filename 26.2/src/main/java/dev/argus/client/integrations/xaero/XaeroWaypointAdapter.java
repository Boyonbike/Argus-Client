package dev.argus.client.integrations.xaero;

import java.util.ArrayList;
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
   private static final String WAYPOINT_SET_NAME = "Argus Client Waypoints";

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
