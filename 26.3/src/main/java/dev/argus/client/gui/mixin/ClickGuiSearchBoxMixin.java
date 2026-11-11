package dev.argus.client.gui.mixin;

import dev.argus.client.gui.screens.ClickGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The legacy click GUI only creates its search box when it opens, and the Argus GUI never lets it
 * open. Base components (module rows, category panels) still read that box, so give them an empty one
 * instead of a null that crashes every click.
 */
@Mixin(value = ClickGuiScreen.class, remap = false)
public abstract class ClickGuiSearchBoxMixin {
    @Shadow(remap = false) public EditBox searchBox;

    @Inject(method = "getSearchBox", at = @At("HEAD"), remap = false)
    private void argusclient$ensureSearchBox(CallbackInfoReturnable<EditBox> callback) {
        if (searchBox == null) searchBox = new EditBox(Minecraft.getInstance().font, 0, -100, 100, 9, Component.literal("Search"));
    }
}
