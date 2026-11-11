package dev.argus.client.commands;

public record SwapSlots(int sourceSlot, int targetSlot) {
   public static final SwapSlots INVALID = new SwapSlots(-1, -1);

   public boolean isInvalid() {
      return this.sourceSlot == -1 || this.targetSlot == -1;
   }
}
