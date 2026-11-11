package dev.argus.mixin;

import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
import dev.argus.client.managers.RenderStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public class GlCommandEncoderMixin {
   @Inject(method = "drawFromBuffers", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_drawElements(IIIJ)V"))
   private void applyRenderStateBeforeIndexedDraw(
      @Coerce Object renderPass,
      int ignoredDrawInt1,
      int ignoredDrawInt2,
      int ignoredDrawInt3,
      IndexType indexType,
      GlRenderPipeline pipeline,
      int ignoredDrawInt4,
      CallbackInfo callback
   ) {
      RenderStateManager.applyRegistered(true);
   }

   @Inject(method = "drawFromBuffers", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_drawArrays(III)V"))
   private void applyRenderStateBeforeArrayDraw(
      @Coerce Object renderPass,
      int ignoredDrawInt1,
      int ignoredDrawInt2,
      int ignoredDrawInt3,
      IndexType indexType,
      GlRenderPipeline pipeline,
      int ignoredDrawInt4,
      CallbackInfo callback
   ) {
      RenderStateManager.applyRegistered(true);
   }

   @Inject(method = "drawFromBuffers", at = @At("TAIL"))
   private void restoreRenderStateAfterDraw(
      @Coerce Object renderPass,
      int ignoredDrawInt1,
      int ignoredDrawInt2,
      int ignoredDrawInt3,
      IndexType indexType,
      GlRenderPipeline pipeline,
      int ignoredDrawInt4,
      CallbackInfo callback
   ) {
      RenderStateManager.applyRegistered(false);
   }
}
