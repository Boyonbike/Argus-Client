package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.settings.options.BlockSampleMode;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.combat.DamageUtils;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class BedPlacementScanner implements ClientAccess {
   private static final MutableBlockPos scanPosition = new MutableBlockPos();
   private final BedAuraModule module;
   private Direction bedDirection;
   private Vec3 hitPoint;
   private float range;

   public BedPlacementScanner(BedAuraModule module) {
      this.module = module;
   }

   public BedPlacementCandidate scanPlacement(LivingEntity target) {
      Float rangeValue = this.module.placeRangeSetting.getValue();
      this.range = rangeValue;
      Vec3 targetPosition = target.position();
      BedPlacementCandidate bestPlacement = null;

      for (float offsetX = -2.0F; offsetX < 2.0F; offsetX++) {
         for (float offsetY = -2.0F; offsetY < 3.0F; offsetY++) {
            for (float offsetZ = -2.0F; offsetZ < 2.0F; offsetZ++) {
               this.hitPoint = null;
               MutableBlockPos mutablePosition = scanPosition;
               double scanXSnapshot = targetPosition.x + offsetX;
               double scanYSnapshot = targetPosition.y + offsetY;
               double scanZ = targetPosition.z + offsetZ;
               double scanY = scanYSnapshot;
               mutablePosition.set(scanXSnapshot, scanY, scanZ);
               MutableBlockPos candidatePosition = scanPosition;
               LivingEntity candidateTarget = target;
               BedPlacementCandidate candidate = this.evaluatePlacement(candidateTarget, candidatePosition);
               if (candidate != null && (bestPlacement == null || candidate.targetDamage() >= bestPlacement.targetDamage())) {
                  bestPlacement = candidate;
               }
            }
         }
      }

      return bestPlacement;
   }

   private Direction selectSupportDirection(Direction facing, BlockPos position, boolean requireSupport) {
      boolean airPlace = this.module.airPlaceSetting.getValue();

      for (Direction direction : Direction.values()) {
         BlockPos adjacentPosition = position.relative(direction);
         float adjacentY = adjacentPosition.getY();
         if (!(adjacentY >= WorldUtils.getTopY()) && (WorldUtils.isPlacementOccupied(adjacentPosition) || airPlace)) {
            if (requireSupport) {
               List supportDirections = BlockInteractionUtils.getStrictDirections(adjacentPosition);
               if (!supportDirections.contains(direction.getOpposite())) {
                  continue;
               }
            }

            if (!this.module.rotateSetting.getValue()) {
               return direction;
            }

            this.hitPoint = this.selectHitPoint(direction, facing, position);
            if (this.hitPoint != null) {
               return direction;
            }
         }
      }

      return null;
   }

   private Vec3 selectHitPoint(Direction supportDirection, Direction facing, BlockPos position) {
      Direction sampleDirection = supportDirection;
      BlockPos samplePosition = position;

      for (Vec3 sample : this.sampleHitPoints(samplePosition, sampleDirection)) {
         if (!(sample.distanceTo(ClientAccess.argusclientMinecraft.player.getEyePosition()) > this.range)) {
            double yaw = RotationManager.getRotationsToPosition(sample)[0];
            Direction sampleFacing = Direction.fromYRot(yaw);
            if (facing == sampleFacing) {
               return sample;
            }
         }
      }

      return null;
   }

   private BlockHitResult createHitResult(Direction direction, BlockPos position) {
      label22: {
         if (this.module.airPlaceSetting.getValue()) {
            BlockPos adjacentPosition = position.relative(direction);
            if (!WorldUtils.isPlacementOccupied(adjacentPosition)) {
               break label22;
            }
         }

         if (!this.module.liquidPlaceSetting.getValue() || !WorldUtils.hasFluid(position)) {
            return new BlockHitResult(this.hitPoint, direction.getOpposite(), position.relative(direction), false);
         }
      }

      BlockPos pointPosition = position;
      Direction pointFacing = this.bedDirection;
      BedPlacementScanner pointSelector = this;
      this.hitPoint = pointSelector.selectHitPoint(null, pointFacing, pointPosition);
      return new BlockHitResult(this.hitPoint, direction, position, false);
   }

   private BedPlacementCandidate evaluatePlacement(LivingEntity target, BlockPos headPosition) {
      BlockPos statePosition = headPosition;
      BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(statePosition);
      if (!state.canBeReplaced()) {
         BedPart headPart = BedPart.HEAD;
         BlockPos headCheckPosition = headPosition;
         if (!this.module.isBedPart(headCheckPosition, headPart)) {
            return null;
         }
      }

      BlockPos footPosition = null;
      Direction supportDirection = null;

      for (Direction direction : Plane.HORIZONTAL) {
         BlockPos candidateFoot = headPosition.relative(direction);
         boolean secondaryPlacementFlag = false;
         if (!BlockInteractionUtils.canPlaceAt(candidateFoot, true, secondaryPlacementFlag)) {
            BedPart footPart = BedPart.FOOT;
            BlockPos footPartPosition = candidateFoot;
            if (!this.module.isBedPart(footPartPosition, footPart)) {
               continue;
            }
         }

         Direction supportFacingSnapshot = direction.getOpposite();
         boolean requireSupport = BlockInteractionUtils.isStrictDirectionEnabled();
         BlockPos supportPosition = candidateFoot;
         Direction supportFacing = supportFacingSnapshot;
         supportDirection = this.selectSupportDirection(supportFacing, supportPosition, requireSupport);
         if (supportDirection != null) {
            Direction facingSource = direction;
            this.bedDirection = facingSource.getOpposite();
            footPosition = candidateFoot;
            break;
         }
      }

      if (footPosition == null) {
         return null;
      }

      Vec3 eyeSnapshot = ClientAccess.argusclientMinecraft.player.getEyePosition();
      AABB footBounds = new AABB(footPosition);
      if (eyeSnapshot.distanceTo(BoundingBoxUtils.clampToBounds(eyeSnapshot, footBounds)) > this.range) {
         return null;
      }

      Vec3 explosionCenterSnapshot = headPosition.getCenter();
      AABB damageBounds = this.module.getDamageBounds(target);
      LivingEntity damageTarget = target;
      float targetDamage = DamageUtils.getBedDamage(explosionCenterSnapshot, damageTarget, damageBounds);
      Float minimumDamage = this.module.minDamageSetting.getValue();
      if (targetDamage < minimumDamage) {
         return null;
      }

      explosionCenterSnapshot = headPosition.getCenter();
      LocalPlayer selfPlayer = ClientAccess.argusclientMinecraft.player;
      float selfDamage = DamageUtils.getBedDamage(explosionCenterSnapshot, selfPlayer);
      Float maximumDamage = this.module.maxSelfDamageSetting.getValue();
      if (selfDamage > maximumDamage) {
         return null;
      }

      if (this.hitPoint == null && !this.module.rotateSetting.getValue()) {
         Vec3 fallbackCenterSnapshot = footPosition.getCenter();
         Direction fallbackDirection = supportDirection;
         Vec3 fallbackCenter = fallbackCenterSnapshot;
         this.hitPoint = fallbackCenter.relative(fallbackDirection, 0.5);
      }

      return new BedPlacementCandidate(footPosition, headPosition.immutable(), targetDamage, selfDamage, this.createHitResult(supportDirection, footPosition));
   }

   private List<Vec3> sampleHitPoints(BlockPos position, Direction direction) {
      if (direction == null) {
         BlockPos centerPosition = position;
         BlockSampleMode samplingMode = BlockSampleMode.X8;
         return samplingMode.sampleBlock(centerPosition);
      } else {
         BlockSampleMode samplingModeSnapshot = BlockSampleMode.X8;
         BlockPos facePositionSnapshot = position.relative(direction);
         Direction faceDirection = direction.getOpposite();
         return samplingModeSnapshot.sampleFace(facePositionSnapshot, faceDirection);
      }
   }
}
