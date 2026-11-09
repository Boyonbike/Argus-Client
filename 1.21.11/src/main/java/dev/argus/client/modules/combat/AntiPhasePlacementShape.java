package dev.argus.client.modules.combat;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public record AntiPhasePlacementShape(Direction direction, AABB bounds) {
   public static AntiPhasePlacementShape fromShape(Direction facing, VoxelShape shape) {
      return new AntiPhasePlacementShape(facing.getOpposite(), shape.bounds());
   }
}
