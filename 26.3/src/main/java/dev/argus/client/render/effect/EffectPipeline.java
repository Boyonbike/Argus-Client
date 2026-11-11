package dev.argus.client.render.effect;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.Identifier;

/**
 * A backend-neutral full-screen effect pipeline. The vertex shader receives a
 * four-vertex quad; the fragment shader reads the input target through its
 * declared sampler uniforms and the std140 EffectUniforms block.
 */
public final class EffectPipeline {
   private static int sequence;
   private static final Pattern SAMPLER = Pattern.compile("uniform\\s+sampler2D\\s+([A-Za-z_]\\w*)\\s*;");

   private final EffectShaderSource source;
   private final RenderPipeline pipeline;
   private final EffectUniforms uniforms;
   private CompiledRenderPipeline compiled;
   private GpuBuffer quad;

   private EffectPipeline(EffectShaderSource source, RenderPipeline pipeline, EffectUniforms uniforms) {
      this.source = source;
      this.pipeline = pipeline;
      this.uniforms = uniforms;
   }

   public static EffectPipeline fromResources(String vertexResource, String fragmentResource) {
      EffectShaderSource source = EffectShaderSource.fromResources(vertexResource, fragmentResource);
      return create(source, vertexResource, fragmentResource);
   }

   public static EffectPipeline fromSources(String name, String vertexSource, String fragmentSource) {
      EffectShaderSource source = EffectShaderSource.fromSources(name, vertexSource, fragmentSource);
      return create(source, name + ".vsh", name + ".fsh");
   }

   private static EffectPipeline create(EffectShaderSource source, String vertexResource, String fragmentResource) {
      BindGroupLayout.Builder layout = BindGroupLayout.builder()
         .withUniform(EffectUniforms.BLOCK_NAME, UniformType.UNIFORM_BUFFER);
      Matcher samplerMatcher = SAMPLER.matcher(source.fragmentSource());
      while (samplerMatcher.find()) {
         layout.withUniform(samplerMatcher.group(1), UniformType.COMBINED_IMAGE_SAMPLER);
      }
      Matcher vertexSamplerMatcher = SAMPLER.matcher(source.vertexSource());
      while (vertexSamplerMatcher.find()) {
         layout.withUniform(vertexSamplerMatcher.group(1), UniformType.COMBINED_IMAGE_SAMPLER);
      }

      RenderPipeline pipeline = RenderPipeline.builder()
         .withLocation(Identifier.fromNamespaceAndPath("argusclient", "pipeline/effect_" + (sequence++)))
         .withVertexShader(EffectShaderSource.shaderId(vertexResource))
         .withFragmentShader(EffectShaderSource.shaderId(fragmentResource))
         .withBindGroupLayout(layout.build())
         .withVertexBinding(0, DefaultVertexFormat.POSITION)
         .withPrimitiveTopology(PrimitiveTopology.QUADS)
         .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
         .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
         .build();
      return new EffectPipeline(source, pipeline, EffectUniforms.fromSource(source.fragmentSource()));
   }

   public EffectUniforms uniforms() {
      return this.uniforms;
   }

   public void draw(CommandEncoder encoder, EffectTarget output) {
      try (RenderPass pass = encoder.createRenderPass(
            () -> "argus-effect", output.colorView(), Optional.empty())) {
         this.draw(pass, encoder);
      }
   }

   public void draw(CommandEncoder encoder, com.mojang.blaze3d.pipeline.RenderTarget output) {
      try (RenderPass pass = encoder.createRenderPass(
            () -> "argus-effect", output.getColorTextureView(), Optional.empty())) {
         this.draw(pass, encoder);
      }
   }

   public void draw(RenderPass pass, CommandEncoder encoder) {
      pass.setPipeline(this.compiled());
      pass.setVertexBuffer(0, this.quad().slice());
      this.uniforms.bind(pass, encoder);
      pass.draw(4, 1, 0, 0);
   }

   private CompiledRenderPipeline compiled() {
      if (this.compiled == null) {
         this.compiled = RenderSystem.getDevice()
            .compilePipeline(this.pipeline, this.source, Runnable::run)
            .join()
            .finishCompile();
      }
      return this.compiled;
   }

   private GpuBuffer quad() {
      if (this.quad == null) {
         ByteBuffer vertices = ByteBuffer.allocateDirect(4 * 3 * Float.BYTES).order(ByteOrder.nativeOrder());
         vertices.putFloat(-1.0F).putFloat(-1.0F).putFloat(0.0F);
         vertices.putFloat(-1.0F).putFloat(1.0F).putFloat(0.0F);
         vertices.putFloat(1.0F).putFloat(1.0F).putFloat(0.0F);
         vertices.putFloat(1.0F).putFloat(-1.0F).putFloat(0.0F);
         vertices.flip();
         GpuDevice device = RenderSystem.getDevice();
         this.quad = device.createBuffer(() -> "argus-effect-quad", GpuBuffer.USAGE_VERTEX, vertices);
      }
      return this.quad;
   }
}
