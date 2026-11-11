package dev.argus.client.settings;

import com.google.gson.JsonElement;

public interface JsonConfigurable {
   void fromJson(JsonElement var1);

   JsonElement toJson();

   default String getConfigName() {
      return "";
   }
}
