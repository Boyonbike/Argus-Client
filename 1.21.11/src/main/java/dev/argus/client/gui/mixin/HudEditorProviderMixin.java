package dev.argus.client.gui.mixin;

import dev.argus.client.ClientContext;
import dev.argus.client.gui.ArgusHudEditorScreen;
import dev.argus.client.gui.screens.HudEditorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The HUD editor is a lazily created singleton that every HUD widget registers with. Creating the
 * Argus editor in its place means widgets, config loading and the in-game HUD all share one instance.
 */
@Mixin(value = ClientContext.class, remap = false)
public abstract class HudEditorProviderMixin {
    @Shadow(remap = false) private static HudEditorScreen hudEditor;

    @Inject(method = "getHudEditor", at = @At("HEAD"), remap = false)
    private static void argusclient$createArgusEditor(CallbackInfoReturnable<HudEditorScreen> callback) {
        if (hudEditor == null) hudEditor = new ArgusHudEditorScreen();
    }
}
