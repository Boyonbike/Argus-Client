package dev.argus.client.events;

import net.minecraft.world.item.ItemStack;

public final class SlotSetEvent extends CancellableEvent {
   private final ItemStack itemStack;

   public SlotSetEvent(ItemStack itemStack) {
      this.itemStack = itemStack;
   }

   public ItemStack getItemStack() {
      return this.itemStack;
   }
}
