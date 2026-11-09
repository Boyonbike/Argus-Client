package dev.argus.client.render;

import org.lwjgl.opengl.GL20;

public class BaseShaderPass implements ShaderPass {
   protected final String vertexResource;
   protected final String fragmentResource;
   protected CompiledShaderProgram program;
   protected ShaderUniforms uniforms;

   @Override
   public CompiledShaderProgram getProgram() {
      return this.program;
   }

   @Override
   public void updateUniforms() {
   }

   protected void initializeProgram() {
      String vertexSource = RenderTargetUtils.readShaderSource(this.vertexResource);
      String fragmentSource = RenderTargetUtils.readShaderSource(this.fragmentResource);
      this.program = CompiledShaderProgram.compile(vertexSource, fragmentSource);
      this.uniforms = new ShaderUniforms(this.program.programId());
   }

   public BaseShaderPass(String vertexResource, String fragmentResource) {
      this.vertexResource = vertexResource;
      this.fragmentResource = fragmentResource;
   }

   @Override
   public void render(Runnable draw) {
      CompiledShaderProgram capturedProgram = this.program;
      int programId = capturedProgram.programId();
      GL20.glUseProgram(programId);
      draw.run();
   }
}
