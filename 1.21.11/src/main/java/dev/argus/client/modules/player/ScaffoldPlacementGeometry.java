package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public record ScaffoldPlacementGeometry(Direction supportDirection, Vec3 aimPosition, VoxelShape supportShape) implements ClientAccess {
   public static ScaffoldPlacementGeometry fromTarget(ScaffoldPlacementTarget target, boolean closestToPlayer) {
      if (target.airPlace()) {
         AABB airTargetBounds = new AABB(target.position());
         Vec3 airCenter = target.position().getCenter();
         return new ScaffoldPlacementGeometry(null, airCenter, Shapes.create(airTargetBounds));
      }

      Direction supportDirection = target.side();
      Vec3 aimPositionResult;
      if (closestToPlayer) {
         BlockPos supportPosition = target.position().relative(supportDirection);
         aimPositionResult = closestPointAtBlockBase(supportPosition);
      } else {
         aimPositionResult = target.position().relative(supportDirection).getCenter();
      }

      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      BlockPos supportBlockPosition = target.position().relative(supportDirection);
      BlockState supportState = level.getBlockState(supportBlockPosition);
      ClientLevel shapeLevel = ClientAccess.argusclientMinecraft.level;
      BlockPos shapePosition = BlockPos.ZERO;
      VoxelShape supportShape = supportState.getCollisionShape(shapeLevel, shapePosition);
      return new ScaffoldPlacementGeometry(supportDirection, aimPositionResult, supportShape);
   }

   private static Vec3 closestPointAtBlockBase(BlockPos position) {
      return new Vec3(closestX(position), position.getY(), closestZ(position));
   }

   private static double closestX(BlockPos position) {
      double playerX = ClientAccess.argusclientMinecraft.player.getX();
      double minimumX = position.getX();
      double maximumX = position.getX() + 1;
      return Mth.clamp(playerX, minimumX, maximumX);
   }

   private static double closestZ(BlockPos position) {
      double playerZ = ClientAccess.argusclientMinecraft.player.getZ();
      double minimumZ = position.getZ();
      double maximumZ = position.getZ() + 1;
      return Mth.clamp(playerZ, minimumZ, maximumZ);
   }
}
