package dev.argus.client.modules.combat;

import dev.argus.client.settings.Setting;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.inventory.AutoMineCandidates;
import dev.argus.client.utils.inventory.AutoMineProviderBase;
import dev.argus.client.utils.world.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public final class AutoMineFishProvider extends AutoMineProviderBase {
   public AutoMineFishProvider(AutoMineModule module) {
      super(module);
   }

   @Override
   public void collectCandidates(AutoMineCandidates candidates) {
      AutoMineTargetSelector targetSelector = this.autoMine.targetSelector;
      Player target = targetSelector.selectTarget();
      if (target != null) {
         AutoMineTargetSelector boundsSelector = this.autoMine.targetSelector;
         long occupiedCount = BlockPos.betweenClosedStream(boundsSelector.getTargetFootprintBounds(target)).count();
         if (occupiedCount == 1L) {
            BlockPos targetPosition = PlayerStateUtils.getRoundedBlockPosition(target);
            if (!WorldUtils.isAir(targetPosition) && !WorldUtils.canMineBlock(targetPosition)) {
               BlockPos supportPosition = targetPosition.below();
               if (WorldUtils.canMineBlock(supportPosition)) {
                  candidates.offer(400, output -> {
                     AutoMineModule module = this.autoMine;
                     if (module.isExtraBreakEnabled()) {
                        BlockPos secondaryPosition = targetPosition.below();
                        output.setSecondaryPosition(secondaryPosition);
                     } else {
                        BlockPos primaryPosition = targetPosition.below();
                        output.setPrimaryPosition(primaryPosition);
                     }
                  });
                  return;
               }
            }
         }
      }
   }

   @Override
   public boolean isEnabled() {
      Setting<Boolean> enabledSetting = this.autoMine.fishSetting;
      return enabledSetting.getValue();
   }
}
