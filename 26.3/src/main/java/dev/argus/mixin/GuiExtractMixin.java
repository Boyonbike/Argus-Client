package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.argus.client.gui.GuiCapture;
import dev.argus.client.gui.screens.ClientScreen;
import dev.argus.client.modules.player.NameProtectModule;
import dev.argus.client.render.GuiProjectionUtils;
import dev.argus.client.render.RenderUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiExtractMixin {
   @Inject(
      method = "extractRenderState",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
         shift = Shift.BEFORE
      )
   )
   private void beforeScreenRendering(
      DeltaTracker deltaTracker, boolean shouldRenderLevel, boolean resourcesLoaded, CallbackInfo callback, @Local GuiGraphicsExtractor graphics
   ) {
      NameProtectModule.replacementBypassed = true;
      if (dev.argus.client.ClientAccess.argusclientMinecraft.gui.screen() instanceof ClientScreen clientScreen) {
         if (clientScreen.hasMenuParent()) {
            clientScreen.getParentScreen().extractRenderStateWithTooltipAndSubtitles(graphics, -99, -99, RenderUtils.getPartialTick());
         }

         GuiProjectionUtils.beginGuiProjection();
      }
   }

   @Inject(
      method = "extractRenderState",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
         shift = Shift.AFTER
      )
   )
   private void afterScreenRendering(DeltaTracker deltaTracker, boolean shouldRenderLevel, boolean resourcesLoaded, CallbackInfo callback) {
      NameProtectModule.replacementBypassed = false;
      // Unconditional: the screen may have closed while drawing, and restore is a no-op when nothing began.
      GuiProjectionUtils.restoreProjection();
   }

   @ModifyExpressionValue(
      method = "extractRenderState",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Gui;overlay:Lnet/minecraft/client/gui/screens/Overlay;", ordinal = 0)
   )
   private Overlay captureHudGraphics(Overlay overlay, @Local GuiGraphicsExtractor graphics) {
      GuiCapture.hudGraphics = graphics;
      return overlay;
   }
}
