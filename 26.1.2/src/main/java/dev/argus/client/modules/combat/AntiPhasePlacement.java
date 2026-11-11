package dev.argus.client.modules.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record AntiPhasePlacement(BlockPos supportPosition, Direction direction, AABB bounds) {
   public Vec3 getHitPosition() {
      Vec3 supportCenter = this.supportPosition.getCenter();
      return supportCenter.relative(this.direction, 0.5);
   }
}
