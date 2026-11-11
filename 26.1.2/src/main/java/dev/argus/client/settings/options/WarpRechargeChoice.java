package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum WarpRechargeChoice implements NamedEntry {
   HOLD("Hold"),
   INSTANT("Instant"),
   NONE("None");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   WarpRechargeChoice(String displayName) {
      this.displayName = displayName;
   }
}
