package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.gui.screens.ShulkerPreviewScreen;
import dev.argus.client.managers.InventoryStateManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.ListFilterMode;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.inventory.InventoryUtils;
import java.util.Set;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class InventoryCleanerModule extends Module {
   private final Setting<Set<Item>> itemsSetting;
   private final Setting<ListFilterMode> selectionSetting;
   private final Setting<Integer> delaySetting;
   private final Setting<Integer> frequencySetting;
   private final Setting<Boolean> offhandSetting;
   private final Setting<Boolean> containersSetting;
   private final Setting<Boolean> onlyUISetting;
   private final Setting<Boolean> ignoreHotbarSetting;
   private final ElapsedTimer cleaningTimer;

   @EventHandler(priority = 100)
   private void discardSelectedStacks(LocalPlayerTickEvent event) {
      int discardAttempts = 0;
      ElapsedTimer capturedTimer = this.cleaningTimer;
      long delayMillis = this.delaySetting.getValue().intValue();
      if (capturedTimer.hasElapsed(delayMillis)) {
         Boolean cleanContainers = this.containersSetting.getValue();
         if (cleanContainers || ClientAccess.argusclientMinecraft.player.inventoryMenu == ClientAccess.argusclientMinecraft.player.containerMenu) {
            Screen screen = ClientAccess.argusclientMinecraft.screen;
            boolean supportedScreen = screen instanceof InventoryScreen
               || screen instanceof ContainerScreen
               || screen instanceof ShulkerBoxScreen && !(screen instanceof ShulkerPreviewScreen);
            if (!this.onlyUISetting.getValue() || supportedScreen) {
               boolean playerInventoryMenu = false;
               int slotCountForHotbar = ClientAccess.argusclientMinecraft.player.containerMenu.slots.size();
               if (ClientAccess.argusclientMinecraft.player.containerMenu instanceof InventoryMenu) {
                  slotCountForHotbar--;
                  playerInventoryMenu = true;
               }

               for (Slot slot : ClientAccess.argusclientMinecraft.player.containerMenu.slots) {
                  ItemStack stack = slot.getItem();
                  InventoryStateManager capturedClickManager = ClientContext.inventoryStateManager;
                  byte clickGateKind = 1;
                  if (InventoryStateManager.wouldReachClickLimit(capturedClickManager, clickGateKind)) {
                     break;
                  }

                  if (!stack.isEmpty()) {
                     ListFilterMode selectedPolicy = this.selectionSetting.getValue();
                     Item selectedItem = stack.getItem();
                     Setting<Set<Item>> itemsOption = this.itemsSetting;
                     if (ListFilterMode.matches(selectedPolicy, selectedItem, itemsOption)
                        && (slot.index < slotCountForHotbar - 9 || slot.index == 45 || !this.ignoreHotbarSetting.getValue())) {
                        if (slot.index == 45) {
                           Boolean cleanOffhand = this.offhandSetting.getValue();
                           if (!cleanOffhand) {
                              continue;
                           }
                        }

                        if (!playerInventoryMenu || (slot.index < 5 || slot.index > 8) && slot.index != 0) {
                           ClickType clickType = ClickType.THROW;
                           byte throwButton = 1;
                           int throwSlot = slot.index;
                           InventoryUtils.clickSlot(throwSlot, throwButton, clickType);
                           capturedTimer = this.cleaningTimer;
                           capturedTimer.reset();
                           discardAttempts++;
                           Integer frequency = this.frequencySetting.getValue();
                           if (discardAttempts >= frequency) {
                              break;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public InventoryCleanerModule() {
      super("InventoryCleaner", "Gets rid of the selected items in your inventory.", ModuleCategory.PLAYER);
      DefaultedRegistry itemRegistry = BuiltInRegistries.ITEM;
      Item[] defaultItems = new Item[0];
      this.itemsSetting = this.add(new RegistrySetSetting<>("Items", itemRegistry, defaultItems));
      this.selectionSetting = this.add(new EnumSetting<>("Selection", ListFilterMode.WHITELIST));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 100, 0, 1000).withUnit("ms"));
      this.frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 4));
      this.offhandSetting = this.add(new BooleanSetting("Offhand", false));
      this.containersSetting = this.add(new BooleanSetting("Containers", false));
      this.onlyUISetting = this.add(new BooleanSetting("OnlyUI", false));
      this.ignoreHotbarSetting = this.add(new BooleanSetting("IgnoreHotbar", true));
      this.cleaningTimer = new ElapsedTimer();
   }
}
