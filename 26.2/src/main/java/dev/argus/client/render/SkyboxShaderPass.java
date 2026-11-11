package dev.argus.client.render;

import dev.argus.client.ClientContext;
import dev.argus.client.managers.ConfigurationManager;
import dev.argus.client.modules.render.SkyboxModule;
import dev.argus.client.utils.UncheckedExceptions;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

public class SkyboxShaderPass extends BaseShaderPass implements ReloadableShaderPass {
   private static final SkyboxModule SKYBOX_MODULE = ClientContext.getModule(SkyboxModule.class);

   @Override
   public void updateUniforms() {
      Minecraft clientInstance = Minecraft.getInstance();
      int screenWidth = clientInstance.getWindow().getScreenWidth();
      int screenHeight = clientInstance.getWindow().getScreenHeight();
      float aspectRatio = (float)screenWidth / screenHeight;
      Camera pitchCamera = clientInstance.gameRenderer.mainCamera();
      float cameraRotation = -pitchCamera.xRot();
      float normalizedPitch = cameraRotation / 90.0F;
      cameraRotation = clientInstance.gameRenderer.mainCamera().yRot();
      float normalizedYaw = cameraRotation / 180.0F * aspectRatio;
      ShaderUniforms scaleUniformsCapture = this.uniforms;
      scaleUniformsCapture.set("u_Scale", 1.0F);
      ShaderUniforms mouseUniformsCapture = this.uniforms;
      SkyboxModule yawModuleCapture = SKYBOX_MODULE;
      float mouseXCapture = normalizedYaw * yawModuleCapture.getParallax();
      SkyboxModule pitchModuleCapture = SKYBOX_MODULE;
      float mouseY = normalizedPitch * pitchModuleCapture.getParallax();
      mouseUniformsCapture.set("u_Mouse", mouseXCapture, mouseY);
      ShaderUniforms resolutionUniformsCapture = this.uniforms;
      resolutionUniformsCapture.set("u_Resolution", screenWidth, screenHeight);
      ShaderUniforms timeUniformsCapture = this.uniforms;
      float elapsedMillis = (float)(System.currentTimeMillis() - START_TIME_MILLIS);
      float elapsedTime = elapsedMillis / 1000.0F;
      timeUniformsCapture.set("u_Time", elapsedTime);
      ShaderUniforms colorUniformsCapture = this.uniforms;
      SkyboxModule colorModuleCapture = SKYBOX_MODULE;
      Color color = colorModuleCapture.getShaderColor();
      colorUniformsCapture.set("u_Color", color);
   }

   @Override
   public CompiledShaderProgram compileProgram() {
      Path shaderDirectory = ConfigurationManager.SHADERS_DIRECTORY;
      Path fragmentPath = shaderDirectory.resolve("sky.frag");

      String fragmentSource;
      try {
         fragmentSource = Files.readString(fragmentPath);
      } catch (Throwable failure) {
         throw UncheckedExceptions.rethrow(failure);
      }

      String vertexSource = RenderTargetUtils.readShaderSource(this.vertexResource);
      String vertexSourceForCompile = vertexSource;
      return CompiledShaderProgram.compile(vertexSourceForCompile, fragmentSource);
   }

   public SkyboxShaderPass() {
      super("sky/base.vert", "sky/sky.frag");
      this.reload(this);
   }
}
