package dev.argus.client.render;

import dev.argus.client.utils.chat.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;

public interface ReloadableShaderPass {
   String RELOAD_FAILURE_MESSAGE = "Failed to compile shader, check Minecraft logs for more details.";

   CompiledShaderProgram compileProgram();

   default void reload(BaseShaderPass pass) {
      if (pass.program != null) {
         CompiledShaderProgram oldProgram = pass.program;
         oldProgram.delete();
      }

      try {
         pass.program = this.compileProgram();
      } catch (Throwable failure) {
         String errorMessage = "Failed to compile shader, check Minecraft logs for more details.";
         String failureMessage = failure.getMessage();
         ShaderDiagnostics.logError(failureMessage);
         MutableComponent plainNotification = Component.literal(errorMessage);
         ChatFormatting errorColor = ChatFormatting.RED;
         MutableComponent coloredNotification = plainNotification.withStyle(errorColor);
         MessageSignature signature = ChatUtils.createSignature(-99);
         ChatUtils.MessagePriority priority = ChatUtils.MessagePriority.HIGH;
         ChatUtils.printChat(coloredNotification, signature, priority);
         String fallbackVertexRead = RenderTargetUtils.readShaderSource(pass.vertexResource);
         String fallbackFragmentRead = RenderTargetUtils.readShaderSource(pass.fragmentResource);
         pass.program = CompiledShaderProgram.compile(fallbackVertexRead, fallbackFragmentRead);
      }

      pass.uniforms = pass.program.uniforms();
   }
}
