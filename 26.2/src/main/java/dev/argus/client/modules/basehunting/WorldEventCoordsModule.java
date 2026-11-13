package dev.argus.client.modules.basehunting;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import java.awt.Color;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.world.phys.AABB;

/**
 * Shows where world-wide events happen: a wither being summoned, the ender dragon dying, or an end portal being
 * opened. Every player hears these, and the packet carries where they happened. Some servers hide the position.
 * Ported from Trouser Streak's WorldEventCoords.
 */
public class WorldEventCoordsModule extends Module {
   private static final int WITHER_SPAWNED = 1023;
   private static final int DRAGON_DIED = 1028;
   private static final int END_PORTAL_OPENED = 1038;
   private final Setting<Boolean> witherSetting = this.add(new BooleanSetting("Wither", true));
   private final Setting<Boolean> dragonSetting = this.add(new BooleanSetting("Dragon", true));
   private final Setting<Boolean> portalSetting = this.add(new BooleanSetting("EndPortal", true));
   private final Setting<Boolean> unknownSetting = this.add(new BooleanSetting("OtherGlobal", false));
   private final Setting<Boolean> triangulateSetting = this.add(new BooleanSetting("Triangulate", true).markVisibilityDependency());
   private final Setting<Integer> windowSetting = this.add(new NumberSetting<>("Window", 120, 5, 1800).withUnit("s").dependsOn(this.triangulateSetting));
   private final Setting<Integer> minMoveSetting = this.add(new NumberSetting<>("MinMove", 48, 8, 1000).withUnit("m").dependsOn(this.triangulateSetting));
   private final Setting<Float> minShiftSetting = this.add(new NumberSetting<>("MinShift", 2.0F, 0.2F, 45.0F).withUnit("°").dependsOn(this.triangulateSetting));
   private final java.util.Map<Integer, Bearing> bearings = new java.util.concurrent.ConcurrentHashMap<>();
   private final HuntMarkers markers = new HuntMarkers(this, new Color(255, 255, 120, 40), new Color(255, 255, 120, 230), true);

   public WorldEventCoordsModule() {
      super("WorldEventCoords", "Shows where withers are summoned, dragons die and end portals open.", ModuleCategory.BASE_HUNTING);
   }

   @Override
   public void onDisable() {
      this.markers.clear();
      this.bearings.clear();
   }

   @EventHandler
   private void onDisconnect(MinecraftDisconnectEvent event) {
      this.markers.clear();
      this.bearings.clear();
   }

   @EventHandler
   private void onPacket(PacketEvent.Receive event) {
      if (!(event.getPacket() instanceof ClientboundLevelEventPacket packet) || !packet.isGlobalEvent()) {
         return;
      }

      String what = switch (packet.getType()) {
         case WITHER_SPAWNED -> this.witherSetting.getValue() ? "a wither being summoned" : null;
         case DRAGON_DIED -> this.dragonSetting.getValue() ? "the ender dragon dying" : null;
         case END_PORTAL_OPENED -> this.portalSetting.getValue() ? "an end portal opening" : null;
         default -> this.unknownSetting.getValue() ? "global event " + packet.getType() : null;
      };
      if (what == null) {
         return;
      }

      BlockPos position = packet.getPos().immutable();
      net.minecraft.client.player.LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (player == null) {
         return;
      }

      double dx = position.getX() + 0.5 - player.getX();
      double dz = position.getZ() + 0.5 - player.getZ();
      double distance = Math.sqrt(dx * dx + dz * dz);
      int viewBlocks = ClientAccess.argusclientMinecraft.options.renderDistance().get() * 16 + 48;
      if (!this.triangulateSetting.getValue() || distance > viewBlocks) {
         // Vanilla servers send the real spot.
         this.markers.report(position, new AABB(position).inflate(1.0), what);
         return;
      }

      // Servers that hide the spot still send it in the right direction, so two hearings from different places cross.
      double angle = Math.atan2(-dx, dz);
      Bearing previous = this.bearings.get(packet.getType());
      long now = System.currentTimeMillis();
      Bearing current = new Bearing(player.getX(), player.getZ(), angle, now);
      if (previous == null || now - previous.time() > this.windowSetting.getValue() * 1000L) {
         this.bearings.put(packet.getType(), current);
         this.say("Heard " + what + " " + Math.round(Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360 + "° from north; move "
            + this.minMoveSetting.getValue() + "m sideways and hear the next one to place it.");
         return;
      }

      double moved = Math.hypot(current.x() - previous.x(), current.z() - previous.z());
      double shift = Math.toDegrees(Math.abs(Math.atan2(Math.sin(angle - previous.angle()), Math.cos(angle - previous.angle()))));
      if (moved < this.minMoveSetting.getValue() || shift < this.minShiftSetting.getValue()) {
         return;
      }

      double[] point = Triangulation.intersect(previous.x(), previous.z(), previous.angle(), current.x(), current.z(), angle);
      this.bearings.put(packet.getType(), current);
      if (point != null) {
         BlockPos estimate = BlockPos.containing(point[0], position.getY(), point[1]);
         this.markers.report(estimate, new AABB(estimate).inflate(2.0), what + " (estimated)");
      }
   }

   private void say(String message) {
      ClientAccess.argusclientMinecraft.execute(
         () -> dev.argus.client.utils.chat.ChatUtils.printChat(
            net.minecraft.network.chat.Component.literal("WorldEventCoords: " + message), dev.argus.client.utils.chat.ChatUtils.moduleSignature(this)
         )
      );
   }

   private record Bearing(double x, double z, double angle, long time) {
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      this.markers.render(event);
   }
}
