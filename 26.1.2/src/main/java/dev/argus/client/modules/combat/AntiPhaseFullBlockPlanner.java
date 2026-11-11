package dev.argus.client.modules.combat;

import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public class AntiPhaseFullBlockPlanner implements AntiPhasePlacementPlanner {
   @Override
   public AntiPhasePlacement findPlacement(Player target) {
      BlockPos targetPosition = PlayerStateUtils.getRoundedBlockPosition(target);
      boolean strictDirections = BlockInteractionUtils.isStrictDirectionEnabled();
      Direction supportDirection = BlockInteractionUtils.findSupportDirection(targetPosition, strictDirections);
      return supportDirection != null && WorldUtils.isAir(targetPosition)
         ? new AntiPhasePlacement(
            targetPosition.relative(supportDirection), supportDirection.getOpposite(), this.getPlacementBounds(targetPosition, supportDirection)
         )
         : null;
   }

   protected AABB getPlacementBounds(BlockPos position, Direction supportDirection) {
      return new AABB(position);
   }
}
