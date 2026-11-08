package dev.randomstuff.client.gui.mixin;

import dev.randomstuff.client.gui.GuiNavigation;
import dev.randomstuff.client.modules.client.UIModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The target is our own stable client API, so Minecraft name remapping is disabled. */
@Mixin(value=UIModule.class,remap=false)
public abstract class UIModuleMixin {
    @Inject(method="onEnable",at=@At("HEAD"),cancellable=true,remap=false)
    private void randomstuffclient$open(CallbackInfo callback){GuiNavigation.openFromBind();callback.cancel();}
    @Inject(method="onDisable",at=@At("HEAD"),remap=false)
    private void randomstuffclient$close(CallbackInfo callback){GuiNavigation.closeFromBind();}
    @Inject(method="onLocalPlayerTick",at=@At("HEAD"),cancellable=true,remap=false)
    private void randomstuffclient$keepOpen(CallbackInfo callback){if(GuiNavigation.isOpen())callback.cancel();}
}
