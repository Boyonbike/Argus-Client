package dev.argus.client.render;

/** Retained stage state hook; RenderPearl pipelines own program and texture state. */
public class ShaderStageRenderState implements RenderState {
   public static final ShaderStageRenderState INSTANCE = new ShaderStageRenderState();
   private ShaderStage stage;
   private int inputTextureId = -1;

   private ShaderStageRenderState() {
   }

   public void configure(ShaderStage stage, int inputTextureId) {
      this.stage = stage;
      this.inputTextureId = inputTextureId;
   }

   @Override
   public void apply(boolean entering) {
   }
}
