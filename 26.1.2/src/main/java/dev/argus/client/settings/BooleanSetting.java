package dev.argus.client.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.function.Predicate;

public final class BooleanSetting extends Setting<Boolean> {
   public BooleanSetting(String name, Boolean defaultValue, Predicate<? super Boolean> visibilityPredicate) {
      super(name, defaultValue, visibilityPredicate);
   }

   public BooleanSetting(String name, Boolean defaultValue) {
      super(name, defaultValue);
   }

   @Override
   public void fromJson(JsonElement json) {
      this.setValue(json.getAsBoolean());
   }

   @Override
   public JsonElement toJson() {
      return new JsonPrimitive(this.getValue());
   }

   @Override
   public void parseValue(String text) {
      if ("toggle".equalsIgnoreCase(text)) {
         this.setValue(!this.getValue());
      } else if (text.equals("0") || text.equalsIgnoreCase("false")) {
         this.setValue(false);
      } else if (text.equals("1") || text.equalsIgnoreCase("true")) {
         this.setValue(true);
      }
   }
}
