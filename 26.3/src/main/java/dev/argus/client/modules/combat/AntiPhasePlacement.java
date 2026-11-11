package dev.argus.client.modules.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record AntiPhasePlacement(BlockPos supportPosition, Direction direction, AABB bounds) {
   public Vec3 getHitPosition() {
      Vec3 supportCenter = Vec3.atCenterOf(this.supportPosition);
      return supportCenter.relative(this.direction, 0.5);
   }
}
