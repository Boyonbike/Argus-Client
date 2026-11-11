package dev.argus.client.gui.mixin;

import dev.argus.client.modules.player.AutoRekitModule;
import dev.argus.client.modules.player.ChestStealerModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevent two enabled modules from undoing each other's storage transfers. */
@Mixin(value = ChestStealerModule.class, remap = false)
public abstract class ChestStealerRekitMixin {
    @Inject(method = "onSendPosition", at = @At("HEAD"), cancellable = true, remap = false)
    private void argus$yieldToRekit(CallbackInfo ci) {
        AutoRekitModule module = AutoRekitModule.get();
        if (module != null && module.ownsStorage()) ci.cancel();
    }
}
