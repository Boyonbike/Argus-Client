package dev.argus.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBuffer.MappedView;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.NoRenderModule;
import dev.argus.client.modules.render.SkyboxModule;
import java.awt.Color;
import java.nio.ByteBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   private static final SkyboxModule skyboxModule = ClientContext.getModule(SkyboxModule.class);
   @Unique
   private SkyboxModule.FogDistances capturedFogDistances;

   @Inject(method = "updateBuffer(Lnet/minecraft/client/renderer/fog/FogData;)V", at = @At("HEAD"))
   private void captureFogDistances(FogData fogData, CallbackInfo callback) {
      this.capturedFogDistances = new SkyboxModule.FogDistances(
         fogData.environmentalStart, fogData.environmentalEnd, fogData.renderDistanceStart, fogData.renderDistanceEnd, fogData.skyEnd, fogData.cloudEnd);
   }

   // 26.1.2: setupFog only builds the FogData (extract phase); the GPU buffer is written later by updateBuffer(FogData).
   @Inject(method = "setupFog", at = @At("RETURN"))
   private void applyNoRenderFogSettings(
      Camera camera,
      int renderDistance,
      DeltaTracker deltaTracker,
      float ignoredSetupFloat,
      ClientLevel level,
      CallbackInfoReturnable<FogData> callback
   ) {
      FogData fogData = callback.getReturnValue();
      if (noRenderModule.isToggled() && noRenderModule.fogSetting.getValue()) {
         noRenderModule.applyFogSettings(fogData, renderDistance);
      }

      if (skyboxModule.isToggled() && skyboxModule.isCurrentDimensionEnabled()) {
         Color configuredColor = skyboxModule.fogSetting.getValue();
         float red = configuredColor.getRed() / 255.0F;
         float green = configuredColor.getGreen() / 255.0F;
         float blue = configuredColor.getBlue() / 255.0F;
         float alpha = configuredColor.getAlpha() / 255.0F;
         fogData.color.set(red, green, blue, alpha);
      }
   }

   @Inject(method = "updateBuffer(Lnet/minecraft/client/renderer/fog/FogData;)V", at = @At("TAIL"))
   private void updateSplitSkyFogBuffer(FogData fogData, CallbackInfo callback) {
      if (skyboxModule.isToggled() && skyboxModule.splitFogSetting.getValue() && this.capturedFogDistances != null) {
         Color backgroundColor = skyboxModule.backgroundSetting.getValue();
         Vector4f skyColor = new Vector4f(backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue(), backgroundColor.getAlpha())
            .div(255.0F);

         try (MappedView mappedBuffer = RenderSystem.getDevice().createCommandEncoder().mapBuffer(skyboxModule.getSkyFogBuffer().currentBuffer(), false, true)) {
            this.updateBuffer(
               mappedBuffer.data(),
               0,
               skyColor,
               this.capturedFogDistances.environmentalStart(),
               this.capturedFogDistances.environmentalEnd(),
               this.capturedFogDistances.renderDistanceStart(),
               this.capturedFogDistances.renderDistanceEnd(),
               this.capturedFogDistances.skyEnd(),
               this.capturedFogDistances.cloudsEnd()
            );
         }
      }
   }

   @Shadow
   protected abstract void updateBuffer(ByteBuffer var1, int var2, Vector4f var3, float var4, float var5, float var6, float var7, float var8, float var9);
}
