package dev.argus.client.render;

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
      this.program = CompiledShaderProgram.fromResources(this.vertexResource, this.fragmentResource);
      this.uniforms = this.program.uniforms();
   }

   public BaseShaderPass(String vertexResource, String fragmentResource) {
      this.vertexResource = vertexResource;
      this.fragmentResource = fragmentResource;
   }

   @Override
   public void render(Runnable draw) {
      draw.run();
   }
}
