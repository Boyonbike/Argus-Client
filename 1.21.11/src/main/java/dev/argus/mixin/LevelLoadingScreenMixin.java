package dev.argus.mixin;

import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelLoadingScreen.class)
public class LevelLoadingScreenMixin {
   @Inject(method = "shouldCloseOnEsc", at = @At("HEAD"), cancellable = true)
   private void argusclient$allowClosingWithEscape(CallbackInfoReturnable<Boolean> callback) {
      callback.setReturnValue(true);
   }
}
