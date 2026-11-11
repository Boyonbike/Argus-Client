package dev.argus.client.events;

import net.minecraft.world.inventory.Slot;

public final class AbstractContainerMenuMoveItemStackToEvent extends CancellableEvent {
   private final Slot slot;

   public AbstractContainerMenuMoveItemStackToEvent(Slot slot) {
      this.slot = slot;
   }

   public Slot getSlot() {
      return this.slot;
   }
}
