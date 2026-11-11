package dev.argus.mixin;

import dev.argus.client.ClientContext;
import dev.argus.client.modules.movement.EntityControlModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com/viaversion/viaversion/protocols/v1_21to1_21_2/storage/ClientVehicleStorage", remap = false)
public class ClientVehicleStorageMixin {
   private static final EntityControlModule entityControl = ClientContext.getModule(EntityControlModule.class);

   @Inject(method = {"sidewaysMovement", "forwardMovement"}, at = @At("HEAD"), cancellable = true, remap = false)
   private void applyGrimOverflow(CallbackInfoReturnable<Float> callback) {
      if (entityControl.isGrimOverflowActive()) {
         callback.setReturnValue(Float.NaN);
      }
   }
}
