package dev.argus.client.modules.player;

import dev.argus.client.events.WorldRenderEvent;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

public class SpeedMineRenderQueue {
   private final Map<BlockPos, SpeedMineBlockRenderState> blockRenderStates = new HashMap<>();

   public void remove(BlockPos blockPos) {
      this.blockRenderStates.remove(blockPos);
   }

   public void setProgress(BlockPos blockPos, float progress) {
      SpeedMineBlockRenderState renderState = this.blockRenderStates.computeIfAbsent(blockPos, pos -> new SpeedMineBlockRenderState(pos, progress));
      renderState.setProgress(progress);
   }

   public void setProgressTwice(BlockPos blockPos, float progress) {
      SpeedMineBlockRenderState renderState = this.blockRenderStates.computeIfAbsent(blockPos, pos -> new SpeedMineBlockRenderState(pos, progress));
      renderState.setProgress(progress);
      renderState.setProgress(progress);
   }

   public void clear() {
      this.blockRenderStates.clear();
   }

   public void render(WorldRenderEvent event) {
      BlockPos fadedBlockPos = null;

      for (SpeedMineBlockRenderState renderState : this.blockRenderStates.values()) {
         renderState.render(event);
         if (renderState.isFadedOut()) {
            fadedBlockPos = renderState.getBlockPos();
         }
      }

      if (fadedBlockPos != null) {
         this.blockRenderStates.remove(fadedBlockPos);
      }
   }

   public void snapshotProgress() {
      this.blockRenderStates.values().forEach(SpeedMineBlockRenderState::snapshotProgress);
   }
}
