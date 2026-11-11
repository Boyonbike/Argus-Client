package dev.argus.client.render;

import dev.argus.client.ClientAccess;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public final class ShaderDiagnostics implements ClientAccess {
   private ShaderDiagnostics() {
   }

   public static String formatCompileLog(String source, int shaderId) {
      return "Shader compilation is handled by RenderPearl; see the client log for the compiler output.";
   }

   public static void logError(String message) {
      Logger logger = ClientAccess.logger;
      Marker marker = MarkerFactory.getMarker("Shaders");
      logger.error(marker, message);
   }
}
