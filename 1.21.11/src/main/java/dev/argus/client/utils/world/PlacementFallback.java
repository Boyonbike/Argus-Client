package dev.argus.client.utils.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PlacementFallback {
   public static final double SURVIVAL_SERVER_BLOCK_RANGE = 4.5;
   public static final double CREATIVE_SERVER_BLOCK_RANGE = 5.0;
   public static final double SERVER_RANGE_PADDING = 1.0;

   private PlacementFallback() {
   }

   public static double serverEnvelope(boolean creative) {
      return (creative ? CREATIVE_SERVER_BLOCK_RANGE : SURVIVAL_SERVER_BLOCK_RANGE) + SERVER_RANGE_PADDING;
   }

   public static boolean isWithinServerEnvelope(BlockPos position, Vec3 eyePosition, boolean creative) {
      double envelope = serverEnvelope(creative);
      return new AABB(position).distanceToSqr(eyePosition) < envelope * envelope;
   }

   public static BlockPos resolveTarget(BlockPos clickedPos, BlockPos placementPos, Vec3 eyePosition, boolean creative) {
      if (isWithinServerEnvelope(clickedPos, eyePosition, creative)) {
         return null;
      }

      return isWithinServerEnvelope(placementPos, eyePosition, creative) ? placementPos : null;
   }
}
