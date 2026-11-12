package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.argus.client.render.ArgusFeatureDispatcher;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Lets Argus build a second dispatcher that owns its own staged vertex buffer. */
@Mixin(FeatureRenderDispatcher.class)
public class FeatureRenderDispatcherMixin {
   @WrapOperation(
      method = "<init>",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/RenderBuffers;stagedVertexBuffer()Lnet/minecraft/client/renderer/StagedVertexBuffer;"
      )
   )
   private StagedVertexBuffer useArgusStagedBuffer(RenderBuffers renderBuffers, Operation<StagedVertexBuffer> original) {
      StagedVertexBuffer argusBuffer = ArgusFeatureDispatcher.constructingBuffer();
      return argusBuffer != null ? argusBuffer : original.call(renderBuffers);
   }
}
