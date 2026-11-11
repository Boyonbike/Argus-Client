package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum AutoArmorBlastChoice implements NamedEntry {
   NONE("None"),
   LEGS("Legs"),
   SMART("1.21"),
   ALL("All");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   AutoArmorBlastChoice(String displayName) {
      this.displayName = displayName;
   }
}
