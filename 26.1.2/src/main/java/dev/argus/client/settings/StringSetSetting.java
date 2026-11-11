package dev.argus.client.settings;

import com.google.gson.JsonElement;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

public final class StringSetSetting extends SetSetting<String> {
   public StringSetSetting(String name, String... defaultEntries) {
      super(name, defaultEntries);
   }

   public StringSetSetting(String name, Predicate<? super Set<String>> visibility, String... defaultEntries) {
      super(name, visibility, defaultEntries);
   }

   public String parseEntry(String text) {
      return text.toLowerCase(Locale.ROOT);
   }

   @Override
   public Collection<String> getAvailableEntries() {
      return Collections.emptyList();
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json.isJsonArray()) {
         Set<String> values = (Set<String>)this.getValue();
         values.clear();

         for (JsonElement entry : json.getAsJsonArray()) {
            String text = entry.getAsString();
            values.add(text);
         }
      }
   }

   public String formatEntry(String entry) {
      return entry.toLowerCase(Locale.ROOT);
   }
}
