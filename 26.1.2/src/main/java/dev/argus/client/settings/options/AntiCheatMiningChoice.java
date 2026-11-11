package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum AntiCheatMiningChoice implements NamedEntry {
   VANILLA("Vanilla"),
   GRIM("Grim"),
   GRIM_STRICT("GrimStrict");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   AntiCheatMiningChoice(String displayName) {
      this.displayName = displayName;
   }
}
