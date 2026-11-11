package dev.argus.client.render;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;

public class ShaderStageRenderState implements RenderState {
   public static final ShaderStageRenderState INSTANCE = new ShaderStageRenderState();
   private ShaderStage stage;
   private int inputTextureId = -1;
   private int savedTextureBinding;
   private int savedProgram;
   private int savedMinFilter;
   private int savedMagFilter;

   private ShaderStageRenderState() {
   }

   public void configure(ShaderStage stage, int inputTextureId) {
      this.stage = stage;
      this.inputTextureId = inputTextureId;
   }

   @Override
   public void apply(boolean entering) {
      if (entering) {
         this.savedProgram = GL11C.glGetInteger(35725);
         GL13C.glActiveTexture(33999);
         this.savedTextureBinding = GL11C.glGetInteger(32873);
         int textureIdToBind = this.inputTextureId;
         GL11C.glBindTexture(3553, textureIdToBind);
         this.savedMinFilter = GL11C.glGetTexParameteri(3553, 10241);
         this.savedMagFilter = GL11C.glGetTexParameteri(3553, 10240);
         GL11C.glTexParameteri(3553, 10241, 9729);
         GL11C.glTexParameteri(3553, 10240, 9729);
         GL11C.glTexParameteri(3553, 10242, 33071);
         GL11C.glTexParameteri(3553, 10243, 33071);
         ShaderStage stageForProgram = this.stage;
         CompiledShaderProgram capturedProgram = stageForProgram.getProgram();
         int programId = capturedProgram.programId();
         GL20C.glUseProgram(programId);
         GL11C.glDisable(3042);
         ShaderStage stageForUniforms = this.stage;
         stageForUniforms.updateUniforms();
      } else {
         GL13C.glActiveTexture(33999);
         GL11C.glBindTexture(3553, this.inputTextureId);
         GL11C.glTexParameteri(3553, 10241, this.savedMinFilter);
         GL11C.glTexParameteri(3553, 10240, this.savedMagFilter);
         int textureBindingToRestore = this.savedTextureBinding;
         GL11C.glBindTexture(3553, textureBindingToRestore);
         int programToRestore = this.savedProgram;
         GL20C.glUseProgram(programToRestore);
         GL13C.glActiveTexture(33984);
         GL11C.glEnable(3042);
      }
   }
}
