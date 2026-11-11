package dev.argus.client.render.effect;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;

/** An off-screen colour+depth target for an effect pass. */
public final class EffectTarget {
   private final TextureTarget target;

   public EffectTarget(String name) {
      this.target = new TextureTarget(
         name, 1, 1, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
   }

   public void resize(int width, int height) {
      this.target.resize(Math.max(1, width), Math.max(1, height));
   }

   public int width() {
      return this.target.width;
   }

   public int height() {
      return this.target.height;
   }

   public GpuTextureView colorView() {
      return this.target.getColorTextureView();
   }

   public GpuTextureView depthView() {
      return this.target.getDepthTextureView();
   }

   public GpuSampler sampler() {
      return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
   }

   public RenderTarget target() {
      return this.target;
   }

   public void close() {
      this.target.destroyBuffers();
   }
}
