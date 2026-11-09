package dev.argus.client.events;

import net.minecraft.world.InteractionHand;

public final class MultiPlayerGameModeUseItemEvent extends CancellableEvent {
   private final InteractionHand hand;

   public MultiPlayerGameModeUseItemEvent(InteractionHand hand) {
      this.hand = hand;
   }

   public InteractionHand getHand() {
      return this.hand;
   }
}
