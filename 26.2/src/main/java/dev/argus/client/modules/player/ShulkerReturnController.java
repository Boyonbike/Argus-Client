package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.MultiPlayerGameModeHandleInventoryMouseClickEvent;
import dev.argus.client.managers.InventoryStateManager;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.SwingInteractionManager;
import dev.argus.client.managers.WorldStateManager;
import dev.argus.client.utils.entity.SwingAction;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.BlockInteractionUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ShulkerReturnController extends ChestStealerController {
   private final List<ShulkerReturnController.TrackedShulker> trackedShulkers = new ArrayList<>();
   private final Queue<ShulkerReturnController.TrackedShulker> pendingShulkers = new ArrayDeque<>();
   private boolean reopenRequested;
   private boolean handlingReturnClick;
   private boolean returnedThisTick;
   private boolean leftEnderChest;

   private boolean isPlayerInventorySlot(int slotIndex) {
      return slotIndex >= 0 && slotIndex <= 26;
   }

   public ShulkerReturnController(ChestStealerModule module) {
      super(module);
   }

   public void onContainerSlotUpdate(int containerId, ItemStack stack) {
      Screen screen = ClientAccess.argusclientMinecraft.gui.screen();
      if (!InventoryUtils.isEnderChestScreen(screen) && containerId == ClientAccess.argusclientMinecraft.player.inventoryMenu.containerId) {
         for (ShulkerReturnController.TrackedShulker trackedShulker : this.trackedShulkers) {
            if (trackedShulker.matchesItemAndName(stack)) {
               this.reopenRequested = true;
            }
         }
      }
   }

   public void onInventoryMouseClick(MultiPlayerGameModeHandleInventoryMouseClickEvent event) {
      Screen screen = ClientAccess.argusclientMinecraft.gui.screen();
      if (InventoryUtils.isEnderChestScreen(screen) && !this.handlingReturnClick) {
         AbstractContainerMenu menu = ClientAccess.argusclientMinecraft.player.containerMenu;
         int clickedSlotId = event.getSlotId();
         Slot slot = menu.getSlot(clickedSlotId);
         ShulkerReturnController.TrackedShulker trackedShulker = new ShulkerReturnController.TrackedShulker(
            getTrackedItem(slot), slot.getItem().get(DataComponents.CUSTOM_NAME), event.getSlotId()
         );
         int clickedSlot = event.getSlotId();
         if (this.isPlayerInventorySlot(clickedSlot)) {
            ItemStack clickedStack = slot.getItem();
            TagKey shulkerTag = ItemTags.SHULKER_BOXES;
            if (clickedStack.is(shulkerTag)) {
               if (event.getClickType() == ContainerInput.SWAP && event.getButton() < 9) {
                  this.pendingShulkers.add(trackedShulker);
               }

               if (event.getClickType() == ContainerInput.QUICK_MOVE) {
                  this.pendingShulkers.add(trackedShulker);
               }

               if (event.getClickType() == ContainerInput.PICKUP) {
                  this.pendingShulkers.poll();
               }

               return;
            }
         }
      }
   }

   private static Item getTrackedItem(Slot slot) {
      return slot.getItem().getItem();
   }

   private int findTrackedShulkerSlot(ShulkerReturnController.TrackedShulker trackedShulker) {
      for (int slotIndex = 27; slotIndex <= 62; slotIndex++) {
         ItemStack stack = ClientAccess.argusclientMinecraft.player.containerMenu.getSlot(slotIndex).getItem();
         if (trackedShulker.matchesItemAndName(stack)) {
            return slotIndex;
         }
      }

      return -1;
   }

   public boolean hasReturnedThisTick() {
      return this.returnedThisTick;
   }

   public void reopenRequestedShulker() {
      if (this.reopenRequested) {
         BlockPos position = this.findReopenPosition();
         if (position != null) {
            Direction fallbackDirection = Direction.UP;
            Direction direction = BlockInteractionUtils.getStrictDirectionsOrFallback(position, fallbackDirection).getFirst();
            double hitOffset = 0.5;
            BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(position).relative(direction, hitOffset), direction, position, false);
            ClientAccess.argusclientMinecraft.player.closeContainer();
            InteractionHand interactionHand = InteractionHand.MAIN_HAND;
            PacketSender.sendUseItemOn(interactionHand, hitResult);
            SwingInteractionManager swingManager = ClientContext.swingManager;
            InteractionHand swingHand = InteractionHand.MAIN_HAND;
            SwingAction swingType = SwingAction.INTERACT;
            swingManager.swing(swingHand, swingType);
            RotationManager rotationManager = ClientContext.rotationManager;
            float[] rotations = RotationManager.getRotationsToBlock(position);
            rotationManager.requestRotation(rotations, 250);
         }

         this.reopenRequested = false;
      }
   }

   private BlockPos findReopenPosition() {
      BlockPos lastInteracted = ChestStealerModule.getLastInteractedBlock(this.module);
      if (lastInteracted != null
         && this.isWithinReopenRange(lastInteracted)
         && ClientAccess.argusclientMinecraft.level.getBlockState(lastInteracted).is(Blocks.ENDER_CHEST)) {
         return lastInteracted;
      }

      BlockPos nearest = null;
      double nearestDistance = Double.MAX_VALUE;
      WorldStateManager containerTracker = ClientContext.worldStateManager;

      for (BlockEntity blockEntity : containerTracker.getBlockEntities()) {
         if (blockEntity instanceof EnderChestBlockEntity) {
            BlockPos candidate = blockEntity.getBlockPos();
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            Vec3 eyePosition = player.getEyePosition();
            Vec3 center = Vec3.atCenterOf(candidate);
            double distance = eyePosition.distanceTo(center);
            if (distance <= 5.0 && distance < nearestDistance) {
               nearestDistance = distance;
               nearest = candidate;
            }
         }
      }

      return nearest;
   }

   private boolean isWithinReopenRange(BlockPos position) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Vec3 eyePosition = player.getEyePosition();
      Vec3 center = Vec3.atCenterOf(position);
      return eyePosition.distanceTo(center) <= 5.0;
   }

   public void tick() {
      this.returnedThisTick = false;
      Screen screen = ClientAccess.argusclientMinecraft.gui.screen();
      if (!InventoryUtils.isEnderChestScreen(screen)) {
         this.leftEnderChest = true;

         while (!this.pendingShulkers.isEmpty()) {
            this.trackedShulkers.add(this.pendingShulkers.poll());
         }
      } else if (!this.trackedShulkers.isEmpty() && this.leftEnderChest) {
         if (!this.module.hasScreenOpenTimedOut()) {
            return;
         }

         if (ClientAccess.argusclientMinecraft.player.containerMenu instanceof ChestMenu menu && menu.getRowCount() == 3) {
            InventoryStateManager inventoryCoordinator = ClientContext.inventoryStateManager;
            if (!InventoryStateManager.wouldReachClickLimit(inventoryCoordinator, 1)) {
               this.handlingReturnClick = true;

               for (ShulkerReturnController.TrackedShulker selectedShulker : this.trackedShulkers) {
                  int matchingSlot = this.findTrackedShulkerSlot(selectedShulker);
                  if (matchingSlot != -1) {
                     if (matchingSlot >= 54 && matchingSlot <= 62) {
                        int originalSlot = selectedShulker.slotIndex();
                        int hotbarSlot = matchingSlot - 54;
                        InventoryUtils.swapWithHotbarSlot(originalSlot, hotbarSlot);
                     } else {
                        int originalSlot = selectedShulker.slotIndex();
                        InventoryUtils.swapSlotsWithPickup(matchingSlot, originalSlot);
                     }

                     this.returnedThisTick = true;
                     this.leftEnderChest = false;
                  }
               }

               this.handlingReturnClick = false;
               this.trackedShulkers.clear();
            }
         }
      }
   }

   private record TrackedShulker(Item item, Component customName, int slotIndex) {
      public boolean matchesItemAndName(ItemStack stack) {
         if (stack.getItem() != this.item) {
            return false;
         }

         Object stackName = stack.get(DataComponents.CUSTOM_NAME);
         return Objects.equals(stackName, this.customName);
      }
   }
}
