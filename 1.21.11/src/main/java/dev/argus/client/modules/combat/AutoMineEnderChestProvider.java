package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.inventory.AutoMineCandidates;
import dev.argus.client.utils.inventory.AutoMineProviderBase;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.phys.Vec3;

public final class AutoMineEnderChestProvider extends AutoMineProviderBase {
   public AutoMineEnderChestProvider(AutoMineModule module) {
      super(module);
   }

   @Override
   public void collectCandidates(AutoMineCandidates candidates) {
      BlockPos selectedPosition = null;

      for (BlockEntity blockEntity : ClientContext.worldStateManager.getBlockEntities()) {
         Vec3 eyeSnapshot = ClientAccess.argusclientMinecraft.player.getEyePosition();
         BlockPos blockPosition = blockEntity.getBlockPos();
         Vec3 blockCenter = blockPosition.getCenter();
         double distance = eyeSnapshot.distanceTo(blockCenter);
         Setting<Float> rangeSetting = this.autoMine.rangeSetting;
         Float range = rangeSetting.getValue();
         if (!(distance > range.floatValue()) && blockEntity instanceof EnderChestBlockEntity) {
            BlockPos validityPosition = blockEntity.getBlockPos();
            if (this.canMinePosition(validityPosition)) {
               if (selectedPosition != null) {
                  BlockPos miningPositionSnapshot = blockEntity.getBlockPos();
                  if (!this.isAlreadyMining(miningPositionSnapshot)) {
                     continue;
                  }
               }

               selectedPosition = blockEntity.getBlockPos();
            }
         }
      }

      BlockPos selectedSnapshot = selectedPosition;
      if (selectedSnapshot != null) {
         Consumer<AutoMineCandidates> update = output -> output.setPrimaryPosition(selectedSnapshot);
         candidates.offer(5, update);
      }
   }

   @Override
   public boolean isEnabled() {
      Setting<Boolean> enabledSetting = this.autoMine.enderChestsSetting;
      return enabledSetting.getValue();
   }
}
