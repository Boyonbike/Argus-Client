package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.inventory.AutoMineCandidates;
import dev.argus.client.utils.inventory.AutoMineProviderBase;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class AutoMineBurrowProvider extends AutoMineProviderBase {
   public AutoMineBurrowProvider(AutoMineModule module) {
      super(module);
   }

   @Override
   public void collectCandidates(AutoMineCandidates candidates) {
      AutoMineModule module = this.autoMine;
      boolean extraBreak = module.isExtraBreakEnabled();
      AutoMineTargetSelector targetSelector = this.autoMine.targetSelector;
      Player target = targetSelector.selectTarget();
      if (target != null) {
         double heightSnapshot = target.getY();
         float targetY = (float)Math.round(heightSnapshot) + 0.5F;
         AABB boundsSnapshot = target.getBoundingBox();
         double inflateX = -BoundingBoxUtils.blockCornerInset;
         double inflateZ = -BoundingBoxUtils.blockCornerInset;
         AABB inflatedSnapshot = boundsSnapshot.inflate(inflateX, 0.0, inflateZ);
         double minimumY = targetY;
         AABB minimumSnapshot = inflatedSnapshot.setMinY(minimumY);
         double maximumY = targetY;
         AABB footBounds = minimumSnapshot.setMaxY(maximumY);
         List<BlockPos> burrowCandidates = BlockPos.betweenClosedStream(footBounds)
            .map(BlockPos::immutable)
            .filter(x$0 -> this.canMinePosition(x$0))
            .filter(position -> {
               BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(position);
               Block block = state.getBlock();
               float resistance = block.getExplosionResistance();
               return resistance >= 600.0F && block != Blocks.COBWEB && block != Blocks.ANVIL;
            })
            .sorted(Comparator.comparing(position -> {
               if (this.isAlreadyMining(position)) {
                  return -1.0;
               }

               Vec3 center = Vec3.atCenterOf(position);
               double distanceSquared = target.distanceToSqr(center);
               return distanceSquared;
            }))
            .toList();
         if (!burrowCandidates.isEmpty()) {
            int priority = extraBreak && burrowCandidates.size() == 1 ? 500 : 700;
            candidates.offer(priority, output -> {
               if (!extraBreak) {
                  BlockPos firstPrimaryPosition = burrowCandidates.getFirst();
                  AutoMineCandidates firstPrimaryOutput = output;
                  firstPrimaryOutput.setPrimaryPosition(firstPrimaryPosition);
               } else if (burrowCandidates.size() == 1) {
                  BlockPos singleSecondaryPosition = burrowCandidates.getFirst();
                  AutoMineCandidates singleSecondaryOutput = output;
                  singleSecondaryOutput.setSecondaryPosition(singleSecondaryPosition);
               } else {
                  BlockPos primaryPosition = burrowCandidates.getFirst();
                  AutoMineCandidates primaryOutput = output;
                  primaryOutput.setPrimaryPosition(primaryPosition);
                  BlockPos secondaryPosition = burrowCandidates.get(1);
                  AutoMineCandidates secondaryOutput = output;
                  secondaryOutput.setSecondaryPosition(secondaryPosition);
               }
            });
         }
      }
   }

   @Override
   public boolean isEnabled() {
      Setting<Boolean> enabledSetting = this.autoMine.burrowSetting;
      return enabledSetting.getValue();
   }
}
