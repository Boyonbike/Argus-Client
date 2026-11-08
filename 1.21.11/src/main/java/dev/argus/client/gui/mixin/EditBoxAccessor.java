package dev.argus.client.gui.mixin;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only selection endpoint for the high-density GUI text-input renderer. */
@Mixin(EditBox.class)
public interface EditBoxAccessor {
    @Accessor("highlightPos") int argusclient$getHighlightPosition();
}
