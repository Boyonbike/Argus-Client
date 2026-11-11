package dev.argus.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.argus.client.render.effect.EffectUniforms;
import java.awt.Color;
import org.joml.Matrix4f;

/** Name-based setters over the backend-neutral std140 effect uniforms. */
public final class ShaderUniforms {
   private final EffectUniforms uniforms;

   public ShaderUniforms(EffectUniforms uniforms) {
      this.uniforms = uniforms;
   }

   public void set(String name, int textureUnit, RenderTarget target) {
      this.uniforms.setTexture(
         name,
         target.getColorTextureView(),
         RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
   }

   public void set(String name, com.mojang.renderpearl.api.textures.GpuTextureView texture,
                   com.mojang.renderpearl.api.textures.GpuSampler sampler) {
      this.uniforms.setTexture(name, texture, sampler);
   }

   public void set(String name, Matrix4f matrix) {
      this.uniforms.set(name, matrix);
   }

   public void set(String name, float value) {
      this.uniforms.set(name, value);
   }

   public void set(String name, float x, float y) {
      this.uniforms.set(name, x, y);
   }

   public void set(String name, int value) {
      this.uniforms.set(name, value);
   }

   public void set(String name, boolean enabled) {
      this.uniforms.set(name, enabled);
   }

   public void set(String name, Color color) {
      this.uniforms.set(name, color);
   }

   public void bind(RenderPass pass, CommandEncoder encoder) {
      this.uniforms.bind(pass, encoder);
   }

   public EffectUniforms effectUniforms() {
      return this.uniforms;
   }
}
