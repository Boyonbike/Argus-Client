package dev.argus.client.events;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SignBlockEntity;

public final class ClientPacketListenerHandleOpenSignEditorEvent extends CancellableEvent {
   private final BlockPos position;
   private final SignBlockEntity sign;
   private final boolean frontText;

   public BlockPos getPosition() {
      return this.position;
   }

   public ClientPacketListenerHandleOpenSignEditorEvent(BlockPos position, SignBlockEntity sign, boolean frontText) {
      this.position = position;
      this.sign = sign;
      this.frontText = frontText;
   }

   public boolean isFrontText() {
      return this.frontText;
   }

   public SignBlockEntity getSign() {
      return this.sign;
   }
}
