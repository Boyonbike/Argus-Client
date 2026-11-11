package dev.argus.client.modules.render;

import dev.argus.client.render.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record TracerEndpoints(Vec3 start, Vec3 end) {
   static TracerEndpoints standing(Player player, Vec3 position) {
      Pose pose = player.getPose();
      double eyeHeight = player.getEyeHeight(pose);
      Vec3 eyePosition = position.add(0.0, eyeHeight, 0.0);
      return new TracerEndpoints(position, eyePosition);
   }

   static TracerEndpoints riding(Player player, Vec3 position) {
      position = position.add(0.0, 0.65F, 0.0);
      Pose pose = player.getPose();
      float eyeHeight = player.getEyeHeight(pose);
      double eyeOffset = eyeHeight - 0.65F;
      Vec3 eyePosition = position.add(0.0, eyeOffset, 0.0);
      return new TracerEndpoints(position, eyePosition);
   }

   static TracerEndpoints sleeping(Player player) {
      Direction bedDirection = player.getBedOrientation();
      BlockPos blockPosition = player.blockPosition();
      Vec3 headPosition = blockPosition.getCenter().relative(bedDirection, 0.2).add(0.0, 0.1, 0.0);
      Direction oppositeDirection = bedDirection.getOpposite();
      Vec3 footPosition = headPosition.relative(oppositeDirection, 1.6);
      return new TracerEndpoints(footPosition, headPosition);
   }

   static TracerEndpoints lowPose(Player player, Vec3 position, AABB bounds) {
      double horizontalDiagonal = Math.hypot(bounds.getXsize(), bounds.getZsize());
      float tickDelta = RenderUtils.getPartialTick();
      float bodyYaw = Mth.lerp(tickDelta, player.yBodyRotO, player.yBodyRot);
      float pitch = player.isVisuallyCrawling() ? player.getSwimAmount(tickDelta) : player.getViewXRot(tickDelta);
      float pitchFraction = pitch / 90.0F;
      Vec3 viewDirection = player.calculateViewVector(pitch, bodyYaw);
      Vec3 horizontalDirection = player.calculateViewVector(0.0F, bodyYaw);
      if (player.isFallFlying()) {
         float remainingGlidePitch = 45.0F - Math.abs(pitchFraction);
         double verticalOffset = -Math.abs(remainingGlidePitch * 0.01F * 0.5F);
         position = position.add(viewDirection).add(horizontalDirection.scale(pitchFraction)).add(0.0, verticalOffset, 0.0);
      }

      double backwardDistance = -horizontalDiagonal;
      Vec3 centerPosition = position.add(viewDirection.scale(backwardDistance)).add(0.0, bounds.getYsize() * 0.5, 0.0);
      Vec3 pitchOffset = horizontalDirection.scale(0.3 * pitchFraction);
      double downwardOffset = -Math.abs(0.3 * pitchFraction);
      Vec3 start = centerPosition.add(pitchOffset).add(0.0, downwardOffset, 0.0);
      Vec3 end = start.add(viewDirection.scale(horizontalDiagonal * 2.0));
      return new TracerEndpoints(start, end);
   }
}
