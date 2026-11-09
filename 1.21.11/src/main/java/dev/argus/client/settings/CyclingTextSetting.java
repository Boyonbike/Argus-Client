package dev.argus.client.settings;

import dev.argus.client.render.TextUtils;

public interface CyclingTextSetting {
   boolean matchesText(String var1);

   void setValueFromText(String var1);

   default void parseValue(String text) {
      String[] values = TextUtils.splitQuotedArguments(text);
      if (values.length > 1) {
         this.cycleValues(values);
      } else {
         this.setValueFromText(text == null ? "" : text);
      }
   }

   private void cycleValues(String[] values) {
      for (int index = 0; index < values.length; index++) {
         String candidate = values[index];
         if (this.matchesText(candidate)) {
            this.setValueFromText(values[(index + 1) % values.length]);
            return;
         }
      }

      this.setValueFromText(values[0]);
   }
}
