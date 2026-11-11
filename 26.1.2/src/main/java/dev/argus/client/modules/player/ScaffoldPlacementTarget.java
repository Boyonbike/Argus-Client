package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.client.utils.world.BlockInteractionUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record ScaffoldPlacementTarget(BlockPos position, Direction side, boolean airPlace) {
   private static Vec3 findOrigin(ScaffoldModule module) {
      AABB playerBounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
      Vec3 origin = BoundingBoxUtils.getBottomCenter(playerBounds);
      if (module.tellySetting.getValue()) {
         origin = origin.with(Axis.Y, module.tellyBaseY);
      }

      if (ClientAccess.argusclientMinecraft.player.isPassenger()) {
         AABB vehicleBounds = ClientAccess.argusclientMinecraft.player.getVehicle().getBoundingBox();
         origin = BoundingBoxUtils.getBottomCenter(vehicleBounds);
      }

      return origin;
   }

   static List<ScaffoldPlacementTarget> findMultiFor(ScaffoldModule module, int size, int frequency) {
      Vec3 origin = findOrigin(module);
      double supportY = module.findSupportHeight((float)origin.y());
      int radius = size - 1;
      List<ScaffoldPlacementTarget> targets = new ArrayList<>();

      for (int x = -radius; x <= radius; x++) {
         for (int z = -radius; z <= radius; z++) {
            BlockPos position = BlockPos.containing(origin.add(x, -origin.y() + supportY, z));
            if (module.isSelectedBlockNonCollidable() && !ClientAccess.argusclientMinecraft.level.getBlockState(position).canBeReplaced()) {
               position = position.above();
            }

            if (module.canPlaceAt(position, module.blockSlot)) {
               Direction side = BlockInteractionUtils.findSupportDirection(position, BlockInteractionUtils.isStrictDirectionEnabled());
               if (module.airPlaceSetting.getValue()) {
                  side = null;
               }

               if (side != null || module.airPlaceSetting.getValue()) {
                  targets.add(new ScaffoldPlacementTarget(position, side, side == null));
               }
            }
         }
      }

      targets.sort(Comparator.comparing(target -> {
         Vec3 center = target.position().getCenter();
         double deltaX = origin.x - center.x;
         double deltaZ = origin.z - center.z;
         return (float)(deltaX * deltaX + deltaZ * deltaZ);
      }));
      return targets.stream().limit(frequency).toList();
   }

   static ScaffoldPlacementTarget findFor(ScaffoldModule module) {
      BlockPos position = null;
      Vec3 origin = findOrigin(module);
      LocalPlayer vehicleCheckPlayer = ClientAccess.argusclientMinecraft.player;
      boolean collidingBelow;
      if (vehicleCheckPlayer.isPassenger()) {
         collidingBelow = ClientAccess.argusclientMinecraft.player.getVehicle().verticalCollisionBelow;
      } else {
         collidingBelow = ClientAccess.argusclientMinecraft.player.verticalCollisionBelow;
      }

      float sampleY = (float)origin.y();
      double supportY = module.findSupportHeight(sampleY);
      int verticalOffset = 0;

      while (true) {
         Float maximumOffset = module.offsetSetting.getValue();
         if (verticalOffset >= maximumOffset + 1.0F) {
            break;
         }

         boolean canSearchForward = false;
         if (MovementPhysics.isLocalPlayerMoving()) {
            canSearchForward = ClientAccess.argusclientMinecraft.player.onGround();
         }

         if (!canSearchForward && verticalOffset > 0) {
            break;
         }

         Axis sampleAxis = Axis.Y;
         Vec3 verticalSample = origin.with(sampleAxis, supportY);
         Vec3 movementOffset = PlayerStateUtils.getMovementVector().scale(verticalOffset);
         position = BlockPos.containing(verticalSample.add(movementOffset));
         boolean checkBlockedPosition = module.isSelectedBlockNonCollidable();
         if (!checkBlockedPosition && !collidingBelow) {
            Boolean tellyForwardEnabled = module.tellySetting.getValue();
            checkBlockedPosition = !tellyForwardEnabled;
         }

         if (checkBlockedPosition) {
            BlockState currentBlockState = ClientAccess.argusclientMinecraft.level.getBlockState(position);
            if (!currentBlockState.canBeReplaced()) {
               position = position.above();
            }
         }

         int placementSlot = module.blockSlot;
         if (module.canPlaceAt(position, placementSlot)) {
            break;
         }

         position = null;
         verticalOffset++;
      }

      if (position == null) {
         return null;
      }

      boolean supportFlags = BlockInteractionUtils.isStrictDirectionEnabled();
      Direction supportSide = BlockInteractionUtils.findSupportDirection(position, supportFlags);
      if (supportSide != null) {
         return new ScaffoldPlacementTarget(position, supportSide, false);
      }

      if (module.airPlaceSetting.getValue()) {
         return new ScaffoldPlacementTarget(BlockPos.containing(origin).below(), null, true);
      }

      for (int searchDistance = 1; searchDistance < 3; searchDistance++) {
         for (Direction searchDirection : Direction.values()) {
            if (searchDirection != Direction.UP) {
               BlockPos neighborPosition = position.relative(searchDirection, searchDistance);
               boolean neighborSupportFlags = BlockInteractionUtils.isStrictDirectionEnabled();
               Direction neighborSupportSide = BlockInteractionUtils.findSupportDirection(neighborPosition, neighborSupportFlags);
               if (neighborSupportSide != null) {
                  BlockPos neighborTargetPosition = position.relative(searchDirection);
                  return new ScaffoldPlacementTarget(neighborTargetPosition, neighborSupportSide, false);
               }
            }
         }
      }

      return null;
   }
}
