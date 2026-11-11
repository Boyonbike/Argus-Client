package dev.argus.client.commands;

import dev.argus.client.ClientAccess;
import dev.argus.client.utils.inventory.InventoryUtils;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;

public class OffhandSwapStrategy implements ActionSwapStrategy {
   @Override
   public SwapSlots findSlots(Item[] items) {
      return new SwapSlots(InventoryUtils.toMenuSlot(InventoryUtils.findInventorySlot(items)), 40);
   }

   @Override
   public void apply(SwapSlots slots) {
      MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
      int containerId = ClientAccess.argusclientMinecraft.player.containerMenu.containerId;
      gameMode.handleContainerInput(containerId, slots.sourceSlot(), 40, ContainerInput.SWAP, ClientAccess.argusclientMinecraft.player);
   }

   @Override
   public void undo(SwapSlots slots) {
      MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
      int containerId = ClientAccess.argusclientMinecraft.player.containerMenu.containerId;
      gameMode.handleContainerInput(containerId, slots.sourceSlot(), 40, ContainerInput.SWAP, ClientAccess.argusclientMinecraft.player);
   }
}
