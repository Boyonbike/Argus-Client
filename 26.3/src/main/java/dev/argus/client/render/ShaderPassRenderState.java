package dev.argus.client.render;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;

public class ShaderPassRenderState implements RenderState {
   public static final ShaderPassRenderState INSTANCE = new ShaderPassRenderState();
   private ShaderPass pass;
   private int inputTextureId = -1;
   private int savedTextureBinding;
   private int savedProgram;
   private int savedMinFilter;
   private int savedMagFilter;

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
      if (entering) {
         this.savedProgram = GL11C.glGetInteger(35725);
         GL13C.glActiveTexture(33999);
         this.savedTextureBinding = GL11C.glGetInteger(32873);
         if (this.inputTextureId != -1) {
            int textureIdToBind = this.inputTextureId;
            GL11C.glBindTexture(3553, textureIdToBind);
            this.savedMinFilter = GL11C.glGetTexParameteri(3553, 10241);
            this.savedMagFilter = GL11C.glGetTexParameteri(3553, 10240);
            GL11C.glTexParameteri(3553, 10241, 9729);
            GL11C.glTexParameteri(3553, 10240, 9729);
         }

         GL11C.glTexParameteri(3553, 10242, 33071);
         GL11C.glTexParameteri(3553, 10243, 33071);
         ShaderPass passForProgram = this.pass;
         CompiledShaderProgram capturedProgram = passForProgram.getProgram();
         int programId = capturedProgram.programId();
         GL20C.glUseProgram(programId);
         ShaderPass passForUniforms = this.pass;
         passForUniforms.updateUniforms();
      } else {
         GL13C.glActiveTexture(33999);
         if (this.inputTextureId != -1) {
            GL11C.glBindTexture(3553, this.inputTextureId);
            GL11C.glTexParameteri(3553, 10241, this.savedMinFilter);
            GL11C.glTexParameteri(3553, 10240, this.savedMagFilter);
            int textureBindingToRestore = this.savedTextureBinding;
            GL11C.glBindTexture(3553, textureBindingToRestore);
         }

         int programToRestore = this.savedProgram;
         GL20C.glUseProgram(programToRestore);
         GL13C.glActiveTexture(33984);
      }
   }
}
