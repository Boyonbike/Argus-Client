package dev.argus.client.render.effect;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class EffectBackendAuditTest {
   private static final List<String> EFFECT_FILES = List.of(
      "BaseShaderPass.java", "CompiledShaderProgram.java", "ShaderUniforms.java",
      "ShaderPassRenderState.java", "ShaderStageRenderState.java",
      "RenderTargetShaderStage.java", "PostProcessShaderPass.java",
      "BloomShaderPass.java", "GradientShaderPass.java", "RainbowShaderPass.java",
      "SolidShaderPass.java", "CustomShaderPass.java", "DepthOfFieldPipeline.java",
      "RenderTargetUtils.java", "ShaderDiagnostics.java", "SkyboxShaderPass.java",
      "MaskShaderRenderer.java", "BloomMaskPipeline.java", "BlurMaskPipeline.java",
      "GlowyShaderPipeline.java", "ESPOutlineShaderPass.java", "PreprocessedShaderPass.java",
      "ShaderPipeline.java");

   @Test
   void effectBackendUsesNoRawOpenGl() throws Exception {
      Path dir = Path.of("src/main/java/dev/argus/client/render");
      for (String name : EFFECT_FILES) {
         String text = Files.readString(dir.resolve(name));
         assertFalse(text.contains("org.lwjgl.opengl"), name + " imports raw OpenGL");
         assertFalse(text.contains("GlTexture"), name + " uses a GL texture handle");
         assertFalse(text.contains("GlStateManager"), name + " uses GL state");
         assertFalse(text.contains("glfwGetTime"), name + " uses GLFW time");
      }
   }

   @Test
   void effectBackendHasNoGlslUniformLocations() throws Exception {
      try (Stream<Path> files = Files.walk(Path.of("src/main/resources/assets/argusclient/shaders"))) {
         for (Path file : files.filter(p -> p.toString().endsWith(".frag")
               || p.toString().endsWith(".vert") || p.toString().endsWith(".fsh")
               || p.toString().endsWith(".vsh")).toList()) {
            assertFalse(Files.readString(file).contains("glGetUniformLocation"), file.toString());
         }
      }
   }
}
