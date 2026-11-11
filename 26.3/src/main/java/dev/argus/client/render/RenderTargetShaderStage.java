package dev.argus.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import org.joml.Matrix4fStack;

public class RenderTargetShaderStage implements ShaderStage {
   protected final CompiledShaderProgram program;
   protected final ShaderUniforms uniforms;
   protected final RenderTarget outputTarget;

   @Override
   public CompiledShaderProgram getProgram() {
      return this.program;
   }

   @Override
   public void updateUniforms() {
      this.uniforms.set("u_Proj", RenderUtils.WORLD_PROJECTION_MATRIX);
      Matrix4fStack modelViewMatrix = RenderSystem.getModelViewStack();
      this.uniforms.set("u_ModelView", modelViewMatrix);
      this.uniforms.set("u_Size", this.outputTarget.width, this.outputTarget.height);
      this.uniforms.set("u_Time", (float)(System.nanoTime() / 1.0e9));
   }

   @Override
   public RenderTarget getOutputTarget() {
      return this.outputTarget;
   }

   public RenderTargetShaderStage(String vertexResource, String fragmentResource, RenderTarget outputTarget) {
      this(CompiledShaderProgram.fromResources(vertexResource, fragmentResource), outputTarget);
   }

   public RenderTargetShaderStage(CompiledShaderProgram program, RenderTarget outputTarget) {
      this.program = program;
      this.uniforms = program.uniforms();
      this.outputTarget = outputTarget;
   }

   @Override
   public void apply(RenderTarget inputTarget) {
      this.updateUniforms();
      this.uniforms.set("u_Texture", inputTarget.getColorTextureView(), outputSampler());
      CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
      this.program.pipeline().draw(encoder, this.outputTarget);
      encoder.submit();
   }

   private static com.mojang.renderpearl.api.textures.GpuSampler outputSampler() {
      return RenderSystem.getSamplerCache().getClampToEdge(com.mojang.renderpearl.api.textures.FilterMode.LINEAR);
   }
}
