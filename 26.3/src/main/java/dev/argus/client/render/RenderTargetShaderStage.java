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
      // Registry targets start at 1x1 and are only sized to the window on activation; without this the
      // stage renders into a single pixel that the final blit stretches over the screen.
      dev.argus.client.managers.RenderTargetRegistry.activate(this.outputTarget);
      this.updateUniforms();
      this.uniforms.set("u_Texture", inputTarget.getColorTextureView(), outputSampler());
      CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
      // Overwrite the output rather than blend into last frame's contents (26.1.2 cleared it and disabled GL_BLEND).
      encoder.clearColorTexture(this.outputTarget.getColorTexture(), new org.joml.Vector4f(0.0F, 0.0F, 0.0F, 0.0F));
      this.program.pipeline().opaque().draw(encoder, this.outputTarget);
      // No submit(): it is the per-frame boundary (Minecraft calls it once); mid-frame it recycles transient uniform memory.
   }

   private static com.mojang.renderpearl.api.textures.GpuSampler outputSampler() {
      return RenderSystem.getSamplerCache().getClampToEdge(com.mojang.renderpearl.api.textures.FilterMode.LINEAR);
   }
}
