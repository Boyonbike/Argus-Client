package dev.argus.client.events;

import net.minecraft.core.BlockPos;

public final class CrystalPlacementEvent extends CancellableEvent {
   private final BlockPos pos;

   public CrystalPlacementEvent(BlockPos pos) {
      this.pos = pos;
   }

   public BlockPos getPos() {
      return this.pos;
   }
}
