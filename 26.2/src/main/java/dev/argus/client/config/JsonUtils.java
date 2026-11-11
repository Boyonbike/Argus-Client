package dev.argus.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map.Entry;

public final class JsonUtils {
   private JsonUtils() {
   }

   public static boolean isRecursivelyEmpty(JsonObject object) {
      for (Entry<String, JsonElement> entry : object.entrySet()) {
         if (!(entry.getValue() instanceof JsonObject nestedObject)) {
            return false;
         }

         if (!isRecursivelyEmpty(nestedObject)) {
            return false;
         }
      }

      return true;
   }

   public static boolean hasProperties(JsonObject object, String... propertyNames) {
      for (String propertyName : propertyNames) {
         if (!object.has(propertyName)) {
            return false;
         }
      }

      return true;
   }

   public static void removeProperties(JsonObject object, String... propertyNames) {
      for (String propertyName : propertyNames) {
         object.remove(propertyName);
      }
   }
}
