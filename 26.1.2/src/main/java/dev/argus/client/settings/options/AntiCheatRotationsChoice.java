package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum AntiCheatRotationsChoice implements NamedEntry {
   MOTION("Motion"),
   SILENT("Silent");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   AntiCheatRotationsChoice(String displayName) {
      this.displayName = displayName;
   }
}
