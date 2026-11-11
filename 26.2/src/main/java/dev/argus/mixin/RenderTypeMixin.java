package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.RenderTypeDrawEvent;
import dev.argus.client.render.ClientRenderPipelines;
import java.util.List;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
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

   @Inject(method = "<init>", at = @At("TAIL"))
   private void captureGlintPipeline(String name, RenderSetup setup, CallbackInfo callback) {
      this.glintPipeline = ClientRenderPipelines.isGlint(setup.pipeline);
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
         prepared.pipeline(), prepared.outputTarget(), prepared.dynamicTransforms(), prepared.scissorState(), textures
      );
   }

   @Inject(method = "prepare", at = @At("RETURN"))
   private void afterPrepare(CallbackInfoReturnable<PreparedRenderType> callback) {
      if (this.glintPipeline) {
         ClientAccess.eventBus.post(new RenderTypeDrawEvent(EventStage.Post));
      }
   }
}
