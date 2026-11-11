package dev.argus.client.events;

public class SelectedSlotChangeEvent extends CancellableEvent {
   private final int slot;

   public int getSlot() {
      return this.slot;
   }

   public SelectedSlotChangeEvent(int slot) {
      this.slot = slot;
   }

   public static final class ClientPacketListenerHandleSetHeldSlotEvent extends CancellableEvent {
      private final int slot;

      public int getSlot() {
         return this.slot;
      }

      public ClientPacketListenerHandleSetHeldSlotEvent(int slot) {
         this.slot = slot;
      }
   }
}
