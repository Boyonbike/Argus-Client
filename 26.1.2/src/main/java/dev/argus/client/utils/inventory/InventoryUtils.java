package dev.argus.client.utils.inventory;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.player.ItemSaverModule;
import dev.argus.mixin.MultiPlayerGameModeAccessor;
import java.util.function.Predicate;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.state.BlockState;

public final class InventoryUtils implements ClientAccess {
   private static final ItemSaverModule itemSaver = ClientContext.getModule(ItemSaverModule.class);
   private static final Component enderChestScreenTitle = Component.translatable("container.enderchest");

   private InventoryUtils() {
   }

   public static void selectHotbarSlot(int slot) {
      if (slot >= 0 && slot < 9) {
         ClientAccess.argusclientMinecraft.player.getInventory().setSelectedSlot(slot);
         ((MultiPlayerGameModeAccessor)ClientAccess.argusclientMinecraft.gameMode).sync();
      }
   }

   public static void swapIntoSelectedSlot(int slot) {
      if (slot >= 0) {
         swapWithHotbarSlot(toMenuSlot(slot), getSelectedSlot());
      }
   }

   public static void swapWithHotbarSlot(int menuSlot, int hotbarSlot) {
      if (menuSlot >= 0 && hotbarSlot >= 0) {
         ContainerInput clickType = ContainerInput.SWAP;
         clickSlot(menuSlot, hotbarSlot, clickType);
      }
   }

   public static void swapMenuSlots(int firstSlot, int secondSlot) {
      if (firstSlot >= 0 && secondSlot >= 0) {
         if (firstSlot >= 36 && firstSlot <= 44) {
            clickSlot(secondSlot, firstSlot - 36, ContainerInput.SWAP);
         } else {
            clickSlot(firstSlot, 0, ContainerInput.SWAP);
            clickSlot(secondSlot, 0, ContainerInput.SWAP);
            clickSlot(firstSlot, 0, ContainerInput.SWAP);
         }
      }
   }

   public static void swapSlotsWithPickup(int firstSlot, int secondSlot) {
      if (firstSlot != -1 && secondSlot != -1) {
         clickSlot(firstSlot, 0, ContainerInput.PICKUP);
         clickSlot(secondSlot, 0, ContainerInput.PICKUP);
         ItemStack carriedStack = ClientAccess.argusclientMinecraft.player.containerMenu.getCarried();
         if (!carriedStack.isEmpty()) {
            clickSlot(firstSlot, 0, ContainerInput.PICKUP);
         }
      }
   }

   public static void clickSlot(int slot, int button, ContainerInput clickType) {
      int containerId = ClientAccess.argusclientMinecraft.player.containerMenu.containerId;
      ClientAccess.argusclientMinecraft.gameMode.handleContainerInput(containerId, slot, button, clickType, ClientAccess.argusclientMinecraft.player);
   }

   public static void clickSlot(int containerId, int slot, int button, ContainerInput clickType) {
      MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      gameMode.handleContainerInput(containerId, slot, button, clickType, player);
   }

   public static int getSelectedSlot() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      return player.getInventory().getSelectedSlot();
   }

   public static int findMenuSlot(Item item, boolean includeCrafting) {
      Predicate<ItemStack> predicate = stack -> true;
      return findMenuSlot(item, predicate, includeCrafting);
   }

   public static int findMenuSlot(Item item) {
      return findMenuSlot(item, false);
   }

   public static int findInventorySlot(Item... items) {
      return findInventorySlot(stack -> {
         Item item = stack.getItem();

         for (Item candidate : items) {
            if (candidate.equals(item)) {
               return true;
            }
         }

         return false;
      }, false);
   }

   public static int findMenuSlot(Item item, Predicate<ItemStack> predicate, boolean includeCrafting) {
      if (!ClientContext.inventoryStateManager.hasPendingLocalEntityEvent()) {
         ItemStack offhand = ClientAccess.argusclientMinecraft.player.getOffhandItem();
         if (offhand.is(item) && predicate.test(offhand)) {
            return -1;
         }
      }

      for (int slot = 35; slot >= 0; slot--) {
         ItemStack stack = ClientAccess.argusclientMinecraft.player.getInventory().getItem(slot);
         if (stack.is(item) && predicate.test(stack)) {
            return toMenuSlot(slot);
         }
      }

      if (includeCrafting) {
         InventoryMenu menu = ClientAccess.argusclientMinecraft.player.inventoryMenu;

         for (int slot = 0; slot < 4; slot++) {
            ItemStack stack = menu.getCraftSlots().getItems().get(slot);
            if (stack.is(item) && predicate.test(stack)) {
               return slot + 1;
            }
         }
      }

      return -1;
   }

   public static int findInventorySlot(Predicate<ItemStack> predicate, boolean excludeHotbar) {
      for (int slot = 35; slot >= (excludeHotbar ? 9 : 0); slot--) {
         ItemStack stack = ClientAccess.argusclientMinecraft.player.getInventory().getItem(slot);
         if (predicate.test(stack)) {
            return slot;
         }
      }

      return -1;
   }

   public static int findHotbarSlotInItemOrder(Item... items) {
      for (Item item : items) {
         for (int slot = 0; slot < 9; slot++) {
            if (ClientAccess.argusclientMinecraft.player.getInventory().getItem(slot).is(item)) {
               return slot;
            }
         }
      }

      return -1;
   }

   public static int findHotbarSlot(Item... items) {
      return findHotbarSlot(stack -> {
         for (Item item : items) {
            if (stack.is(item)) {
               return true;
            }
         }

         return false;
      });
   }

   public static int findHotbarSlot(Item item) {
      return findHotbarSlot(stack -> stack.is(item));
   }

   public static int findHotbarSlot(Predicate<ItemStack> predicate) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (predicate.test(player.getMainHandItem())) {
         return player.getInventory().getSelectedSlot();
      }

      for (int slot = 0; slot < 9; slot++) {
         if (predicate.test(player.getInventory().getItem(slot))) {
            return slot;
         }
      }

      return -1;
   }

   public static int countItems(Predicate<ItemStack> predicate) {
      int count = 0;
      Inventory inventory = ClientAccess.argusclientMinecraft.player.getInventory();

      for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (predicate.test(stack)) {
            count += stack.getCount();
         }
      }

      for (ItemStack stack : ClientAccess.argusclientMinecraft.player.inventoryMenu.getCraftSlots().getItems()) {
         if (predicate.test(stack)) {
            count += stack.getCount();
         }
      }

      return count;
   }

   public static InteractionHand getHandForItem(Item item) {
      if (ClientAccess.argusclientMinecraft.player.getOffhandItem().getItem() == item) {
         return InteractionHand.OFF_HAND;
      } else {
         return ClientAccess.argusclientMinecraft.player.getMainHandItem().getItem() == item ? InteractionHand.MAIN_HAND : null;
      }
   }

   public static float getLowestArmorDurabilityPercent(Player player) {
      float lowest = 100.0F;

      for (ItemStack stack : EquipmentUtils.getArmorStacks(player)) {
         lowest = Math.min(lowest, getDurabilityPercent(stack));
      }

      return lowest;
   }

   public static float getDurabilityPercent(ItemStack stack) {
      if (!stack.isDamageableItem()) {
         return 100.0F;
      }

      float remaining = ((float)stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
      return 100 - (int)((1.0F - remaining) * 100.0F);
   }

   public static int toMenuSlot(int slot) {
      return slot >= 0 && slot < 9 ? slot + 36 : slot;
   }

   public static boolean isInventoryFull() {
      for (int slot = 0; slot < 36; slot++) {
         if (ClientAccess.argusclientMinecraft.player.getInventory().getItem(slot).isEmpty()) {
            return false;
         }
      }

      return true;
   }

   public static boolean isHotbarFull() {
      for (int slot = 0; slot < 9; slot++) {
         if (ClientAccess.argusclientMinecraft.player.getInventory().getItem(slot).isEmpty()) {
            return false;
         }
      }

      return true;
   }

   public static void selectHotbarSlotForPlacement(int slot) {
      selectHotbarSlot(slot);
   }

   public static boolean canEquip(EquipmentSlot slot, ItemStack stack) {
      if (slot == null) {
         return false;
      }

      DataComponentType<?> equippableType = DataComponents.EQUIPPABLE;
      return stack.get(equippableType) instanceof Equippable equippable
         ? equippable.slot() == slot
         : stack.getItem() == Items.ELYTRA && slot == EquipmentSlot.CHEST;
   }

   public static int findBestToolSlot(BlockPos position, boolean includeInventory) {
      BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(position);
      int selected = getSelectedSlot();
      int bestSlot = -1;
      double bestSpeed = -1.0;

      for (int slot = 0; slot < (includeInventory ? 36 : 9); slot++) {
         ItemStack stack = ClientAccess.argusclientMinecraft.player.getInventory().getItem(slot);
         if ((!itemSaver.isToggled() || ItemSaverModule.isSafeToUse(stack))
            && (!ClientAccess.argusclientMinecraft.player.isCreative() || !stack.is(ItemTags.SWORDS))) {
            double speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed || speed == bestSpeed && slot == selected) {
               bestSpeed = speed;
               bestSlot = slot;
            }
         }
      }

      return bestSlot;
   }

   public static ItemStack getCarriedStack() {
      return ClientAccess.argusclientMinecraft.player.containerMenu.getCarried();
   }

   public static InventoryUtils.SwapSlot findSwapSlot(Item item, boolean includeInventory) {
      Predicate<ItemStack> predicate = stack -> stack.is(item);
      return findSwapSlot(predicate, includeInventory);
   }

   public static InventoryUtils.SwapSlot findSwapSlot(Predicate<ItemStack> predicate, boolean includeInventory) {
      if (predicate.test(ClientAccess.argusclientMinecraft.player.getOffhandItem())) {
         return new InventoryUtils.SwapSlot(-2, false, InteractionHand.OFF_HAND);
      }

      int foundSlot = findHotbarSlot(predicate);
      return foundSlot == -1 && includeInventory
         ? new InventoryUtils.SwapSlot(findInventorySlot(predicate, true), false, InteractionHand.MAIN_HAND)
         : new InventoryUtils.SwapSlot(foundSlot, true, InteractionHand.MAIN_HAND);
   }

   public static void swapTo(InventoryUtils.SwapSlot slot) {
      if (slot.isFound() && !slot.isInHand()) {
         if (slot.hotbar()) {
            selectHotbarSlot(slot.slot());
         } else {
            swapIntoSelectedSlot(slot.slot());
         }
      }
   }

   public static void swapBack(int previousSlot, InventoryUtils.SwapSlot slot) {
      if (slot.isFound() && slot.hand() != InteractionHand.OFF_HAND) {
         if (slot.hotbar()) {
            selectHotbarSlot(previousSlot);
         } else {
            swapIntoSelectedSlot(slot.slot());
         }
      }
   }

   public static void runWithSwap(int previousSlot, InventoryUtils.SwapSlot slot, Runnable action) {
      if (slot.isFound()) {
         swapTo(slot);

         try {
            action.run();
         } finally {
            swapBack(previousSlot, slot);
         }
      }
   }

   public static boolean isEnderChestScreen(Screen screen) {
      return screen != null && screen.getTitle().equals(enderChestScreenTitle);
   }

   public record SwapSlot(int slot, boolean hotbar, InteractionHand hand) {
      public boolean isFound() {
         return this.slot != -1;
      }

      public ItemStack getStack() {
         if (this.hand == InteractionHand.OFF_HAND) {
            return ClientAccess.argusclientMinecraft.player.getOffhandItem();
         }

         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         Inventory inventory = player.getInventory();
         int inventorySlot = this.slot;
         return inventory.getItem(inventorySlot);
      }

      public boolean isInHand() {
         return this.hand == InteractionHand.OFF_HAND || this.slot == InventoryUtils.getSelectedSlot() && this.hotbar;
      }
   }
}
