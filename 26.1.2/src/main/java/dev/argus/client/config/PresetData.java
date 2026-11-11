package dev.argus.client.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.argus.client.ClientAccess;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Path;

public record PresetData(String name, JsonObject contents) implements ClientAccess, NamedEntry {
   @Override
   public String getName() {
      return this.name;
   }

   public void saveToDirectory(Path directory) throws IOException {
      String presetName = this.name;
      Path targetPath = directory.resolve(presetName + ".json");
      Gson gson = ClientAccess.gson;
      JsonObject contents = this.contents();
      String serializedContents = gson.toJson(contents);
      FileUtils.writeUtf8Atomically(targetPath, serializedContents);
   }
}
