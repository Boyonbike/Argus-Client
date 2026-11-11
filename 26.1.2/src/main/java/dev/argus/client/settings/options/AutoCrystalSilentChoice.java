package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum AutoCrystalSilentChoice implements NamedEntry {
   NORMAL("Normal"),
   ALTERNATIVE("Alternative");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   AutoCrystalSilentChoice(String displayName) {
      this.displayName = displayName;
   }
}
