package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum TracersModeChoice implements NamedEntry {
   LINES("Lines"),
   MARKER("Marker");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   TracersModeChoice(String displayName) {
      this.displayName = displayName;
   }
}
