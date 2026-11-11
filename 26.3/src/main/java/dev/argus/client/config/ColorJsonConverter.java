package dev.argus.client.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.awt.Color;
import org.jetbrains.annotations.NotNull;

public final class ColorJsonConverter {
   @NotNull
   public Color fromJson(JsonElement json) {
      if (json.isJsonArray()) {
         JsonArray array = json.getAsJsonArray();
         if (array.size() >= 4) {
            return new Color(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt(), array.get(3).getAsInt());
         }
      }

      throw new IllegalArgumentException();
   }

   @NotNull
   public JsonElement toJson(Color color) {
      JsonArray array = new JsonArray();
      array.add(color.getRed());
      array.add(color.getGreen());
      array.add(color.getBlue());
      array.add(color.getAlpha());
      return array;
   }
}
