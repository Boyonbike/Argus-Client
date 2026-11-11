package dev.argus.client.commands;

import dev.argus.client.ClientAccess;
import dev.argus.client.utils.inventory.InventoryUtils;
import net.minecraft.world.item.Item;

public class AlternativeSwapStrategy implements ActionSwapStrategy {
   @Override
   public SwapSlots findSlots(Item[] items) {
      int hotbarSlot = InventoryUtils.findHotbarSlot(items);
      int sourceSlot = InventoryUtils.toMenuSlot(hotbarSlot);
      if (sourceSlot == -1) {
         sourceSlot = InventoryUtils.findInventorySlot(items);
      }

      return new SwapSlots(sourceSlot, ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot());
   }

   @Override
   public void apply(SwapSlots slots) {
      InventoryUtils.swapIntoSelectedSlot(slots.sourceSlot());
   }

   @Override
   public void undo(SwapSlots slots) {
      InventoryUtils.swapIntoSelectedSlot(slots.sourceSlot());
   }
}
