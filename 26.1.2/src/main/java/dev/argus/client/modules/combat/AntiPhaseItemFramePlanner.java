package dev.argus.client.modules.combat;

import dev.argus.client.ClientContext;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AntiPhaseItemFramePlanner extends AntiPhaseFullBlockPlanner {
   private static final List<AntiPhasePlacementShape> itemFrameShapes;

   @Override
   public AntiPhasePlacement findPlacement(Player target) {
      AntiPhasePlacement abovePlacement = this.findAbovePlacement(target);
      return abovePlacement != null ? abovePlacement : super.findPlacement(target);
   }

   @Override
   protected AABB getPlacementBounds(BlockPos position, Direction supportDirection) {
      for (AntiPhasePlacementShape shape : itemFrameShapes) {
         Direction shapeDirection = shape.direction();
         if (shapeDirection == supportDirection.getOpposite()) {
            AABB shapeBounds = shape.bounds();
            return shapeBounds.move(position);
         }
      }

      return new AABB(position);
   }

   private AntiPhasePlacement findAbovePlacement(Player target) {
      BlockPos targetPosition = PlayerStateUtils.getRoundedBlockPosition(target);
      BlockPos abovePosition = targetPosition.above();
      if (WorldUtils.isAir(abovePosition) && ClientContext.worldStateManager.isPlacementOccupied(targetPosition)) {
         List<Direction> validDirections = BlockInteractionUtils.getStrictDirections(targetPosition);
         return BlockInteractionUtils.isStrictDirectionEnabled() && !validDirections.contains(Direction.UP)
            ? null
            : new AntiPhasePlacement(targetPosition, Direction.UP, this.getPlacementBounds(targetPosition.above(), Direction.DOWN));
      } else {
         return null;
      }
   }

   static {
      Direction east = Direction.EAST;
      VoxelShape eastBounds = Block.box(0.0, 2.0, 2.0, 1.0, 14.0, 14.0);
      AntiPhasePlacementShape eastShape = AntiPhasePlacementShape.fromShape(east, eastBounds);
      Direction west = Direction.WEST;
      VoxelShape westBounds = Block.box(15.0, 2.0, 2.0, 16.0, 14.0, 14.0);
      AntiPhasePlacementShape westShape = AntiPhasePlacementShape.fromShape(west, westBounds);
      Direction north = Direction.NORTH;
      VoxelShape northBounds = Block.box(2.0, 2.0, 0.0, 14.0, 14.0, 1.0);
      AntiPhasePlacementShape northShape = AntiPhasePlacementShape.fromShape(north, northBounds);
      Direction south = Direction.SOUTH;
      VoxelShape southBounds = Block.box(2.0, 2.0, 15.0, 14.0, 14.0, 16.0);
      AntiPhasePlacementShape southShape = AntiPhasePlacementShape.fromShape(south, southBounds);
      Direction down = Direction.DOWN;
      VoxelShape downBounds = Block.box(2.0, 0.0, 2.0, 14.0, 1.0, 14.0);
      AntiPhasePlacementShape downShape = AntiPhasePlacementShape.fromShape(down, downBounds);
      Direction up = Direction.UP;
      VoxelShape upBounds = Block.box(2.0, 15.0, 2.0, 14.0, 16.0, 14.0);
      AntiPhasePlacementShape upShape = AntiPhasePlacementShape.fromShape(up, upBounds);
      itemFrameShapes = List.of(eastShape, westShape, northShape, southShape, downShape, upShape);
   }
}
