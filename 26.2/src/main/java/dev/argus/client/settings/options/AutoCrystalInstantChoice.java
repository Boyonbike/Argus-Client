package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum AutoCrystalInstantChoice implements NamedEntry {
   ALWAYS("Always"),
   STRICT("Strict"),
   NONE("None");

   private final String displayName;

   public boolean isEnabled() {
      return this != NONE;
   }

   @Override
   public String getName() {
      return this.displayName;
   }

   AutoCrystalInstantChoice(String displayName) {
      this.displayName = displayName;
   }
}
