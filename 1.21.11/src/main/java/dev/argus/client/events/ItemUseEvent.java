package dev.argus.client.events;

import net.minecraft.world.item.ItemStack;

public abstract sealed class ItemUseEvent extends CancellableEvent permits ItemUseEvent.BeforeFinish, ItemUseEvent.Slowdown {
   public static final class BeforeFinish extends ItemUseEvent {
      public ItemStack stack;

      public BeforeFinish(ItemStack stack) {
         this.stack = stack;
      }

      public ItemStack getStack() {
         return this.stack;
      }
   }

   public static final class Slowdown extends ItemUseEvent {
   }
}
