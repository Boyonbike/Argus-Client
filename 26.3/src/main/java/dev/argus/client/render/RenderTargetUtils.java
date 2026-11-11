package dev.argus.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.argus.client.ClientAccess;
import dev.argus.client.managers.RenderTargetRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;

public final class RenderTargetUtils implements ClientAccess {
   private static RenderTarget currentTarget;

   /** The target effect geometry should be submitted into, or the main target. */
   public static RenderTarget currentRenderTarget() {
      return currentTarget != null ? currentTarget : ClientAccess.argusclientMinecraft.gameRenderer.mainRenderTarget();
   }

   public static void restoreRenderTargets() {
      currentTarget = null;
   }

   public static void overrideRenderTargets(RenderTarget target) {
      RenderTargetRegistry.activate(target);
      currentTarget = target;
   }

   public static void setMainRenderTarget(RenderTarget target) {
      RenderTargetRegistry.activate(target);
      currentTarget = target;
   }

   public static String readShaderSource(String resourcePath) {
      String normalized = resourcePath.endsWith(".vert")
         ? resourcePath.substring(0, resourcePath.length() - 5) + ".vsh"
         : resourcePath.endsWith(".frag")
            ? resourcePath.substring(0, resourcePath.length() - 5) + ".fsh"
            : resourcePath;
      try (InputStream stream = BaseShaderPass.class.getResourceAsStream(
            "/assets/argusclient/shaders/" + normalized)) {
         if (stream == null) {
            return "";
         }
         return IOUtils.toString(stream, StandardCharsets.UTF_8);
      } catch (IOException failure) {
         failure.printStackTrace();
         return "";
      }
   }

   private RenderTargetUtils() {
      throw new AssertionError();
   }
}
