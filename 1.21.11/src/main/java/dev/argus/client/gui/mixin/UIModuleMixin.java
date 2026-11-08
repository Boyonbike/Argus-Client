package dev.argus.client.gui.mixin;

import dev.argus.client.gui.GuiNavigation;
import dev.argus.client.modules.client.UIModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The target is our own stable client API, so Minecraft name remapping is disabled. */
@Mixin(value=UIModule.class,remap=false)
public abstract class UIModuleMixin {
    @Inject(method="onEnable",at=@At("HEAD"),cancellable=true,remap=false)
    private void argusclient$open(CallbackInfo callback){GuiNavigation.openFromBind();callback.cancel();}
    @Inject(method="onDisable",at=@At("HEAD"),remap=false)
    private void argusclient$close(CallbackInfo callback){GuiNavigation.closeFromBind();}
    @Inject(method="onLocalPlayerTick",at=@At("HEAD"),cancellable=true,remap=false)
    private void argusclient$keepOpen(CallbackInfo callback){if(GuiNavigation.isOpen())callback.cancel();}
}
