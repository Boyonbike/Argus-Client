package dev.argus.client.settings.options;

import dev.argus.client.ClientAccess;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.utils.inventory.InventoryUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;

public enum ScaffoldSwapChoice implements ClientAccess, NamedEntry {
   NONE("None"),
   NORMAL("Normal") {
      @Override
      public void runWithSlot(int slot, Runnable action) {
         InventoryUtils.selectHotbarSlotForPlacement(slot);
         action.run();
      }
   },
   SILENT("Silent") {
      @Override
      public void runWithSlot(int slot, Runnable action) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         Inventory inventory = player.getInventory();
         int originalSlot = inventory.getSelectedSlot();
         InventoryUtils.selectHotbarSlotForPlacement(slot);
         action.run();
         InventoryUtils.selectHotbarSlotForPlacement(originalSlot);
      }
   };

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   ScaffoldSwapChoice(String displayName) {
      this.displayName = displayName;
   }

   public void runWithSlot(int slot, Runnable action) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Inventory inventory = player.getInventory();
      if (inventory.getSelectedSlot() == slot) {
         action.run();
      }
   }
}
