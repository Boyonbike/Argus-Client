package dev.argus.mixin;

import dev.argus.client.managers.RenderStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder")
public class GlCommandEncoderMixin {
   @Inject(method = "drawFromBuffers", at = @At("HEAD"))
   private void applyRenderStateBeforeDraw(CallbackInfo callback) {
      RenderStateManager.applyRegistered(true);
   }

   @Inject(method = "drawFromBuffers", at = @At("TAIL"))
   private void restoreRenderStateAfterDraw(CallbackInfo callback) {
      RenderStateManager.applyRegistered(false);
   }
}
