package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.InventoryStateManager;
import dev.argus.client.managers.KitRegistry;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.inventory.Kit;
import java.util.ArrayList;
import java.util.Map.Entry;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

public class ShulkerRefillController extends ChestStealerController {
   private int savedSelectedSlot = -1;
   private boolean pendingClose;

   public ShulkerRefillController(ChestStealerModule module) {
      super(module);
   }

   private ShulkerRefillController.RefillSelection findBestShulker(ChestMenu menu, Kit profile) {
      int bestSlot = -1;
      float bestScore = 0.001F;

      for (int slot = 0; slot < 27; slot++) {
         ItemStack stack = menu.getSlot(slot).getItem();
         if (stack.is(ItemTags.SHULKER_BOXES)) {
            float score = this.scoreContents(stack, profile);
            if (score > bestScore) {
               bestScore = score;
               bestSlot = slot;
            }
         }
      }

      return new ShulkerRefillController.RefillSelection(bestSlot, bestScore);
   }

   private float scoreContents(ItemStack shulkerStack, Kit profile) {
      ItemContainerContents contents = shulkerStack.get(DataComponents.CONTAINER);
      if (contents == null) {
         return 0.0F;
      }

      ArrayList<ItemStack> contentStacks = new ArrayList<>();

      for (ItemStack contentStack : contents.nonEmptyItemCopyStream().toList()) {
         contentStacks.add(contentStack);
      }

      float score = 0.0F;

      for (Entry profileEntry : profile.getItemsBySlot().entrySet()) {
         Kit.ItemDescriptor desiredStack = (Kit.ItemDescriptor)profileEntry.getValue();
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         Inventory inventory = player.getInventory();
         int inventorySlot = (Integer)profileEntry.getKey();
         ItemStack inventoryStack = inventory.getItem(inventorySlot);
         float missingFraction = 1.0F - (float)inventoryStack.getCount() / this.getStackCapacity(inventoryStack);
         if ((!desiredStack.matches(inventoryStack) || missingFraction != 0.0F) && !inventoryStack.is(ItemTags.SWORDS)) {
            float bestFraction = 0.0F;

            for (ItemStack candidateStack : contentStacks) {
               float fraction = (float)candidateStack.getCount() / this.getStackCapacity(candidateStack);
               if (desiredStack.matches(candidateStack) && fraction > bestFraction) {
                  bestFraction = fraction;
               }
            }

            if (bestFraction > 0.0F) {
               score += missingFraction * bestFraction;
            }
         }
      }

      return score;
   }

   private int getStackCapacity(ItemStack stack) {
      return stack.isEmpty() ? 64 : stack.getMaxStackSize();
   }

   private int findSwordOrEmptySlot(ChestMenu menu) {
      for (int slot = 54; slot <= 62; slot++) {
         if (menu.getSlot(slot).getItem().is(ItemTags.SWORDS)) {
            return slot;
         }
      }

      for (int slot = 54; slot <= 62; slot++) {
         if (menu.getSlot(slot).getItem().isEmpty()) {
            return slot;
         }
      }

      return 54;
   }

   private void restoreSelectedSlot() {
      if (this.savedSelectedSlot != -1) {
         int selectedSlot = this.savedSelectedSlot;
         InventoryUtils.selectHotbarSlot(selectedSlot);
         this.savedSelectedSlot = -1;
      }
   }

   public void tick() {
      if (this.pendingClose) {
         ClientAccess.argusclientMinecraft.player.closeContainer();
         this.pendingClose = false;
      } else if (!InventoryUtils.isEnderChestScreen(ClientAccess.argusclientMinecraft.screen)) {
         this.restoreSelectedSlot();
      } else if (this.module.hasScreenOpenTimedOut()
         && ClientAccess.argusclientMinecraft.player.containerMenu instanceof ChestMenu menu
         && menu.getRowCount() == 3) {
         if (!InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 1)) {
            Kit profile = KitRegistry.getCurrentKit(ClientContext.kitRegistry);
            if (profile != null) {
               ShulkerRefillController.RefillSelection selection = this.findBestShulker(menu, profile);
               if (selection.slotIndex() != -1) {
                  for (int slot = 54; slot <= 62; slot++) {
                     ItemStack stack = menu.getSlot(slot).getItem();
                     if (stack.is(ItemTags.SHULKER_BOXES) && this.scoreContents(stack, profile) >= selection.score()) {
                        return;
                     }
                  }

                  int hotbarSlot = this.findSwordOrEmptySlot(menu) - 54;
                  InventoryUtils.swapWithHotbarSlot(selection.slotIndex(), hotbarSlot);
                  this.module.openController.requestOpen();
                  this.savedSelectedSlot = hotbarSlot;
                  if (this.module.shouldCloseAutomatically()) {
                     this.pendingClose = true;
                  }
               }
            }
         }
      }
   }

   record RefillSelection(int slotIndex, float score) {
   }
}
