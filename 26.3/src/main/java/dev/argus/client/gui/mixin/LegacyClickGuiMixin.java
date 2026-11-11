package dev.argus.client.gui.mixin;

import dev.argus.client.gui.GuiNavigation;
import dev.argus.client.gui.screens.ClickGuiScreen;
import dev.argus.client.gui.screens.ClientScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The base client opens its legacy click GUI directly from the title, multiplayer and
 * disconnected screens' key handlers. Redirect those opens to the Argus GUI.
 */
@Mixin(value = ClientScreen.class, remap = false)
public abstract class LegacyClickGuiMixin {
    @Inject(method = "open", at = @At("HEAD"), cancellable = true, remap = false)
    private void argusclient$openArgusGui(CallbackInfo callback) {
        if ((Object) this instanceof ClickGuiScreen) {
            GuiNavigation.openAnywhere();
            callback.cancel();
        }
    }
}
