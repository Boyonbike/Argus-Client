package dev.argus.client.gui.mixin;

import dev.argus.client.gui.screens.HudMarginController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Argus HUD editor draws and drags the four safe-zone edges itself; the old two-axis controller is switched off. */
@Mixin(value = HudMarginController.class, remap = false)
public abstract class HudMarginControllerMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void argus$noRender(CallbackInfo callback) { callback.cancel(); }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void argus$noClick(CallbackInfo callback) { callback.cancel(); }
}
