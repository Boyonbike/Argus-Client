package dev.argus.client.audio;

import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class OfflineSoundAssets {
   private static final String PREFIX = "/assets/argusclient/offline/killstreaks/";

   private OfflineSoundAssets() {
   }

   public static void installKillstreaks(Path path) {
      Path directory = path.toAbsolutePath().normalize();
      if (!Files.isDirectory(directory)) {
         try {
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
               throw new IOException("Sound-pack path is occupied by a file: " + directory);
            }

            String manifest;
            try (InputStream inputStream = OfflineSoundAssets.class.getResourceAsStream("/assets/argusclient/offline/killstreaks/manifest.txt")) {
               if (inputStream == null) {
                  throw new IOException("Preserved sound-pack manifest is missing");
               }

               manifest = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            for (String relativePath : manifest.lines().toList()) {
               if (!relativePath.isBlank()) {
                  Path destination = directory.resolve(relativePath).normalize();
                  if (!destination.startsWith(directory) || !relativePath.endsWith(".ogg")) {
                     throw new IOException("Invalid preserved sound path");
                  }

                  InputStream inputStream = OfflineSoundAssets.class.getResourceAsStream("/assets/argusclient/offline/killstreaks/" + relativePath);
                  if (inputStream != null) {
                     try {
                        Files.createDirectories(destination.getParent());
                        if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                           Files.copy(inputStream, destination);
                        }
                     } finally {
                        inputStream.close();
                     }
                  }
               }
            }
         } catch (IOException | SecurityException exception) {
            System.getLogger("Argus Client Offline Sounds").log(Level.WARNING, "Optional killstreak sounds are unavailable: " + exception.getMessage());
         }
      }
   }
}
