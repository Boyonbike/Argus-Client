package dev.argus.client.settings.options;

import dev.argus.client.ClientAccess;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.utils.entity.EntityUtils;
import java.util.Comparator;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public enum EntitySortChoice implements NamedEntry {
   DISTANCE("Distance", Comparator.comparingDouble(entity -> {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      float distance = entity.distanceTo(player);
      return distance;
   })),
   CROSSHAIR("Crosshair", Comparator.comparingDouble(entity -> {
      GameRenderer renderer = ClientAccess.argusclientMinecraft.gameRenderer;
      Camera camera = renderer.mainCamera();
      float cameraYaw = camera.yaw();
      float targetYaw = RotationManager.getRotationsToEntity(entity)[0];
      return Mth.degreesDifferenceAbs(cameraYaw, targetYaw);
   })),
   HEALTH("Health", Comparator.comparingDouble(EntityUtils::getHealthWithAbsorption));

   private final String name;
   private final Comparator<Entity> comparator;

   EntitySortChoice(String name, Comparator<Entity> comparator) {
      this.name = name;
      this.comparator = comparator;
   }

   @Override
   public String getName() {
      return this.name;
   }

   public Comparator<Entity> getComparator() {
      return this.comparator;
   }
}
