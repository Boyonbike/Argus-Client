package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.combat.AutoCrystalModule;
import dev.argus.client.modules.exploit.MultiTaskModule;
import dev.argus.client.modules.exploit.XCarryModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.ListFilterMode;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.movement.MovementPhysics;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ReplenishModule extends Module {
   private static MultiTaskModule multiTaskModule = ClientContext.getModule(MultiTaskModule.class);
   private static XCarryModule xCarryModule = ClientContext.getModule(XCarryModule.class);
   private static AutoCrystalModule autoCrystalModule = ClientContext.getModule(AutoCrystalModule.class);
   private final Setting<Set<Item>> itemsSetting = this.add(
      new RegistrySetSetting<>(
         "Items", BuiltInRegistries.ITEM, Items.TOTEM_OF_UNDYING, Items.END_CRYSTAL, Items.EXPERIENCE_BOTTLE, Items.ENCHANTED_GOLDEN_APPLE
      )
   );
   private final Setting<ListFilterMode> selectionSetting = this.add(new EnumSetting<>("Selection", ListFilterMode.ANY));
   private final Setting<Integer> delaySetting = this.add(new NumberSetting<>("Delay", 50, 0, 500).withUnit("ms"));
   private final Setting<Integer> thresholdSetting = this.add(new NumberSetting<>("Threshold", 32, 16, 63));
   private final Setting<Boolean> sortSetting = this.add(new BooleanSetting("Sort", false));
   private final Setting<Boolean> unstackableSetting = this.add(new BooleanSetting("Unstackable", false).markVisibilityDependency());
   private final Setting<Boolean> renamedSetting = this.add(new BooleanSetting("Renamed", true).dependsOn(this.unstackableSetting));
   final Setting<Boolean> limitSetting = this.add(new BooleanSetting("Limit", true).markAsGroup());
   private final Setting<Boolean> motionSetting;
   private final Setting<Boolean> fallingSetting;
   private final List<ReplenishModule.RememberedStack> rememberedHotbar;
   private final ElapsedTimer replenishTimer;

   private boolean replenishStack(int hotbarSlot) {
      Inventory inventory = ClientAccess.argusclientMinecraft.player.getInventory();
      ItemStack target = inventory.getItem(hotbarSlot);
      if (ListFilterMode.matches(this.selectionSetting.getValue(), target.getItem(), this.itemsSetting.getValue()) && !target.isEmpty() && target.isStackable()
         )
       {
         float threshold = this.thresholdSetting.getValue().floatValue() / 64.0F;
         if ((float)target.getCount() / target.getMaxStackSize() >= threshold) {
            return false;
         }

         int sourceSlot = -1;
         ItemStack source = ItemStack.EMPTY;

         for (int slot = 9; slot < 36; slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (!candidate.isEmpty() && ItemStack.isSameItemSameComponents(candidate, target) && candidate.getCount() > source.getCount()) {
               sourceSlot = slot;
               source = candidate;
            }
         }

         if (xCarryModule.isToggled() && !(ClientAccess.argusclientMinecraft.screen instanceof InventoryScreen)) {
            CraftingContainer crafting = ClientAccess.argusclientMinecraft.player.inventoryMenu.getCraftSlots();

            for (int slot = 0; slot < 4; slot++) {
               ItemStack candidate = crafting.getItem(slot);
               if (!candidate.isEmpty() && ItemStack.isSameItemSameComponents(candidate, target) && candidate.getCount() > source.getCount()) {
                  sourceSlot = slot + 1;
                  source = candidate;
               }
            }
         }

         if (sourceSlot == -1) {
            return false;
         }

         int count = source.getCount();
         boolean useSwap = count > source.getMaxStackSize() * threshold * 1.5 || count == source.getMaxStackSize();
         ClientContext.inventoryStateManager.setClickBatching(true);

         try {
            if (this.sortSetting.getValue() && count != 1 && target.getMaxStackSize() - target.getCount() > count) {
               InventoryUtils.clickSlot(sourceSlot, 0, ContainerInput.PICKUP);
               InventoryUtils.clickSlot(sourceSlot, 1, ContainerInput.PICKUP);
               InventoryUtils.clickSlot(InventoryUtils.toMenuSlot(hotbarSlot), 0, ContainerInput.PICKUP);
            } else if (InventoryUtils.isHotbarFull()) {
               InventoryUtils.clickSlot(sourceSlot, 0, ContainerInput.QUICK_MOVE);
            } else if (useSwap) {
               InventoryUtils.clickSlot(sourceSlot, hotbarSlot, ContainerInput.SWAP);
            } else {
               InventoryUtils.swapSlotsWithPickup(sourceSlot, InventoryUtils.toMenuSlot(hotbarSlot));
            }
         } finally {
            ClientContext.inventoryStateManager.setClickBatching(false);
         }

         return true;
      } else {
         return false;
      }
   }

   private boolean areOtherScannedSlotsOccupied(int excludedSlot) {
      Inventory inventory = ClientAccess.argusclientMinecraft.player.getInventory();

      for (int slot = 0; slot < 9; slot++) {
         if (slot != excludedSlot && inventory.getItem(slot).isEmpty()) {
            return false;
         }
      }

      return true;
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (!this.isWorldUnavailable() && this.replenishTimer.hasElapsed(this.delaySetting.getValue().longValue())) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         if (player.containerMenu.getCarried().isEmpty()) {
            if (!MovementPhysics.isLocalPlayerMoving() || !this.motionSetting.getValue()) {
               if (!(player.getDeltaMovement().y() > 0.0) || !this.fallingSetting.getValue()) {
                  if (!multiTaskModule.isItemUseRestricted()) {
                     boolean containerOpen = ClientAccess.argusclientMinecraft.screen instanceof AbstractContainerScreen;

                     for (int slot = 0; slot < 9; slot++) {
                        ItemStack stack = player.getInventory().getItem(slot);
                        ReplenishModule.RememberedStack remembered = this.rememberedHotbar.get(slot);
                        boolean restore = this.unstackableSetting.getValue()
                           && (remembered.item().getDefaultMaxStackSize() == 1 || this.renamedSetting.getValue() && remembered.customNamePresent());
                        if (restore && stack.isEmpty() && remembered.item() != Items.AIR && !containerOpen && this.restoreMissingItem(remembered.item(), slot)) {
                           this.replenishTimer.reset();
                           return;
                        }

                        this.rememberedHotbar.set(slot, ReplenishModule.RememberedStack.from(stack));
                     }

                     if (!containerOpen || ClientAccess.argusclientMinecraft.screen instanceof InventoryScreen) {
                        for (int slot = 0; slot < 9; slot++) {
                           if (this.replenishStack(slot)) {
                              this.replenishTimer.reset();
                              return;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean restoreMissingItem(Item item, int hotbarSlot) {
      ListFilterMode selectionMode = this.selectionSetting.getValue();
      Collection selectedItemValues = this.itemsSetting.getValue();
      if (!ListFilterMode.matches(selectionMode, item, selectedItemValues)) {
         return false;
      }

      boolean forceSuicide = autoCrystalModule.isToggled() && autoCrystalModule.forceSuicideSetting.getValue();
      Predicate<ItemStack> restorePredicate = candidateStack -> {
         LocalPlayer offhandPlayer = ClientAccess.argusclientMinecraft.player;
         return candidateStack != offhandPlayer.getOffhandItem() && (!forceSuicide || candidateStack.getItem() != Items.TOTEM_OF_UNDYING);
      };
      int sourceSlot = InventoryUtils.findMenuSlot(item, restorePredicate, true);
      if (sourceSlot == -1 && item == Items.TOTEM_OF_UNDYING && !forceSuicide) {
         Predicate<ItemStack> fallbackPredicate = candidateStack -> {
            Item fallbackItem = candidateStack.getItem();
            if (fallbackItem != Items.ENCHANTED_GOLDEN_APPLE && fallbackItem != Items.GOLDEN_APPLE) {
               return false;
            }

            LocalPlayer offhandPlayer = ClientAccess.argusclientMinecraft.player;
            return candidateStack != offhandPlayer.getOffhandItem();
         };
         sourceSlot = InventoryUtils.findInventorySlot(fallbackPredicate, true);
      }

      if (sourceSlot < 36 && sourceSlot != -1) {
         if (this.areOtherScannedSlotsOccupied(hotbarSlot)) {
            MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
            int containerId = ClientAccess.argusclientMinecraft.player.containerMenu.containerId;
            int clickSourceSlot = sourceSlot;
            byte mouseButton = 0;
            ContainerInput quickMove = ContainerInput.QUICK_MOVE;
            LocalPlayer clickPlayer = ClientAccess.argusclientMinecraft.player;
            gameMode.handleInventoryMouseClick(containerId, clickSourceSlot, mouseButton, quickMove, clickPlayer);
         } else {
            int conversionHotbarSlot = hotbarSlot;
            int targetSlot = InventoryUtils.toMenuSlot(conversionHotbarSlot);
            InventoryUtils.swapSlotsWithPickup(sourceSlot, targetSlot);
         }

         return true;
      } else {
         return false;
      }
   }

   public static void bindModules(ModuleRegistry registry) {
      multiTaskModule = (MultiTaskModule)registry.getModule(MultiTaskModule.class);
      xCarryModule = (XCarryModule)registry.getModule(XCarryModule.class);
      autoCrystalModule = (AutoCrystalModule)registry.getModule(AutoCrystalModule.class);
   }

   public ReplenishModule() {
      super("Replenish", "Replenishes your hotbar.", ModuleCategory.PLAYER);
      Predicate<Object> motionVisibility = ignored -> this.limitSetting.isExpanded();
      this.motionSetting = this.add(new BooleanSetting("Motion", false, motionVisibility));
      Predicate<Object> fallingVisibility = ignored -> this.limitSetting.isExpanded();
      this.fallingSetting = this.add(new BooleanSetting("Falling", false, fallingVisibility));
      ReplenishModule.RememberedStack emptyEntry = ReplenishModule.RememberedStack.EMPTY;
      this.rememberedHotbar = NonNullList.withSize(9, emptyEntry);
      this.replenishTimer = new ElapsedTimer();
      this.setDrawn(false);
   }

   private record RememberedStack(Item item, boolean customNamePresent) {
      public static final ReplenishModule.RememberedStack EMPTY = new ReplenishModule.RememberedStack(Items.AIR, false);

      public static ReplenishModule.RememberedStack from(ItemStack stack) {
         return new ReplenishModule.RememberedStack(stack.getItem(), stack.has(DataComponents.CUSTOM_NAME));
      }
   }
}
