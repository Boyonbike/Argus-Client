package dev.argus.client.gui.mixin;

import dev.argus.client.ClientContext;
import dev.argus.client.gui.HudSafeZones;
import dev.argus.client.modules.client.HUDModule;
import dev.argus.client.render.HudAnchorGroup;
import dev.argus.client.render.HudHorizontalAlignment;
import dev.argus.client.render.HudVerticalAlignment;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lays out stacked and edge-snapped HUD elements against the four safe-zone sides. */
@Mixin(value = HudAnchorGroup.class, remap = false)
public abstract class HudAnchorGroupMixin {
    @Inject(method = "calculateX", at = @At("HEAD"), cancellable = true, remap = false)
    private void argus$x(float width, HudHorizontalAlignment alignment, CallbackInfoReturnable<Float> callback) {
        float screen = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        switch (alignment) {
            case LEFT -> callback.setReturnValue((float) HudSafeZones.left());
            case RIGHT -> callback.setReturnValue(screen - width - HudSafeZones.right());
            default -> { }
        }
    }

    @Inject(method = "calculateY", at = @At("HEAD"), cancellable = true, remap = false)
    private void argus$y(float height, HudHorizontalAlignment horizontal, HudVerticalAlignment vertical, CallbackInfoReturnable<Float> callback) {
        if (vertical != HudVerticalAlignment.TOP) return;
        HUDModule hud = ClientContext.getModule(HUDModule.class);
        callback.setReturnValue(HudSafeZones.top() + (horizontal == HudHorizontalAlignment.RIGHT ? hud.getEffectIconOffset() : 0));
    }

    @Inject(method = "getBottomMargin", at = @At("HEAD"), cancellable = true, remap = false)
    private void argus$bottom(boolean rightAligned, CallbackInfoReturnable<Float> callback) {
        HUDModule hud = ClientContext.getModule(HUDModule.class);
        float lift = rightAligned ? hud.getBottomHudOffset() : hud.getChatInputOffset();
        int bottom = HudSafeZones.bottom();
        // The base client caps chat and search lifts at 16; a larger bottom zone is always honoured.
        callback.setReturnValue(Math.max(bottom, Math.min(bottom + lift, 16)));
    }
}
