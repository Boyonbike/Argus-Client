package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MultiPlayerGameModeStartDestroyBlockEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.utils.inventory.InventoryUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ItemSaverModule extends Module {
   public static boolean isSafeToUse(ItemStack stack) {
      return !stack.isEmpty() && stack.isDamageableItem() && !stack.has(DataComponents.EQUIPPABLE) && !stack.is(Items.ELYTRA)
         ? InventoryUtils.getDurabilityPercent(stack) > 5.0F
         : true;
   }

   @EventHandler
   private void onMultiPlayerGameModeStartDestroyBlockEvent(MultiPlayerGameModeStartDestroyBlockEvent event) {
      Inventory inventory = ClientAccess.argusclientMinecraft.player.getInventory();
      int selectedSlot = inventory.getSelectedSlot();
      if (!isSafeToUse(inventory.getItem(selectedSlot))) {
         InventoryUtils.selectHotbarSlotForPlacement(selectedSlot == 8 ? selectedSlot - 1 : selectedSlot + 1);
         event.cancel();
      }
   }

   public ItemSaverModule() {
      super("ItemSaver", "Prevents your tools from breaking.", ModuleCategory.PLAYER);
   }
}
