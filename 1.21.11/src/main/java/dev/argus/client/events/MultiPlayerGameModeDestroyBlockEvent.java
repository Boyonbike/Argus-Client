package dev.argus.client.events;

import net.minecraft.core.BlockPos;

public class MultiPlayerGameModeDestroyBlockEvent extends CancellableEvent {
   public BlockPos pos;

   public MultiPlayerGameModeDestroyBlockEvent(BlockPos pos) {
      this.pos = pos;
   }

   public BlockPos getPos() {
      return this.pos;
   }
}
