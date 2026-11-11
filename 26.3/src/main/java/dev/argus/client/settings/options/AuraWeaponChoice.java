package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum AuraWeaponChoice implements NamedEntry {
   NONE("None"),
   REQUIRE("Require"),
   SWAP("Swap");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   AuraWeaponChoice(String displayName) {
      this.displayName = displayName;
   }
}
