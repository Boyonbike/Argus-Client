package dev.argus.client.render;

/** Retained pass state hook; RenderPearl pipelines own program and texture state. */
public class ShaderPassRenderState implements RenderState {
   public static final ShaderPassRenderState INSTANCE = new ShaderPassRenderState();
   private ShaderPass pass;
   private int inputTextureId = -1;

   private ShaderPassRenderState() {
   }

   public void configure(ShaderPass pass) {
      this.configure(pass, -1);
   }

   public void configure(ShaderPass pass, int inputTextureId) {
      this.pass = pass;
      this.inputTextureId = inputTextureId;
   }

   @Override
   public void apply(boolean entering) {
   }
}
