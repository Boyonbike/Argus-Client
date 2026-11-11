package dev.argus.mixin;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.SlotSetEvent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Slot.class)
public class SlotMixin {
   @Inject(method = "set", at = @At("TAIL"))
   private void postSlotSetEvent(ItemStack stack, CallbackInfo callback) {
      ClientAccess.eventBus.post(new SlotSetEvent(stack));
   }
}
