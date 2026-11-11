package dev.argus.client.utils.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public record HoleInfo(HoleInfo.Safety safety, BlockPos pos, AABB bounds, boolean trapped) {
   public enum Safety {
      SAFE,
      UNSAFE;
   }
}
