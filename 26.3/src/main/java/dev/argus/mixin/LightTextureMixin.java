package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.AmbienceModule;
import dev.argus.client.render.ClientRenderPipelines;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Swaps the lightmap pipeline for Ambience's Screen / Occlusion shaders. Those
 * shaders read the 26.3 LightmapInfo block followed by an extra vec4 tint, so
 * for them the uniform is re-uploaded as an extended copy built from the same
 * render state (vanilla values are never overwritten).
 */
@Mixin(Lightmap.class)
public class LightTextureMixin {
   private static final AmbienceModule ambienceModule = ClientContext.getModule(AmbienceModule.class);
   /** 6 floats (padded to 32 bytes) + 4 vec3 (16 each) + 1 vec4. */
   @Unique
   private static final int ARGUS_LIGHTMAP_INFO_SIZE = 32 + 4 * 16 + 16;
   @Unique
   private final ByteBuffer argusclient$lightmapInfo = ByteBuffer.allocateDirect(ARGUS_LIGHTMAP_INFO_SIZE).order(ByteOrder.nativeOrder());

   @ModifyExpressionValue(
      method = "render",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderPipelines;LIGHTMAP:Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;")
   )
   private RenderPipeline overrideLightmapPipeline(RenderPipeline originalPipeline) {
      Color tint = argusclient$tint();
      if (tint == null) {
         return originalPipeline;
      }
      return ambienceModule.brightnessSetting.getValue() == AmbienceModule.BrightnessChoice.SCREEN
         ? ClientRenderPipelines.AMBIENCE
         : ClientRenderPipelines.OCCLUSION;
   }

   @WrapOperation(
      method = "render",
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/renderpearl/api/commands/RenderPass;setUniform(Ljava/lang/String;Lcom/mojang/renderpearl/api/buffers/GpuBuffer;)V"
      )
   )
   private void uploadTintedLightmapInfo(
      RenderPass pass,
      String name,
      GpuBuffer vanillaBuffer,
      Operation<Void> original,
      @Local(argsOnly = true) LightmapRenderState renderState,
      @Local CommandEncoder encoder
   ) {
      Color tint = "LightmapInfo".equals(name) ? argusclient$tint() : null;
      if (tint == null) {
         original.call(pass, name, vanillaBuffer);
         return;
      }

      ByteBuffer data = this.argusclient$lightmapInfo.clear();
      Std140Builder.intoBuffer(data)
         .putFloat(renderState.skyFactor)
         .putFloat(renderState.blockFactor)
         .putFloat(renderState.nightVisionEffectIntensity)
         .putFloat(renderState.darknessEffectScale)
         .putFloat(renderState.bossOverlayWorldDarkening)
         .putFloat(renderState.brightness)
         .putVec3(renderState.blockLightTint)
         .putVec3(renderState.skyLightColor)
         .putVec3(renderState.ambientColor)
         .putVec3(renderState.nightVisionColor)
         .putVec4(tint.getRed() / 255.0F, tint.getGreen() / 255.0F, tint.getBlue() / 255.0F, 1.0F);
      data.position(0).limit(ARGUS_LIGHTMAP_INFO_SIZE);
      GpuBufferSlice slice = encoder.transientMemory().uploadStaging(data, 256L, GpuBuffer.USAGE_UNIFORM);
      pass.setUniform(name, slice);
   }

   /** The Ambience tint when an Argus lightmap pipeline should be used, otherwise null. */
   @Unique
   private static Color argusclient$tint() {
      if (!ambienceModule.isToggled()) {
         return null;
      }
      if (ambienceModule.brightnessSetting.getValue() == AmbienceModule.BrightnessChoice.SCREEN) {
         return ambienceModule.colorSetting.getValue();
      }
      if (ambienceModule.occlusionSetting.getValue() && ambienceModule.occlusionSetting.isVisible()) {
         return ambienceModule.occlusionColorSetting.getValue();
      }
      return null;
   }
}
