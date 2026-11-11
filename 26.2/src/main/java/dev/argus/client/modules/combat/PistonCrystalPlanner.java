package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.settings.options.BlockSampleMode;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class PistonCrystalPlanner {
   private final PistonCrystalModule module;

   PistonCrystalPlanner(PistonCrystalModule module) {
      this.module = module;
   }

   PistonCrystalPlan findPlan(Player target) {
      for (int height = 0; height < 3; height++) {
         PistonCrystalPlan plan = this.findPlan(target, height);
         if (plan != null) {
            return plan;
         }
      }

      return null;
   }

   private PistonCrystalPlan findPlan(Player target, int height) {
      PistonCrystalPlan selected = null;
      double score = Double.MAX_VALUE;

      for (PistonCrystalPlanner.Candidate candidate : this.candidates(target)) {
         BlockPos position = candidate.position().offset(0, height, 0);
         Direction outward = candidate.outward();
         double candidateScore = -ClientAccess.argusclientMinecraft.player.getEyePosition().distanceTo(Vec3.atCenterOf(position));
         if (candidateScore < score) {
            if (!this.hasVerticalClearance(position, outward.getOpposite(), height)) {
               return null;
            }

            PistonCrystalPlan plan = this.buildPlan(position, outward);
            if (plan != null) {
               selected = plan;
               score = candidateScore;
            }
         }
      }

      return selected;
   }

   private PistonCrystalPlan buildPlan(BlockPos crystalPosition, Direction outward) {
      if (WorldUtils.isBedrockOrObsidian(crystalPosition.below())
         && WorldUtils.isReplaceable(crystalPosition)
         && !this.module.hasBlockingEntities(this.module.crystalBounds(crystalPosition), this::blocksCrystal)) {
         BlockPos base = crystalPosition.below();
         List<Direction> directions = BlockInteractionUtils.getStrictDirections(base);
         Direction face = directions.isEmpty() ? Direction.UP : directions.getFirst();
         Vec3 crystalHit = Vec3.atCenterOf(base).relative(face, 0.5);
         if (ClientAccess.argusclientMinecraft.player.getEyePosition().distanceTo(crystalHit) > this.module.placeRangeSetting.getValue().floatValue()) {
            return null;
         }

         Vec3 attackPosition = Vec3.atCenterOf(crystalPosition).relative(outward.getOpposite(), 0.02);
         if (attackPosition.distanceTo(ClientAccess.argusclientMinecraft.player.getEyePosition()) > this.module.attackRangeSetting.getValue().floatValue()) {
            return null;
         }

         PistonCrystalPlanner.PistonPlacement placement = this.findPiston(crystalPosition, outward);
         return placement == null
            ? null
            : new PistonCrystalPlan(
               crystalPosition,
               new BlockHitResult(crystalHit, face, base, false),
               placement.position(),
               outward.getOpposite(),
               placement.hit(),
               placement.powerPosition(),
               placement.powerHit()
            );
      } else {
         return null;
      }
   }

   private boolean hasVerticalClearance(BlockPos position, Direction inward, int height) {
      for (int y = 0; y < height; y++) {
         if (!WorldUtils.isReplaceable(position.relative(inward).above(y + 1))) {
            return false;
         }
      }

      return true;
   }

   private PistonCrystalPlanner.PistonPlacement findPiston(BlockPos crystalPosition, Direction outward) {
      PistonCrystalPlanner.PistonPlacement selected = null;
      double distance = Double.MAX_VALUE;

      for (BlockPos position : this.pistonPositions(crystalPosition, outward)) {
         double candidateDistance = ClientAccess.argusclientMinecraft.player.getEyePosition().distanceTo(Vec3.atCenterOf(position));
         if (!(candidateDistance >= distance)) {
            Direction inward = outward.getOpposite();
            if (!position.relative(inward).equals(crystalPosition) || !WorldUtils.isPlacementOccupied(position.relative(inward, 2))) {
               BlockHitResult hit = this.findHit(
                  position,
                  this.module.rotateSetting.getValue() ? outward : null,
                  true,
                  (support, direction) -> {
                     BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(support);
                     Block block = state.getBlock();
                     return !state.isAir()
                        && !(block instanceof PistonBaseBlock)
                        && !(block instanceof PistonHeadBlock)
                        && !(block instanceof MovingPistonBlock)
                        && !(block instanceof FireBlock);
                  }
               );
               if (hit != null) {
                  PistonCrystalPlanner.PowerPlacement power = this.module.getPowerItem() == Items.REDSTONE_TORCH
                     ? this.findTorch(position, inward, crystalPosition)
                     : this.findRedstone(position, inward, crystalPosition);
                  if (power != null) {
                     selected = new PistonCrystalPlanner.PistonPlacement(position, hit, power.position(), power.hit());
                     distance = candidateDistance;
                  }
               }
            }
         }
      }

      return selected;
   }

   private List<BlockPos> pistonPositions(BlockPos crystalPosition, Direction outward) {
      List<BlockPos> positions = new ArrayList<>();
      int dx = outward.getStepX();
      int dz = outward.getStepZ();
      int minX = dx == 0 ? -1 : Math.min(0, dx);
      int maxX = dx == 0 ? 1 : Math.max(0, dx);
      int minZ = dz == 0 ? -1 : Math.min(0, dz);
      int maxZ = dz == 0 ? 1 : Math.max(0, dz);

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            for (int y = 0; y <= 1; y++) {
               if (x != 0 || y != 0 || z != 0) {
                  BlockPos position = crystalPosition.offset(x, y, z);
                  if (this.isPistonAngleValid(position)
                     && this.allowsPistonHead(position.relative(outward.getOpposite()))
                     && !this.module.hasBlockingEntities(new AABB(position), this::isPlayer)) {
                     BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(position);
                     Block block = state.getBlock();
                     if (block instanceof PistonBaseBlock || block instanceof MovingPistonBlock || block instanceof FireBlock || state.canBeReplaced()) {
                        positions.add(position);
                     }
                  }
               }
            }
         }
      }

      return positions;
   }

   private Set<PistonCrystalPlanner.Candidate> candidates(LivingEntity target) {
      int height = (int)target.getEyeHeight(target.getPose());
      Set<PistonCrystalPlanner.Candidate> candidates = new HashSet<>();

      for (BlockPos occupied : PlayerStateUtils.getOccupiedBlockPositions(target)) {
         if (ClientAccess.argusclientMinecraft.level.getBlockState(occupied).canBeReplaced()) {
            for (Direction outward : Plane.HORIZONTAL) {
               BlockPos position = occupied.relative(outward);
               if (!BoundingBoxUtils.intersectsBlock(target.getBoundingBox(), position)) {
                  candidates.add(new PistonCrystalPlanner.Candidate(position.offset(0, height, 0), outward));
               }
            }
         }
      }

      return candidates;
   }

   private PistonCrystalPlanner.PowerPlacement findTorch(BlockPos piston, Direction facing, BlockPos crystalPosition) {
      PistonCrystalPlanner.PowerPlacement selected = null;
      double distance = Double.MAX_VALUE;

      for (Direction direction : Direction.values()) {
         if (direction != facing && direction != Direction.DOWN) {
            BlockPos position = piston.relative(direction);
            if (!position.equals(crystalPosition)) {
               double candidateDistance = ClientAccess.argusclientMinecraft.player.getEyePosition().distanceTo(Vec3.atCenterOf(position));
               if (!(candidateDistance >= distance)) {
                  BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(position);
                  if (state.canBeReplaced() || state.getBlock() instanceof RedstoneTorchBlock || state.getBlock() instanceof FireBlock) {
                     BlockHitResult hit = this.findHit(position, null, false, (support, side) -> {
                        if ((side != Direction.UP || WorldUtils.isPlacementOccupied(position.below())) && !support.equals(piston)) {
                           Block block = ClientAccess.argusclientMinecraft.level.getBlockState(support).getBlock();
                           return !(block instanceof TorchBlock) && !(block instanceof PistonBaseBlock) && !(block instanceof PistonHeadBlock);
                        } else {
                           return false;
                        }
                     });
                     if (hit != null) {
                        selected = new PistonCrystalPlanner.PowerPlacement(position, hit);
                        distance = candidateDistance;
                     }
                  }
               }
            }
         }
      }

      return selected;
   }

   private PistonCrystalPlanner.PowerPlacement findRedstone(BlockPos piston, Direction facing, BlockPos crystalPosition) {
      PistonCrystalPlanner.PowerPlacement selected = null;
      double distance = Double.MAX_VALUE;

      for (Direction direction : Direction.values()) {
         if (direction != facing) {
            BlockPos position = piston.relative(direction);
            if (!position.equals(crystalPosition) && position.getY() >= crystalPosition.getY()) {
               double candidateDistance = ClientAccess.argusclientMinecraft.player.getEyePosition().distanceTo(Vec3.atCenterOf(position));
               if (!(candidateDistance >= distance)) {
                  BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(position);
                  if ((state.canBeReplaced() || state.is(Blocks.REDSTONE_BLOCK)) && !this.module.hasBlockingEntities(new AABB(position), this::isPlayer)) {
                     BlockHitResult hit = this.findHit(position, null, true, (support, side) -> true);
                     if (hit != null) {
                        selected = new PistonCrystalPlanner.PowerPlacement(position, hit);
                        distance = candidateDistance;
                     }
                  }
               }
            }
         }
      }

      return selected;
   }

   private BlockHitResult findHit(BlockPos position, Direction facing, boolean allowAir, BiPredicate<BlockPos, Direction> predicate) {
      boolean strict = BlockInteractionUtils.isStrictDirectionEnabled();
      if (this.module.airPlaceSetting.getValue() && allowAir) {
         List<Direction> directions = BlockInteractionUtils.getStrictDirectionsOrFallback(position, Direction.UP);
         return new BlockHitResult(Vec3.atCenterOf(position), directions.getFirst(), position, false);
      }

      for (Direction direction : Direction.values()) {
         BlockPos support = position.relative(direction);
         if (!(support.getY() >= WorldUtils.getTopY())
            && WorldUtils.isPlacementOccupied(support)
            && (predicate == null || predicate.test(support, direction))
            && (!strict || BlockInteractionUtils.getStrictDirections(support).contains(direction.getOpposite()))) {
            Vec3 point = this.selectHitPoint(support, direction.getOpposite(), facing, this.module.placeRangeSetting.getValue());
            if (point != null) {
               return new BlockHitResult(point, direction.getOpposite(), support, false);
            }
         }
      }

      return null;
   }

   private Vec3 selectHitPoint(BlockPos position, Direction side, Direction facing, float range) {
      for (Vec3 point : BlockSampleMode.X8.sampleFace(position, side)) {
         if (!(point.distanceTo(ClientAccess.argusclientMinecraft.player.getEyePosition()) > range)) {
            if (facing == null) {
               return point;
            }

            float[] rotations = RotationManager.getRotationsToPosition(point);
            if (Math.abs(rotations[1]) <= 45.0F && Direction.fromYRot(rotations[0]) == facing) {
               return point;
            }
         }
      }

      return null;
   }

   private boolean isPistonAngleValid(BlockPos position) {
      Vec3 eyes = ClientAccess.argusclientMinecraft.player.getEyePosition();
      double dx = eyes.x - position.getX() - 0.5;
      double dz = eyes.z - position.getZ() - 0.5;
      return Math.sqrt(dx * dx + dz * dz) > Math.abs(eyes.y - position.getY() - 0.5);
   }

   private boolean allowsPistonHead(BlockPos position) {
      Block block = ClientAccess.argusclientMinecraft.level.getBlockState(position).getBlock();
      return block instanceof AirBlock
         || block instanceof FireBlock
         || block instanceof PistonHeadBlock
         || block instanceof MovingPistonBlock
         || block instanceof RedstoneTorchBlock;
   }

   private boolean isPlayer(Entity entity) {
      return !entity.isSpectator() && entity instanceof Player;
   }

   private boolean blocksCrystal(Entity entity) {
      return !entity.isSpectator() && (entity instanceof ItemEntity && entity.tickCount > 3 || entity instanceof LivingEntity);
   }

   private record Candidate(BlockPos position, Direction outward) {
   }

   private record PistonPlacement(BlockPos position, BlockHitResult hit, BlockPos powerPosition, BlockHitResult powerHit) {
   }

   private record PowerPlacement(BlockPos position, BlockHitResult hit) {
   }
}
