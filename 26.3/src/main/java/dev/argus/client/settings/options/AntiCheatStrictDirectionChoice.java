package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum AntiCheatStrictDirectionChoice implements NamedEntry {
   OFF("Off"),
   NCP("NCP"),
   GRIM("Grim");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   AntiCheatStrictDirectionChoice(String displayName) {
      this.displayName = displayName;
   }
}
