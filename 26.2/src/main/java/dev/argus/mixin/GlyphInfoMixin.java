package dev.argus.mixin;

import com.mojang.blaze3d.font.GlyphInfo;
import dev.argus.client.modules.render.NoRenderModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlyphInfo.class)
public interface GlyphInfoMixin {
   @Inject(method = "getShadowOffset", at = @At("HEAD"), cancellable = true)
   default void modifyTextShadowOffset(CallbackInfoReturnable<Float> callback) {
      callback.setReturnValue(NoRenderModule.textShadowOffset());
   }
}
