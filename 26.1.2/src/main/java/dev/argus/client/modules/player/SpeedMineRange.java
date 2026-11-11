package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.world.WorldUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class SpeedMineRange {
   static boolean isInRange(BlockPos blockPos, float range) {
      if (!RotationManager.isMovementSyncEnabled()) {
         Vec3 eyePosition = ClientAccess.argusclientMinecraft.player.getEyePosition();
         Vec3 blockCenter = blockPos.getCenter();
         return eyePosition.distanceTo(blockCenter) <= range;
      }

      LocalPlayer scalePlayer = ClientAccess.argusclientMinecraft.player;
      Holder scaleAttribute = Attributes.SCALE;
      float playerScale = (float)scalePlayer.getAttributeValue(scaleAttribute);
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Vec3 feetPosition = BoundingBoxUtils.getInterpolationBounds(player).getBottomCenter();
      AABB blockBounds = new AABB(blockPos);
      double nearestDistance = Double.MAX_VALUE;

      for (float sampleHeight : PlayerStateUtils.getBodySampleHeights()) {
         double sampleY = sampleHeight * playerScale;
         Vec3 samplePoint = feetPosition.add(0.0, sampleY, 0.0);
         double sampleDistance = WorldUtils.distanceToBounds(samplePoint, blockBounds);
         if (sampleDistance < nearestDistance) {
            nearestDistance = sampleDistance;
         }
      }

      return nearestDistance <= range;
   }
}
