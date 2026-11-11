package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.WorldStateManager;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class PusherTargetSelector implements ClientAccess {
   private static final List<Block> excludedHeadBlocks;
   private final PusherModule module;

   private boolean isValidTarget(Player target) {
      BlockPos position = target.blockPosition();
      ClientLevel headLevelSnapshot = ClientAccess.argusclientMinecraft.level;
      BlockPos headPosition = position.above();
      BlockState headState = headLevelSnapshot.getBlockState(headPosition);
      if (!excludedHeadBlocks.contains(headState.getBlock())) {
         float headResistance = headState.getDestroySpeed(ClientAccess.argusclientMinecraft.level, position.above());
         if (headResistance != -1.0F) {
            WorldStateManager blockTrackerSnapshot = ClientContext.worldStateManager;
            BlockPos clearanceSnapshot = position.above(2);
            if (!blockTrackerSnapshot.isPlacementOccupied(clearanceSnapshot)) {
               if (this.module.onlyHoleSetting.getValue() && !ClientContext.holeManager.intersectsHole(position)) {
                  return false;
               }

               if (this.module.onlySafeSetting.getValue() && !ClientContext.holeManager.intersectsHole(position)) {
                  headLevelSnapshot = ClientAccess.argusclientMinecraft.level;
                  BlockState safeState = headLevelSnapshot.getBlockState(target.blockPosition());
                  Block safeBlock = safeState.getBlock();
                  float safeResistance = safeBlock.getExplosionResistance();
                  if (safeResistance < 600.0F) {
                     return false;
                  }
               }

               int occupiedCount = PlayerStateUtils.getSurroundPositions(target).size();
               return occupiedCount <= 6;
            }
         }
      }

      return false;
   }

   PusherTargetSelector(PusherModule module) {
      this.module = module;
   }

   public Player selectTarget() {
      Optional<AbstractClientPlayer> selectedSnapshot = ClientAccess.argusclientMinecraft
         .level
         .players()
         .stream()
         .filter(candidate -> {
            if (candidate.isAlive() && candidate != ClientAccess.argusclientMinecraft.player) {
               AABB bounds = candidate.getBoundingBox();
               double height = bounds.getYsize();
               if (height > 1.0 && EntityUtils.hasCollisionBelow(candidate)) {
                  float distance = ClientAccess.argusclientMinecraft.player.distanceTo(candidate);
                  PusherModule moduleSnapshot = this.module;
                  if (distance <= moduleSnapshot.getRange() && !ClientContext.socialRegistry.isFriendPlayer(candidate)) {
                     return true;
                  }
               }
            }

            return false;
         })
         .filter(candidate -> !this.module.ignoreNakedSetting.getValue() || PlayerStateUtils.hasArmor(candidate))
         .filter(this::isValidTarget)
         .min(Comparator.comparing(candidate -> {
            float yawSnapshot = ClientAccess.argusclientMinecraft.player.getYRot();
            float candidateYaw = RotationManager.getRotationsToEntity(candidate)[0];
            float yawDifference = Mth.degreesDifferenceAbs(yawSnapshot, candidateYaw);
            return yawDifference;
         }));
      return selectedSnapshot.orElse(null);
   }

   static {
      Block respawnAnchor = Blocks.RESPAWN_ANCHOR;
      Block reinforcedDeepslate = Blocks.REINFORCED_DEEPSLATE;
      Block cryingObsidian = Blocks.CRYING_OBSIDIAN;
      Block obsidian = Blocks.OBSIDIAN;
      excludedHeadBlocks = List.of(obsidian, cryingObsidian, reinforcedDeepslate, respawnAnchor);
   }
}
