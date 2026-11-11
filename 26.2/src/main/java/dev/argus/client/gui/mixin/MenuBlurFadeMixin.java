package dev.argus.client.gui.mixin;

import dev.argus.client.gui.UiScreen;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fades the world blur behind Argus pages with their backdrop, so closing the menu doesn't leave the blur behind. */
@Mixin(Options.class)
public abstract class MenuBlurFadeMixin {
    @Inject(method = "getMenuBackgroundBlurriness", at = @At("RETURN"), cancellable = true)
    private void argusclient$fadeWithMenu(CallbackInfoReturnable<Integer> callback) {
        float scale = UiScreen.blurScale();
        if (scale < 1) callback.setReturnValue(Math.round(callback.getReturnValue() * scale));
    }
}
