package dev.argus.client.utils.inventory;

import dev.argus.client.ClientAccess;
import dev.argus.client.settings.options.InventorySorterScopeChoice;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

public interface InventorySortStrategy {
   static ItemStack getInventoryStack(int inventorySlot) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      return player.getInventory().getItem(inventorySlot);
   }

   Integer[] getSourceSlots(InventorySorterScopeChoice var1);
}
