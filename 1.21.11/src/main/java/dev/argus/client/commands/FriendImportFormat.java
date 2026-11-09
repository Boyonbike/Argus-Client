package dev.argus.client.commands;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.argus.client.ClientAccess;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

public enum FriendImportFormat {
   FUTURE("Future") {
      @Override
      public List<String> readFriendNames() throws IOException {
         String path = System.getProperty("user.home");
         String separator = File.separator;
         if (!path.endsWith(separator)) {
            path = path + File.separator;
         }

         path = path + "Future%sfriends.json".formatted(File.separator);
         File file = new File(path);
         if (file.exists() && file.isFile()) {
            FileInputStream input = new FileInputStream(file);
            byte[] bytes = input.readAllBytes();
            input.close();
            if (bytes.length == 0) {
               return Collections.emptyList();
            }

            List<String> names = new ArrayList<>();
            Gson parser = ClientAccess.gson;
            String json = new String(bytes, StandardCharsets.UTF_8);
            JsonArray entries = (JsonArray)parser.fromJson(json, JsonArray.class);

            for (int index = 0; index < entries.size(); index++) {
               JsonObject entry = entries.get(index).getAsJsonObject();
               if (entry.has("friend-label")) {
                  String name = entry.get("friend-label").getAsString();
                  if (!names.contains(name)) {
                     names.add(name);
                  }
               }
            }

            return names;
         } else {
            throw new FileNotFoundException();
         }
      }
   },
   RUSHERHACK("RusherHack") {
      @Override
      public List<String> readFriendNames() throws IOException {
         File file = new File("rusherhack" + File.separator + "config" + File.separator + "relations.json");
         if (file.exists() && file.isFile()) {
            FileInputStream input = new FileInputStream(file);
            byte[] bytes = input.readAllBytes();
            input.close();
            if (bytes.length == 0) {
               return Collections.emptyList();
            }

            List<String> names = new ArrayList<>();
            Gson parser = ClientAccess.gson;
            String json = new String(bytes, StandardCharsets.UTF_8);
            JsonArray entries = (JsonArray)parser.fromJson(json, JsonArray.class);

            for (int index = 0; index < entries.size(); index++) {
               JsonObject entry = entries.get(index).getAsJsonObject();
               if (entry.has("username") && entry.has("state")) {
                  String name = entry.get("username").getAsString();
                  String state = entry.get("state").getAsString();
                  if ("FRIEND".equalsIgnoreCase(state) && !names.contains(name)) {
                     names.add(name);
                  }
               }
            }

            return names;
         } else {
            throw new FileNotFoundException();
         }
      }
   },
   METEOR("Meteor") {
      @Override
      public List<String> readFriendNames() throws IOException {
         File file = new File("meteor-client" + File.separator + "friends.nbt");
         if (file.exists() && file.isFile() && file.length() != 0L) {
            List<String> names = new ArrayList<>();
            Path path = file.toPath();
            CompoundTag root = NbtIo.read(path);
            Optional<ListTag> storedTags = root.getList("friends");
            Consumer<ListTag> visitor = tags -> {
               for (Tag tag : tags) {
                  CompoundTag friend = (CompoundTag)tag;
                  if (friend.contains("name")) {
                     String name = friend.getStringOr("name", "");
                     if (!names.contains(name)) {
                        names.add(name);
                     }
                  }
               }
            };
            storedTags.ifPresent(visitor);
            return names;
         } else {
            throw new FileNotFoundException();
         }
      }
   };

   private final String displayName;

   public List<String> readFriendNames() throws IOException {
      return Collections.emptyList();
   }

   public String getDisplayName() {
      return this.displayName;
   }

   FriendImportFormat(String displayName) {
      this.displayName = displayName;
   }
}
