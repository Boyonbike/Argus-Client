package dev.argus.client.render;

import dev.argus.client.ArgusClient;
import dev.argus.client.managers.ConfigurationManager;
import dev.argus.client.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;

public final class BundledResourceInstaller {
   private final HashMap<String, String> resourcePaths = new HashMap<>();

   public void installMissingResources() {
      this.resourcePaths.forEach(this::installMissingResource);
   }

   public BundledResourceInstaller() {
      this.resourcePaths.put("textures/overlay.png", "assets/argusclient/textures/overlay.png");
      this.resourcePaths.put("textures/skin_protect.png", "assets/argusclient/textures/skin_protect.png");
      this.resourcePaths.put("textures/shine.png", "assets/argusclient/textures/shine.png");
      this.resourcePaths.put("shaders/sky.frag", "assets/argusclient/shaders/sky/sky.frag");
      this.resourcePaths.put("shaders/custom.frag", "assets/argusclient/shaders/custom/example.frag");
   }

   private void installMissingResource(String relativeDestination, String bundledResource) {
      Path destinationPath = ConfigurationManager.ROOT_DIRECTORY.resolve(relativeDestination);
      File destinationFile = destinationPath.toFile();
      if (!destinationFile.exists()) {
         try {
            try (InputStream input = ArgusClient.class.getResourceAsStream("/" + bundledResource)) {
               if (input != null) {
                  byte[] resourceBytes = input.readAllBytes();
                  FileUtils.writeBytes(destinationPath, resourceBytes);
                  return;
               }
            }

            return;
         } catch (IOException ioFailure) {
            ioFailure.printStackTrace();
         }
      }
   }
}
