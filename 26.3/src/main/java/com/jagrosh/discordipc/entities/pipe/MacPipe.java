package com.jagrosh.discordipc.entities.pipe;

import com.jagrosh.discordipc.IPCClient;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

public class MacPipe extends UnixPipe {
   MacPipe(IPCClient client, HashMap callbacks, File endpoint) throws IOException {
      super(client, callbacks, endpoint);
   }

   private void registerCommand(String applicationId, String command) {
      String home = System.getenv("HOME");
      if (home == null) {
         throw new RuntimeException("Unable to find user HOME directory");
      }

      String path = home + "/Library/Application Support/discord";
      if (!this.mkdir(path)) {
         throw new RuntimeException("Failed to create directory '" + path + "'");
      }

      path = path + "/games";
      if (!this.mkdir(path)) {
         throw new RuntimeException("Failed to create directory '" + path + "'");
      }

      path = path + "/" + applicationId + ".json";

      try (FileWriter writer = new FileWriter(path)) {
         writer.write("{\"command\": \"" + command + "\"}");
      } catch (Exception failure) {
         throw new RuntimeException("Failed to write fame info into '" + path + "'");
      }
   }

   private void registerUrl(String applicationId) {
      throw new UnsupportedOperationException("MacOS URL registration is not supported at this time.");
   }

   @Override
   public void registerApp(String applicationId, String command) {
      try {
         if (command != null) {
            this.registerCommand(applicationId, command);
         } else {
            this.registerUrl(applicationId);
         }
      } catch (Exception failure) {
         throw new RuntimeException("Failed to register " + (command == null ? "url" : "command"), failure);
      }
   }

   @Override
   public void registerSteamGame(String applicationId, String command) {
      this.registerApp(applicationId, "steam://rungameid/" + command);
   }
}
