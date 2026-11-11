package dev.argus.client.gui.mixin;

import dev.argus.client.gui.screens.HudEditorScreen;
import dev.argus.client.settings.options.HudAnchor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reaches the base HUD editor's private state so the Argus editor can reuse its drag engine. */
@Mixin(value = HudEditorScreen.class, remap = false)
public interface HudEditorScreenAccessor {
    /** The base editor centres its legacy module panel on init; the Argus editor has none. */
    @Accessor(value = "centerPanelPending", remap = false) void argus$setCenterPanelPending(boolean pending);
    @Accessor(value = "draggingWidget", remap = false) boolean argus$isDraggingWidget();
    @Invoker(value = "swapAnchorContents", remap = false) void argus$swapAnchorContents(HudAnchor first, HudAnchor second);
}
