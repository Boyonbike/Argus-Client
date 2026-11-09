package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum AntiCheatPlacementsChoice implements NamedEntry {
   VANILLA("Vanilla"),
   PAPER("Paper");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   AntiCheatPlacementsChoice(String displayName) {
      this.displayName = displayName;
   }
}
