package dev.argus.client.commands;

import dev.argus.client.utils.inventory.InventoryUtils;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.Equippable;

public class ArmorSwapStrategy implements ActionSwapStrategy {
   @Override
   public SwapSlots findSlots(Item[] items) {
      int targetSlot = 6;

      for (Item item : items) {
         if (!ActionCommand.canEquip(item)) {
            return SwapSlots.INVALID;
         }

         DataComponentGetter components = item.components();
         DataComponentType<?> componentType = DataComponents.EQUIPPABLE;
         if (components.get(componentType) instanceof Equippable equippable) {
            EquipmentSlot equipmentSlot = equippable.slot();
            targetSlot = 8 - equipmentSlot.getIndex();
         }
      }

      int sourceSlot = InventoryUtils.findInventorySlot(items);
      return new SwapSlots(sourceSlot, targetSlot);
   }

   @Override
   public void apply(SwapSlots slots) {
      InventoryUtils.swapSlotsWithPickup(slots.sourceSlot(), slots.targetSlot());
   }

   @Override
   public void undo(SwapSlots slots) {
      InventoryUtils.swapSlotsWithPickup(slots.targetSlot(), slots.sourceSlot());
   }
}
