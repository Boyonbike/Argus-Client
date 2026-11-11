package dev.argus.client.render;

import dev.argus.client.managers.RenderTargetRegistry;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.argus.client.ClientAccess;
import dev.argus.mixin.MinecraftAccessor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;

public final class RenderTargetUtils implements ClientAccess {
   private static RenderTarget savedMainRenderTarget;

   public static void popFramebuffer() {
      ((LevelRendererFramebufferAccess)ClientAccess.argusclientMinecraft.levelRenderer).argusclient$popFramebuffer();
   }

   public static void restoreRenderTargets() {
      popFramebuffer();
      if (savedMainRenderTarget != null) {
         setMainRenderTarget(savedMainRenderTarget);
         savedMainRenderTarget = null;
      }
   }

   private RenderTargetUtils() {
      throw new AssertionError();
   }

   public static void pushFramebuffer(RenderTarget target) {
      ((LevelRendererFramebufferAccess)ClientAccess.argusclientMinecraft.levelRenderer).argusclient$pushFramebuffer(target);
   }

   public static void overrideRenderTargets(RenderTarget target) {
      RenderTargetRegistry.activate(target);
      savedMainRenderTarget = ClientAccess.argusclientMinecraft.getMainRenderTarget();
      pushFramebuffer(target);
      setMainRenderTarget(target);
   }

   public static void setMainRenderTarget(RenderTarget target) {
      RenderTargetRegistry.activate(target);
      ((MinecraftAccessor)ClientAccess.argusclientMinecraft).argusclient$setFramebuffer(target);
   }

   public static String readShaderSource(String resourcePath) {
      try {
         InputStream stream = BaseShaderPass.class.getResourceAsStream("/assets/argusclient/shaders/" + resourcePath);
         assert stream != null;
         return IOUtils.toString(stream, StandardCharsets.UTF_8);
      } catch (IOException | AssertionError failure) {
         failure.printStackTrace();
         return "";
      }
   }
}
