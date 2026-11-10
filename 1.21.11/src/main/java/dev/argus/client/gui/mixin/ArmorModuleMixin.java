package dev.argus.client.gui.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.events.HudRenderEvent;
import dev.argus.client.gui.ArmorWidgetRenderer;
import dev.argus.client.modules.HudWidgetModule;
import dev.argus.client.modules.hud.ArmorModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the armour HUD movable: it gets a real element on the HUD canvas, and its drawing (which
 * the base client pins above the hotbar) is shifted to wherever that element is.
 */
@Mixin(value = ArmorModule.class, remap = false)
public abstract class ArmorModuleMixin extends HudWidgetModule {
    @Unique private boolean argus$shifted;
    @Unique private PoseStack argus$poses;

    private ArmorModuleMixin() { super(""); }

    @Shadow(remap = false) private int getArmorBottomOffset() { throw new AssertionError(); }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void argus$movable(CallbackInfo callback) { this.renderer = new ArmorWidgetRenderer(this); }

    @Inject(method = "onHudRender", at = @At("HEAD"), remap = false)
    private void argus$shift(HudRenderEvent event, CallbackInfo callback) {
        argus$shifted = false;
        if (Minecraft.getInstance().player == null || !(this.renderer instanceof ArmorWidgetRenderer element)) return;
        float homeX = event.getGuiGraphics().guiWidth() / 2f + 15, homeY = event.getGuiGraphics().guiHeight() - getArmorBottomOffset();
        element.placeIfNew(homeX, homeY - ArmorWidgetRenderer.TEXT);
        argus$poses = event.getPoseStack();
        argus$poses.pushPose();
        argus$poses.translate(element.getX() + element.contentShiftX() - homeX, element.getY() + element.contentShiftY() + ArmorWidgetRenderer.TEXT - homeY, 0);
        argus$shifted = true;
    }

    @Inject(method = "onHudRender", at = @At("RETURN"), remap = false)
    private void argus$unshift(HudRenderEvent event, CallbackInfo callback) {
        if (argus$shifted) { argus$poses.popPose(); argus$shifted = false; argus$poses = null; }
    }
}
