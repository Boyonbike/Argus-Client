package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AntiPhaseLadderPlanner implements ClientAccess, AntiPhasePlacementPlanner {
   private static final List<AntiPhasePlacementShape> ladderShapes;

   @Override
   public AntiPhasePlacement findPlacement(Player target) {
      BlockPos targetPosition = PlayerStateUtils.getRoundedBlockPosition(target);
      Direction bestDirection = null;
      AABB bestBounds = null;
      float bestDistanceSquared = 999.0F;
      if (!WorldUtils.isAir(targetPosition)) {
         return null;
      }

      for (AntiPhasePlacementShape shape : ladderShapes) {
         Direction supportDirection = shape.direction();
         BlockPos supportPosition = targetPosition.relative(supportDirection);
         if (!this.shouldRejectPlacement(targetPosition, target, shape)
            && (!BlockInteractionUtils.isStrictDirectionEnabled() || !this.isUnsupportedDirection(supportPosition, supportDirection))) {
            Vec3 targetPositionVector = target.position();
            Vec3 supportCenter = Vec3.atCenterOf(supportPosition);
            float distanceSquared = (float)targetPositionVector.distanceToSqr(supportCenter);
            if (distanceSquared < bestDistanceSquared) {
               bestBounds = shape.bounds();
               bestDirection = supportDirection;
               bestDistanceSquared = distanceSquared;
            }
         }
      }

      return bestDirection == null
         ? null
         : new AntiPhasePlacement(targetPosition.relative(bestDirection), bestDirection.getOpposite(), bestBounds.move(targetPosition));
   }

   protected boolean shouldRejectPlacement(BlockPos position, Player target, AntiPhasePlacementShape shape) {
      Direction supportDirection = shape.direction();
      BlockPos supportPosition = position.relative(supportDirection);
      AABB targetBoundsSnapshot = BoundingBoxUtils.getInterpolationBounds(target);
      AABB shapeBounds = shape.bounds();
      AABB worldBounds = shapeBounds.move(position);
      if (!targetBoundsSnapshot.intersects(worldBounds)) {
         BlockPos statePosition = supportPosition;
         BlockState supportState = ClientAccess.argusclientMinecraft.level.getBlockState(statePosition);
         if (!supportState.canBeReplaced()) {
            return false;
         }
      }

      return true;
   }

   private boolean isUnsupportedDirection(BlockPos supportPosition, Direction supportDirection) {
      List<Direction> validDirections = BlockInteractionUtils.getStrictDirections(supportPosition);
      return !validDirections.contains(supportDirection.getOpposite());
   }

   static {
      Direction east = Direction.EAST;
      VoxelShape eastShape = LadderBlock.SHAPES.get(Direction.EAST);
      AntiPhasePlacementShape eastPlacement = AntiPhasePlacementShape.fromShape(east, eastShape);
      Direction west = Direction.WEST;
      VoxelShape westShape = LadderBlock.SHAPES.get(Direction.WEST);
      AntiPhasePlacementShape westPlacement = AntiPhasePlacementShape.fromShape(west, westShape);
      Direction north = Direction.NORTH;
      VoxelShape northShape = LadderBlock.SHAPES.get(Direction.NORTH);
      AntiPhasePlacementShape northPlacement = AntiPhasePlacementShape.fromShape(north, northShape);
      Direction south = Direction.SOUTH;
      VoxelShape southShape = LadderBlock.SHAPES.get(Direction.SOUTH);
      AntiPhasePlacementShape southPlacement = AntiPhasePlacementShape.fromShape(south, southShape);
      ladderShapes = List.of(eastPlacement, westPlacement, northPlacement, southPlacement);
   }
}
