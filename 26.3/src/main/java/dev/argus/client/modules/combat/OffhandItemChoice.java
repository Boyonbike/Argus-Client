package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.exploit.XCarryModule;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.utils.inventory.InventoryUtils;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum OffhandItemChoice implements ClientAccess, NamedEntry {
   Totem("Totem", Items.TOTEM_OF_UNDYING),
   Crystal("Crystal", Items.END_CRYSTAL),
   Gapple("Gapple", Items.GOLDEN_APPLE) {
      @Override
      public Item getItem(boolean useCrapple) {
         return useCrapple ? super.getItem(true) : Items.ENCHANTED_GOLDEN_APPLE;
      }
   },
   Custom("Custom", Items.SHIELD) {
      @Override
      public Item getItem(boolean useCrapple) {
         return OffhandItemChoice.offhandModule.customSetting.getValue().stream().findAny().orElse(null);
      }
   };

   private static XCarryModule xCarryModule = ClientContext.getModule(XCarryModule.class);
   private static OffhandModule offhandModule = ClientContext.getModule(OffhandModule.class);
   private final String displayName;
   private final Item defaultItem;

   @Override
   public String getName() {
      return this.displayName;
   }

   OffhandItemChoice(String displayName, Item defaultItem) {
      this.displayName = displayName;
      this.defaultItem = defaultItem;
   }

   public Item getItem(boolean useCrapple) {
      return this.defaultItem;
   }

   public int findContainerSlot(boolean useCrapple) {
      Item itemToFind = this.getItem(useCrapple);
      if (itemToFind == null) {
         return -1;
      }

      if (ClientAccess.argusclientMinecraft.player.getOffhandItem().is(itemToFind)) {
         return -1;
      }

      if (ClientAccess.argusclientMinecraft.gui.screen() instanceof ShulkerBoxScreen || ClientAccess.argusclientMinecraft.gui.screen() instanceof ContainerScreen) {
         for (Slot slot : ClientAccess.argusclientMinecraft.player.containerMenu.slots) {
            ItemStack slotStack = slot.getItem();
            if (slotStack.is(itemToFind)) {
               return slot.index;
            }
         }
      }

      boolean includeXCarry = xCarryModule.isToggled();
      return InventoryUtils.findMenuSlot(itemToFind, includeXCarry);
   }

   public static void bindModuleRegistry(ModuleRegistry modules) {
      xCarryModule = (XCarryModule)modules.getModule(XCarryModule.class);
      offhandModule = (OffhandModule)modules.getModule(OffhandModule.class);
   }
}
