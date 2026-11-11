package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum NoSlowStrictInventoryChoice implements NamedEntry {
   NONE("None"),
   NCP("NCP"),
   GRIM_STRICT("GrimStrict");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   NoSlowStrictInventoryChoice(String displayName) {
      this.displayName = displayName;
   }
}
