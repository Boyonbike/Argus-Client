package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum MultiTaskModeChoice implements NamedEntry {
   NONE("None"),
   STRICT("Strict"),
   OFFHAND("Offhand"),
   ALL("Always");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   MultiTaskModeChoice(String displayName) {
      this.displayName = displayName;
   }
}
