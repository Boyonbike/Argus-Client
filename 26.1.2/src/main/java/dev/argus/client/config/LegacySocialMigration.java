package dev.argus.client.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ConfigurationManager;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Path;

public final class LegacySocialMigration implements ClientAccess {
   private LegacySocialMigration() {
   }

   public static void migrate() throws IOException {
      Path friendsPath = ConfigurationManager.ROOT_DIRECTORY.resolve("friends.json");
      Path socialsPath = ConfigurationManager.ROOT_DIRECTORY.resolve("socials.json");
      if (friendsPath.toFile().exists()) {
         if (!socialsPath.toFile().exists()) {
            String friendsText = FileUtils.readUtf8(friendsPath);
            JsonObject friendsRoot = JsonParser.parseString(friendsText).getAsJsonObject();

            for (JsonElement friend : friendsRoot.getAsJsonArray("friends")) {
               SocialRegistry registry = ClientContext.socialRegistry;
               String name = friend.getAsString();
               registry.addFriend(name);
            }

            Gson gson = ClientAccess.gson;
            SocialRegistry registry = ClientContext.socialRegistry;
            JsonElement socials = registry.toJson();
            FileUtils.writeUtf8(socialsPath, gson.toJson(socials));
            friendsPath.toFile().delete();
         }
      }
   }
}
