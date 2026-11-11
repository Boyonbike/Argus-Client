package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.inventory.AutoMineCandidates;
import dev.argus.client.utils.inventory.AutoMineProviderBase;
import java.util.Iterator;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class AutoMineSelfProvider extends AutoMineProviderBase {
   private final ElapsedTimer recentTimer = new ElapsedTimer();
   private BlockPos recentPosition;
   private int recentPriority;

   private boolean isClosedUpperTrapdoor(BlockPos position) {
      BlockPos testedPosition = position;
      BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(testedPosition);
      if (!(state.getBlock() instanceof TrapDoorBlock)) {
         return false;
      }

      BooleanProperty openProperty = TrapDoorBlock.OPEN;
      Boolean open = state.getValue(openProperty);
      if (open) {
         return false;
      }

      EnumProperty halfProperty = TrapDoorBlock.HALF;
      return state.getValue(halfProperty) != Half.BOTTOM;
   }

   public AutoMineSelfProvider(AutoMineModule module) {
      super(module);
   }

   private BlockPos selectFaceCandidate() {
      Setting<Boolean> faceSetting = this.autoMine.faceSetting;
      if (!faceSetting.getValue()) {
         return null;
      }

      if (PlayerStateUtils.isLocalLowProfile()) {
         return null;
      }

      BlockPos eyePosition = BlockPos.containing(ClientAccess.argusclientMinecraft.player.getEyePosition());
      BlockState eyeState = ClientAccess.argusclientMinecraft.level.getBlockState(eyePosition);
      if (!eyeState.isAir()) {
         Block eyeBlock = eyeState.getBlock();
         float resistance = eyeBlock.getExplosionResistance();
         if (!(resistance < 600.0F) && this.canMinePosition(eyePosition)) {
            BlockPos feetPosition = ClientAccess.argusclientMinecraft.player.blockPosition();
            BlockState feetState = ClientAccess.argusclientMinecraft.level.getBlockState(feetPosition);
            if (!feetState.isAir() && !this.canMinePosition(feetPosition)) {
               return null;
            }

            return eyePosition;
         }
      }

      return null;
   }

   @Override
   public void collectCandidates(AutoMineCandidates candidates) {
      BlockPos facePosition = this.selectFaceCandidate();
      AutoMineTargetSelector targetSelector = this.autoMine.targetSelector;
      if ((targetSelector.selectTarget() != null || facePosition != null) && !ClientAccess.argusclientMinecraft.player.isFallFlying()) {
         this.offerRecentCandidate(candidates);
         AABB playerBounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
         Setting<Boolean> onlyCrawlSetting = this.autoMine.onlyCrawlSetting;
         if (!onlyCrawlSetting.getValue() || PlayerStateUtils.isLocalLowProfile() || facePosition != null) {
            AABB boundsSnapshot = playerBounds;
            BlockPos candidatePosition = this.selectSelfCandidate(boundsSnapshot);
            int basePriority;
            if (!PlayerStateUtils.isLocalLowProfile() && facePosition == null) {
               basePriority = 100;
            } else {
               basePriority = 1500;
            }

            int priority = basePriority;
            Setting<Boolean> tryCrawlSetting = this.autoMine.tryCrawlSetting;
            if (tryCrawlSetting.getValue() && !PlayerStateUtils.isLocalLowProfile() && candidatePosition != null) {
               long candidateY = candidatePosition.getY();
               double playerY = ClientAccess.argusclientMinecraft.player.getY();
               if (candidateY == Math.round(playerY)) {
                  priority = 1000;
               }
            }

            if (candidatePosition != null) {
               Consumer<AutoMineCandidates> candidateUpdate = output -> {
                  BlockPos primaryPosition = candidatePosition;
                  AutoMineCandidates primaryOutput = output;
                  primaryOutput.setPrimaryPosition(primaryPosition);
               };
               candidates.offer(priority, candidateUpdate);
               this.recentPosition = candidatePosition;
               this.recentPriority = priority;
               this.recentTimer.reset();
            }
         }
      }
   }

   private boolean hasGroundedResistantSupport(BlockPos supportPosition) {
      BlockPos testedPosition = supportPosition;
      BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(testedPosition);
      Block block = state.getBlock();
      float resistance = block.getExplosionResistance();
      return resistance < 600.0F ? false : ClientAccess.argusclientMinecraft.player.onGround();
   }

   private void offerRecentCandidate(AutoMineCandidates candidates) {
      Setting<Boolean> instantSetting = this.autoMine.instantSetting;
      if (instantSetting.getValue() && !this.recentTimer.hasElapsed(150L) && this.recentPosition != null) {
         int retainedPriority = this.recentPriority;
         Consumer<AutoMineCandidates> recentUpdate = output -> {
            BlockPos primaryPosition = this.recentPosition;
            AutoMineCandidates primaryOutput = output;
            primaryOutput.setPrimaryPosition(primaryPosition);
         };
         candidates.offer(retainedPriority, recentUpdate);
      }
   }

   @Override
   public boolean isEnabled() {
      Setting<Boolean> enabledSetting = this.autoMine.selfSetting;
      return enabledSetting.getValue();
   }

   private BlockPos selectSelfCandidate(AABB playerBounds) {
      BlockPos basePosition = PlayerStateUtils.getLocalRoundedBlockPosition();
      BlockPos belowPosition = basePosition.below();
      AABB boundsSnapshot = playerBounds;
      BlockPos overheadPosition = this.getOverheadPosition(boundsSnapshot);
      BlockPos facePosition = this.selectFaceCandidate();
      Setting<Boolean> tryCrawlSetting = this.autoMine.tryCrawlSetting;
      if (tryCrawlSetting.getValue()) {
         BlockPos baseCandidate = basePosition;
         if (this.canMinePosition(baseCandidate) && !PlayerStateUtils.isLocalLowProfile()) {
            BlockPos recentBase = basePosition;
            if (this.isAlreadyMining(recentBase)) {
               return basePosition;
            }

            BlockPos supportPosition = overheadPosition.below();
            if (this.hasGroundedResistantSupport(supportPosition)) {
               return basePosition;
            }
         }
      }

      BlockPos belowCandidate = belowPosition;
      if (this.canMinePosition(belowCandidate)) {
         BlockPos recentBelow = belowPosition;
         if ((this.isAlreadyMining(recentBelow) || PlayerStateUtils.isLocalLowProfile()) && this.shouldMineDown()) {
            return belowPosition;
         }
      }

      BlockPos belowOverhead = overheadPosition.below();
      if (this.isClosedUpperTrapdoor(belowOverhead) && PlayerStateUtils.isLocalLowProfile()) {
         overheadPosition = overheadPosition.below();
      }

      Setting<Boolean> finalCrawlSetting = this.autoMine.tryCrawlSetting;
      if (finalCrawlSetting.getValue() && PlayerStateUtils.isLocalLowProfile()) {
         return null;
      }

      if (facePosition != null) {
         overheadPosition = facePosition;
      }

      return this.canMinePosition(overheadPosition) ? overheadPosition : null;
   }

   private BlockPos getOverheadPosition(AABB playerBounds) {
      Vec3 positionSnapshot = ClientAccess.argusclientMinecraft.player.position();
      double heightOffset = playerBounds.getYsize() - BoundingBoxUtils.blockCornerInset;
      Vec3 overheadSnapshot = positionSnapshot.add(0.0, heightOffset, 0.0);
      BlockPos headPosition = BlockPos.containing(overheadSnapshot);
      return headPosition.above();
   }

   private boolean shouldMineDown() {
      Setting<Boolean> downSetting = this.autoMine.downSetting;
      if (!downSetting.getValue()) {
         return false;
      }

      Setting<Boolean> downPrioritySetting = this.autoMine.downPrioritySetting;
      if (downPrioritySetting.getValue()) {
         return true;
      }

      ClientLevel levelSnapshot = ClientAccess.argusclientMinecraft.level;
      BlockPos positionSnapshot = PlayerStateUtils.getLocalRoundedBlockPosition();
      BlockPos basePosition = positionSnapshot;
      ClientLevel probeLevel = levelSnapshot;
      BlockPos probePosition = basePosition.below(2);
      if (probeLevel.isEmptyBlock(probePosition)) {
         return false;
      }

      Iterator<BlockPos> occupiedIterator = PlayerStateUtils.getSurroundPositions(ClientAccess.argusclientMinecraft.player).iterator();

      float resistance;
      do {
         if (!occupiedIterator.hasNext()) {
            return true;
         }

         BlockPos occupiedPosition = occupiedIterator.next();
         levelSnapshot = ClientAccess.argusclientMinecraft.level;
         BlockPos belowPosition = occupiedPosition.below();
         BlockState state = levelSnapshot.getBlockState(belowPosition);
         if (state.isAir()) {
            break;
         }

         Block block = state.getBlock();
         resistance = block.getExplosionResistance();
      } while (!(resistance < 600.0F));

      return false;
   }
}
