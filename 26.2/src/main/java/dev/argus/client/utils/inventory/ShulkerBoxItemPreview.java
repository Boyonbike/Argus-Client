package dev.argus.client.utils.inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

public record ShulkerBoxItemPreview(ItemStack containerStack, ItemStack previewStack) {
   public static ShulkerBoxItemPreview create(ItemStack stack) {
      Item containerItem = stack.getItem();
      if (!ItemUtils.isShulkerBox(containerItem)) {
         return null;
      }

      DataComponentType<ItemContainerContents> containerType = DataComponents.CONTAINER;
      ItemContainerContents contents = stack.get(containerType);
      if (contents == null) {
         return null;
      }

      Map<Item, Integer> occupiedSlotCounts = new HashMap<>();

      for (ItemStack containedStack : contents.allItemsCopyStream().toList()) {
         if (!containedStack.isEmpty()) {
            Item containedItem = containedStack.getItem();
            byte zeroCount = 0;
            Integer previousSlotCount = occupiedSlotCounts.getOrDefault(containedItem, Integer.valueOf(zeroCount));
            int nextSlotCount = 1 + previousSlotCount;
            occupiedSlotCounts.put(containedItem, nextSlotCount);
         }
      }

      if (occupiedSlotCounts.isEmpty()) {
         return null;
      }

      Entry<Item, Integer> selectedEntry = occupiedSlotCounts.entrySet().stream().max(Entry.comparingByValue()).orElse(null);
      return selectedEntry == null ? null : new ShulkerBoxItemPreview(stack, selectedEntry.getKey().getDefaultInstance());
   }
}
