package dev.argus.client.modules.basehunting;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.mixin.TrackedAzimuthWaypointAccessor;
import dev.argus.mixin.TrackedChunkWaypointAccessor;
import dev.argus.mixin.TrackedVec3iWaypointAccessor;
import java.awt.Color;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Works out where far-away players are from the locator bar added in 1.21.6. When the server sends only a bearing,
 * move a little and it is triangulated from two bearings taken in different places; when the server sends a chunk or
 * position, that is used directly. Ported from Trouser Streak's WaypointCoordExploit.
 */
public class WaypointCoordsModule extends Module {
   private final Setting<Integer> minMoveSetting = this.add(new NumberSetting<>("MinMove", 20, 5, 200).withUnit("m"));
   private final Setting<Float> minAngleSetting = this.add(new NumberSetting<>("MinAngle", 1.0F, 0.1F, 20.0F).withUnit("°"));
   private final HuntMarkers markers = new HuntMarkers(this, new Color(255, 255, 255, 30), new Color(255, 255, 255, 220), true);
   private final Map<UUID, Sample> samples = new ConcurrentHashMap<>();
   private final Map<UUID, BlockPos> lastPositions = new ConcurrentHashMap<>();

   public WaypointCoordsModule() {
      super("WaypointCoords", "Works out where far-away players are from the locator bar.", ModuleCategory.BASE_HUNTING);
   }

   @Override
   public void onDisable() {
      this.reset();
   }

   @EventHandler
   private void onDisconnect(MinecraftDisconnectEvent event) {
      this.reset();
   }

   private void reset() {
      this.samples.clear();
      this.lastPositions.clear();
      this.markers.clear();
   }

   @Override
   public String getInfo() {
      return this.lastPositions.isEmpty() ? null : String.valueOf(this.lastPositions.size());
   }

   @EventHandler
   private void onTick(LocalPlayerTickEvent event) {
      ClientPacketListener connection = ClientAccess.argusclientMinecraft.getConnection();
      if (this.isWorldUnavailable() || connection == null) {
         return;
      }

      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      connection.getWaypointManager().forEachWaypoint(player, waypoint -> {
         UUID id = waypoint.id().left().orElse(null);
         if (id == null) {
            return;
         }

         Object raw = waypoint;
         if (raw instanceof TrackedVec3iWaypointAccessor exact) {
            Vec3i vector = exact.argusclient$getVector();
            this.found(connection, id, new BlockPos(vector.getX(), vector.getY(), vector.getZ()), "exact");
         } else if (raw instanceof TrackedChunkWaypointAccessor chunkWaypoint) {
            ChunkPos chunk = chunkWaypoint.argusclient$getChunkPos();
            this.found(connection, id, new BlockPos(chunk.getMiddleBlockX(), (int)player.getY(), chunk.getMiddleBlockZ()), "chunk");
         } else if (raw instanceof TrackedAzimuthWaypointAccessor azimuth) {
            this.sample(connection, id, azimuth.argusclient$getAngle(), player.position());
         }
      });
   }

   private void sample(ClientPacketListener connection, UUID id, float angle, Vec3 here) {
      Sample sample = this.samples.get(id);
      if (sample == null) {
         this.samples.put(id, new Sample(here, angle));
         return;
      }

      if (here.distanceTo(sample.position) < this.minMoveSetting.getValue()
         || Math.toDegrees(Math.abs(sample.angle - angle)) < this.minAngleSetting.getValue()) {
         return;
      }

      double[] point = Triangulation.intersect(sample.position.x, sample.position.z, sample.angle, here.x, here.z, angle);
      // Start over from here either way, so the next estimate uses fresh bearings.
      this.samples.put(id, new Sample(here, angle));
      if (point != null) {
         this.found(connection, id, BlockPos.containing(point[0], here.y, point[1]), "estimated");
      }
   }

   private void found(ClientPacketListener connection, UUID id, BlockPos position, String how) {
      BlockPos previous = this.lastPositions.put(id, position);
      if (position.equals(previous)) {
         return;
      }

      String name = playerName(connection, id);
      if (previous != null) {
         this.markers.remove(previous);
      }

      AABB box = new AABB(position.getX() - 0.5, position.getY(), position.getZ() - 0.5, position.getX() + 1.5, position.getY() + 2, position.getZ() + 1.5);
      if (previous == null || !previous.closerThan(position, 64.0)) {
         this.markers.report(position, box, name + " (" + how + ")");
      } else {
         this.markers.mark(position, box, name + " (" + how + ")");
      }
   }

   private static String playerName(ClientPacketListener connection, UUID id) {
      PlayerInfo info = connection.getPlayerInfo(id);
      return info == null ? id.toString() : info.getProfile().name();
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      this.markers.render(event);
   }

   private record Sample(Vec3 position, float angle) {
   }
}
