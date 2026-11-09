package dev.argus.client.managers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.settings.JsonConfigurable;
import dev.argus.client.utils.world.Waypoint;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;

public final class WaypointRegistry extends AbstractRegistry<Waypoint> implements ClientAccess, JsonConfigurable {
   public WaypointRegistry() {
      super(new ArrayList<>());
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   @Override
   public boolean register(Object entry) {
      Waypoint waypoint = (Waypoint)entry;
      return this.registerWaypoint(waypoint);
   }

   @Override
   public boolean unregister(Object entry) {
      Waypoint waypoint = (Waypoint)entry;
      return this.unregisterWaypoint(waypoint);
   }

   public List<Waypoint> getVisible() {
      if (this.isWorldUnavailable()) {
         return Collections.emptyList();
      }

      ClientPacketListener serverCheckConnection = ClientAccess.argusclientMinecraft.player.connection;
      String serverAddress;
      if (serverCheckConnection.getServerData() == null) {
         serverAddress = "singleplayer";
      } else {
         ClientPacketListener addressConnection = ClientAccess.argusclientMinecraft.player.connection;
         serverAddress = addressConnection.getServerData().ip;
      }

      String currentServer = serverAddress;
      return this.entries.stream().filter(candidate -> {
         if (candidate.getServer() != null) {
            String waypointServer = candidate.getServer();
            if (currentServer.equalsIgnoreCase(waypointServer)) {
               return true;
            }
         }

         return false;
      }).sorted(Comparator.comparing(waypoint -> {
         LocalPlayer playerSnapshot = ClientAccess.argusclientMinecraft.player;
         double waypointX = waypoint.getX();
         double waypointY = waypoint.getY();
         double waypointZ = waypoint.getZ();
         return playerSnapshot.distanceToSqr(waypointX, waypointY, waypointZ);
      })).toList();
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json.isJsonObject()) {
         JsonObject configObject = json.getAsJsonObject();
         if (configObject.has("waypoints")) {
            configObject = json.getAsJsonObject();

            for (JsonElement element : configObject.getAsJsonArray("waypoints")) {
               try {
                  Collection destination = this.entries;
                  Gson gson = ClientAccess.gson;
                  Class<Waypoint> waypointClass = Waypoint.class;
                  destination.add((Waypoint)gson.fromJson(element, waypointClass));
               } catch (Exception loadFailure) {
                  loadFailure.printStackTrace();
               }
            }
         }
      }
   }

   @Override
   public JsonElement toJson() {
      JsonArray waypointArray = new JsonArray();
      JsonObject configObject = new JsonObject();

      for (Waypoint waypoint : this.entries) {
         Gson gson = ClientAccess.gson;
         JsonElement waypointJson = gson.toJsonTree(waypoint);
         waypointArray.add(waypointJson);
      }

      configObject.add("waypoints", waypointArray);
      return configObject;
   }

   @Override
   public String getConfigName() {
      return "waypoints.json";
   }

   public boolean registerWaypoint(Waypoint waypoint) {
      for (Waypoint existingWaypoint : this.getRegistry()) {
         String existingName = existingWaypoint.getName();
         String requestedName = waypoint.getName();
         if (existingName.equalsIgnoreCase(requestedName)) {
            String existingServer = existingWaypoint.getServer();
            String requestedServer = waypoint.getServer();
            if (existingServer.equalsIgnoreCase(requestedServer)) {
               return false;
            }
         }
      }

      return this.getRegistry().add(waypoint);
   }

   public boolean unregisterWaypoint(Waypoint waypoint) {
      if (waypoint == null) return false;
      if (waypoint.getName() == null || waypoint.getServer() == null) return this.getRegistry().remove(waypoint);
      return this.getRegistry().removeIf(candidate -> candidate != null
         && waypoint.getName().equalsIgnoreCase(candidate.getName())
         && waypoint.getServer().equalsIgnoreCase(candidate.getServer()));
   }
}
