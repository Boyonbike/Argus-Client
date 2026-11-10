package dev.argus.client.events;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** A block in a client chunk changed, whether from a server packet or a local prediction. Client thread only. */
public final class BlockStateChangeEvent extends CancellableEvent {
   private final BlockPos pos;
   private final BlockState oldState;
   private final BlockState newState;

   public BlockStateChangeEvent(BlockPos pos, BlockState oldState, BlockState newState) {
      this.pos = pos;
      this.oldState = oldState;
      this.newState = newState;
   }

   public BlockPos getPos() {
      return this.pos;
   }

   public BlockState getOldState() {
      return this.oldState;
   }

   public BlockState getNewState() {
      return this.newState;
   }
}
