package dev.argus.client.render;

import dev.argus.client.render.effect.EffectPipeline;
import java.util.concurrent.atomic.AtomicInteger;

/** A compiled RenderPearl effect program, keeping the pass APIs stable. */
public final class CompiledShaderProgram {
   private static final AtomicInteger SEQUENCE = new AtomicInteger();
   private final EffectPipeline pipeline;
   private final ShaderUniforms uniforms;

   private CompiledShaderProgram(EffectPipeline pipeline) {
      this.pipeline = pipeline;
      this.uniforms = new ShaderUniforms(pipeline.uniforms());
   }

   public static CompiledShaderProgram fromResources(String vertexResource, String fragmentResource) {
      return new CompiledShaderProgram(EffectPipeline.fromResources(vertexResource, fragmentResource));
   }

   public static CompiledShaderProgram compile(String vertexSource, String fragmentSource) {
      return new CompiledShaderProgram(
         EffectPipeline.fromSources("inline_" + SEQUENCE.incrementAndGet(), vertexSource, fragmentSource));
   }

   public EffectPipeline pipeline() {
      return this.pipeline;
   }

   public ShaderUniforms uniforms() {
      return this.uniforms;
   }

   /** Retained for the reload path; 26.3 owns pipeline lifetime. */
   public int programId() {
      return 0;
   }

   public void delete() {
   }
}
