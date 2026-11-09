package dev.argus.client.gui.mixin;

import dev.argus.client.gui.GuiNavigation;
import dev.argus.client.gui.screens.ClickGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Safety net: the legacy click GUI can never become the active screen. */
@Mixin(Minecraft.class)
public abstract class MinecraftSetScreenMixin {
    @Shadow public Screen screen;

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen argusclient$replaceLegacyGui(Screen next) {
        return next instanceof ClickGuiScreen ? GuiNavigation.replacementFor(screen) : next;
    }
}
