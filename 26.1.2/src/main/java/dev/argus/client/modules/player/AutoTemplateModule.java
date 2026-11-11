package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.inventory.InventoryUtils;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Repairable;

public final class AutoTemplateModule extends Module {
   private static final ItemStack DIAMOND_REPAIR_ITEM = new ItemStack(Items.DIAMOND);
   private static final Comparator<ItemStack> MATERIAL_ORDER = Comparator.comparing(stack -> stack.is(Items.NETHERITE_INGOT));
   private final Setting<Set<Item>> templatesSetting;
   private final Setting<Set<Item>> materialsSetting;
   private final Setting<AutoTemplateModule.ItemsChoice> itemsSetting;
   private final Setting<Integer> delaySetting;
   private final Setting<Boolean> safeSetting;
   private final ElapsedTimer actionTimer = new ElapsedTimer();

   public AutoTemplateModule() {
      super("AutoTemplate", "Automatically applies selected smithing templates.", ModuleCategory.PLAYER, "AutoUpgrade");
      this.templatesSetting = this.add(new RegistrySetSetting<>("Templates", BuiltInRegistries.ITEM, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
      this.materialsSetting = this.add(new RegistrySetSetting<>("Materials", BuiltInRegistries.ITEM));
      this.itemsSetting = this.add(new EnumSetting<>("Items", AutoTemplateModule.ItemsChoice.ANY));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 100, 0, 1000).withUnit("ms"));
      this.safeSetting = this.add(new BooleanSetting("Safe", true));
   }

   @EventHandler
   private void onPlayerTick(LocalPlayerPreTickEvent event) {
      if (!this.isWorldUnavailable() && ClientAccess.argusclientMinecraft.screen instanceof SmithingScreen) {
         AbstractContainerMenu menu = ClientAccess.argusclientMinecraft.player.containerMenu;
         if (this.actionTimer.hasElapsed(this.delaySetting.getValue().longValue())) {
            if (!menu.getSlot(3).getItem().isEmpty()) {
               this.quickMove(menu, 3);
            } else {
               Item template = this.findTemplate(menu);
               if (template != null) {
                  Predicate<ItemStack> templatePredicate = stack -> stack.is(template);
                  Predicate<ItemStack> basePredicate = this.getBasePredicate(template);
                  Predicate<ItemStack> materialPredicate = this.getMaterialPredicate(template);
                  int materialSlot = this.findBestMaterialSlot(menu, materialPredicate);
                  Predicate<ItemStack> preferredMaterial = materialPredicate.and(
                     stack -> materialSlot == -1 || MATERIAL_ORDER.compare(menu.getSlot(materialSlot).getItem(), stack) >= 0
                  );
                  if (!this.clearInvalidInput(menu, 0, templatePredicate)
                     && !this.clearInvalidInput(menu, 1, basePredicate)
                     && !this.clearInvalidInput(menu, 2, preferredMaterial)) {
                     if (!this.fillInput(menu, 0, this.findSourceSlot(menu, templatePredicate))) {
                        if (!this.fillInput(menu, 1, this.findSourceSlot(menu, basePredicate))) {
                           this.fillInput(menu, 2, materialSlot);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private Item findTemplate(AbstractContainerMenu menu) {
      int reserve = this.safeSetting.getValue() ? 1 : 0;

      for (Item template : this.templatesSetting.getValue()) {
         int count = 0;

         for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (stack.is(template)) {
               count += stack.getCount();
            }
         }

         if (count > reserve && this.hasInput(menu, 1, this.getBasePredicate(template)) && this.hasInput(menu, 2, this.getMaterialPredicate(template))) {
            return template;
         }
      }

      return null;
   }

   private Predicate<ItemStack> getBasePredicate(Item template) {
      return template == Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE
         ? this::isEligibleItem
         : stack -> stack.is(ItemTags.TRIMMABLE_ARMOR) && !stack.has(DataComponents.TRIM);
   }

   private Predicate<ItemStack> getMaterialPredicate(Item template) {
      return template == Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE
         ? stack -> stack.is(Items.NETHERITE_INGOT)
         : stack -> this.materialsSetting.getValue().contains(stack.getItem());
   }

   private boolean hasInput(AbstractContainerMenu menu, int slot, Predicate<ItemStack> predicate) {
      return predicate.test(menu.getSlot(slot).getItem()) || this.findSourceSlot(menu, predicate) != -1;
   }

   private boolean clearInvalidInput(AbstractContainerMenu menu, int slot, Predicate<ItemStack> predicate) {
      ItemStack stack = menu.getSlot(slot).getItem();
      if (!stack.isEmpty() && !predicate.test(stack)) {
         this.quickMove(menu, slot);
         return true;
      } else {
         return false;
      }
   }

   private boolean fillInput(AbstractContainerMenu menu, int target, int source) {
      if (source != -1 && menu.getSlot(target).getItem().isEmpty()) {
         InventoryUtils.swapSlotsWithPickup(source, target);
         this.actionTimer.reset();
         return true;
      } else {
         return false;
      }
   }

   private int findSourceSlot(AbstractContainerMenu menu, Predicate<ItemStack> predicate) {
      for (int slot = 4; slot < 40; slot++) {
         ItemStack stack = menu.getSlot(slot).getItem();
         if (!stack.isEmpty() && predicate.test(stack)) {
            return slot;
         }
      }

      return -1;
   }

   private int findBestMaterialSlot(AbstractContainerMenu menu, Predicate<ItemStack> predicate) {
      int best = -1;

      for (int slot = 4; slot < 40; slot++) {
         ItemStack stack = menu.getSlot(slot).getItem();
         if (!stack.isEmpty() && predicate.test(stack) && (best == -1 || MATERIAL_ORDER.compare(stack, menu.getSlot(best).getItem()) < 0)) {
            best = slot;
         }
      }

      return best;
   }

   private void quickMove(AbstractContainerMenu menu, int slot) {
      InventoryUtils.clickSlot(menu.containerId, slot, 0, ClickType.QUICK_MOVE);
      this.actionTimer.reset();
   }

   private boolean isEligibleItem(ItemStack stack) {
      if (stack.get(DataComponents.REPAIRABLE) instanceof Repairable repairable && repairable.isValidRepairItem(DIAMOND_REPAIR_ITEM)) {
         AutoTemplateModule.ItemsChoice choice = this.itemsSetting.getValue();
         return choice != AutoTemplateModule.ItemsChoice.TOOLS && stack.has(DataComponents.EQUIPPABLE)
            || choice != AutoTemplateModule.ItemsChoice.ARMOR && stack.has(DataComponents.TOOL);
      } else {
         return false;
      }
   }

   public enum ItemsChoice implements NamedEntry {
      ANY("Any"),
      TOOLS("Tools"),
      ARMOR("Armor");

      private final String displayName;

      ItemsChoice(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }
}
