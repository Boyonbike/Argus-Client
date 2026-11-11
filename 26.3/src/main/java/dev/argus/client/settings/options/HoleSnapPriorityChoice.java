package dev.argus.client.settings.options;

import dev.argus.client.ClientAccess;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.utils.math.Vec3Utils;
import dev.argus.client.utils.world.HoleInfo;
import java.util.Comparator;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public enum HoleSnapPriorityChoice implements ClientAccess, NamedEntry {
   DISTANCE("Distance", Comparator.comparing(hole -> {
      BlockPos block = hole.pos();
      Vec3 center = Vec3.atCenterOf(block);
      LocalPlayer capturedPlayer = ClientAccess.argusclientMinecraft.player;
      Vec3 playerPosition = capturedPlayer.position();
      return Vec3Utils.horizontalDistance(center, playerPosition);
   })),
   CROSSHAIR("Crosshair", Comparator.comparing(hole -> {
      GameRenderer gameRenderer = ClientAccess.argusclientMinecraft.gameRenderer;
      Camera camera = gameRenderer.mainCamera();
      float cameraYaw = camera.yaw();
      BlockPos block = hole.pos();
      Vec3 center = Vec3.atCenterOf(block);
      float targetYaw = RotationManager.getRotationsToPosition(center)[0];
      float yawDifference = Mth.degreesDifferenceAbs(cameraYaw, targetYaw);
      return yawDifference;
   }));

   private final String displayName;
   private final Comparator<HoleInfo> comparator;

   @Override
   public String getName() {
      return this.displayName;
   }

   HoleSnapPriorityChoice(String displayName, Comparator<HoleInfo> comparator) {
      this.displayName = displayName;
      this.comparator = comparator;
   }

   public Comparator<HoleInfo> getComparator() {
      return this.comparator;
   }
}
