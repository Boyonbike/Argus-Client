package dev.argus.client.events;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

public final class MultiPlayerGameModeUseItemOnEvent extends CancellableEvent {
   private final BlockHitResult hitResult;
   private final InteractionHand hand;

   public InteractionHand getHand() {
      return this.hand;
   }

   public MultiPlayerGameModeUseItemOnEvent(BlockHitResult hitResult, InteractionHand hand) {
      this.hitResult = hitResult;
      this.hand = hand;
   }

   public BlockHitResult getHitResult() {
      return this.hitResult;
   }
}
