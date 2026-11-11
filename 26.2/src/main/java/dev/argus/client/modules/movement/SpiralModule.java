package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.math.Vec3Utils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class SpiralModule extends Module {
   private final Setting<Integer> sizeSetting = this.add(new NumberSetting<>(new String("Size"), 5, 1, 20));
   private final List<Vec3> waypoints = new ArrayList<>(5000);
   private int waypointIndex;

   public SpiralModule() {
      super("Spiral", ModuleCategory.MOVEMENT);
   }

   @Override
   public void onEnable() {
      this.waypointIndex = 0;
      this.waypoints.clear();
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Vec3 position = player.position();
      float yaw = 0.0F;

      for (int index = 0; index < 20000; index++) {
         this.waypoints.add(position);
         Vec3 direction = Vec3.directionFromRotation(0.0F, yaw);
         Setting<Integer> setting = this.sizeSetting;
         Integer size = setting.getValue();
         int radius = index * size;
         double distance = radius * 16;
         Vec3 offset = direction.scale(distance);
         position = position.add(offset);
         float nextYaw = yaw + 90.0F;
         yaw = Mth.wrapDegrees(nextYaw);
      }
   }

   @EventHandler
   private void onWorldRender(WorldRenderEvent.Main event) {
      int index = Math.max(this.waypointIndex, 1);

      while (true) {
         int startIndex = Math.max(this.waypointIndex, 1);
         if (index >= startIndex + 2) {
            return;
         }

         Vec3 previousPoint = this.waypoints.get(index - 1);
         Vec3 point = this.waypoints.get(index);
         index++;
      }
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      int index = this.waypointIndex;
      Vec3 waypoint = this.waypoints.get(index);
      float yaw = RotationManager.getRotationsToPosition(waypoint)[0];
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      player.setYRot(yaw);
      Vec3 position = ClientAccess.argusclientMinecraft.player.position();
      float distance = Vec3Utils.horizontalDistance(waypoint, position);
      if (distance < 3.0F) {
         this.waypointIndex++;
      }
   }
}
