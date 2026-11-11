package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.RenderTypeDrawEvent;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.ChamsModule;
import dev.argus.client.modules.render.GlintModule;
import dev.argus.client.render.ClientRenderPipelines;
import dev.argus.client.render.GlintTint;
import dev.argus.client.render.RenderUtils;
import java.util.List;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderType.class)
public class RenderTypeMixin {
   @Unique
   private GpuTextureView glintTextureOverride;
   @Unique
   private boolean glintPipeline;
   @Shadow
   @org.spongepowered.asm.mixin.Final
   public RenderSetup state;

   @Inject(method = "<init>", at = @At("TAIL"))
   private void captureGlintPipeline(String name, RenderSetup setup, CallbackInfo callback) {
      this.glintPipeline = ClientRenderPipelines.isGlint(setup.pipeline);
   }


   @WrapOperation(
      method = "writeDynamicTransforms",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"
      )
   )
   private GpuBufferSlice tintGlint(DynamicGpuData uniforms, Matrix4f modelView, Matrix4f textureMatrix, Operation<GpuBufferSlice> original) {
      if (!ClientRenderPipelines.isGlint(this.state.pipeline)) {
         return original.call(uniforms, modelView, textureMatrix);
      }
      GlintModule glint = ClientContext.getModule(GlintModule.class);
      ChamsModule chams = ClientContext.getModule(ChamsModule.class);
      Vector4f colour = GlintTint.colour(
         glint != null && glint.isToggled(),
         glint == null ? null : glint.getGlintColor(),
         ChamsModule.renderingGlintBatch,
         chams == null ? null : chams.getGlintColor(),
         RenderUtils.getRenderColor()[3]
      );
      if (colour.x == 1.0F && colour.y == 1.0F && colour.z == 1.0F && colour.w == 1.0F) {
         return original.call(uniforms, modelView, textureMatrix);
      }
      return uniforms.writeTransform(modelView, colour, new Vector3f(), textureMatrix);
   }

   @Inject(method = "prepare", at = @At("HEAD"))
   private void beforePrepare(CallbackInfoReturnable<PreparedRenderType> callback) {
      if (this.glintPipeline) {
         RenderTypeDrawEvent event = new RenderTypeDrawEvent(EventStage.Pre);
         ClientAccess.eventBus.post(event);
         this.glintTextureOverride = event.getOverride();
      }
   }

   @ModifyReturnValue(method = "prepare", at = @At("RETURN"))
   private PreparedRenderType overrideGlintTexture(PreparedRenderType prepared) {
      if (!this.glintPipeline || this.glintTextureOverride == null || prepared == null) {
         return prepared;
      }
      List<PreparedRenderType.Texture> textures = prepared.textures().stream()
         .map(texture -> "Sampler0".equals(texture.name())
            ? new PreparedRenderType.Texture(texture.name(), this.glintTextureOverride, texture.sampler())
            : texture)
         .toList();
      return new PreparedRenderType(
         prepared.name(), prepared.pipeline(), prepared.oitPipelineSet(), prepared.dynamicTransforms(),
         prepared.scissorState(), textures
      );
   }

   @Inject(method = "prepare", at = @At("RETURN"))
   private void afterPrepare(CallbackInfoReturnable<PreparedRenderType> callback) {
      if (this.glintPipeline) {
         ClientAccess.eventBus.post(new RenderTypeDrawEvent(EventStage.Post));
      }
   }
}
