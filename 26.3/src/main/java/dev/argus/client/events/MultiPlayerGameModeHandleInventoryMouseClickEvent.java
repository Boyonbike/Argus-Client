package dev.argus.client.events;

import net.minecraft.world.inventory.ContainerInput;

public class MultiPlayerGameModeHandleInventoryMouseClickEvent extends CancellableEvent {
   private final int containerId;
   private final int slotId;
   private final int button;
   private final ContainerInput clickType;

   public int getContainerId() {
      return this.containerId;
   }

   public int getSlotId() {
      return this.slotId;
   }

   public int getButton() {
      return this.button;
   }

   public ContainerInput getClickType() {
      return this.clickType;
   }

   public MultiPlayerGameModeHandleInventoryMouseClickEvent(int containerId, int slotId, int button, ContainerInput clickType) {
      this.containerId = containerId;
      this.slotId = slotId;
      this.button = button;
      this.clickType = clickType;
   }

   public static final class After extends MultiPlayerGameModeHandleInventoryMouseClickEvent {
      public After(int containerId, int slotId, int button, ContainerInput clickType) {
         super(containerId, slotId, button, clickType);
      }
   }

   public static final class AfterStackHash extends MultiPlayerGameModeHandleInventoryMouseClickEvent {
      public AfterStackHash(int containerId, int slotId, int button, ContainerInput clickType) {
         super(containerId, slotId, button, clickType);
      }
   }

   public static final class BeforeContainerClick extends CancellableEvent {
   }
}
