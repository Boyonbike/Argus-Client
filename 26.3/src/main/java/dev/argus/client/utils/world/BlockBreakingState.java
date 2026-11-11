package dev.argus.client.utils.world;

import net.minecraft.core.BlockPos;

public interface BlockBreakingState {
   BlockPos getCurrentBreakingBlock();

   void setCurrentBreakingBlock(BlockPos var1);

   float getBreakingProgress();

   void setBreakingProgress(float var1);

   boolean isBreakingBlock();

   void setBreakingBlock(boolean var1);
}
