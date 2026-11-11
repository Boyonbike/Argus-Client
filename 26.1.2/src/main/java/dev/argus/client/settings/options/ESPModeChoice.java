package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum ESPModeChoice implements NamedEntry {
   PLAIN("Plain"),
   TEXT("Text"),
   BOTH("Both");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   ESPModeChoice(String displayName) {
      this.displayName = displayName;
   }
}
