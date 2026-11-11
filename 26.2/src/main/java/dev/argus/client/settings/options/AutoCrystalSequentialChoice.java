package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum AutoCrystalSequentialChoice implements NamedEntry {
   NONE("None"),
   STRICT("Strict"),
   STRONG("Strong");

   private final String displayName;

   public boolean isEnabled() {
      return this != NONE;
   }

   @Override
   public String getName() {
      return this.displayName;
   }

   AutoCrystalSequentialChoice(String displayName) {
      this.displayName = displayName;
   }
}
