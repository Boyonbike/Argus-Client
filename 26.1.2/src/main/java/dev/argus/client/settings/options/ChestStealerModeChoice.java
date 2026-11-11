package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum ChestStealerModeChoice implements NamedEntry {
   STEAL("Steal"),
   FILL("Fill"),
   DROP("Drop"),
   REFILL("Refill"),
   REKIT("Rekit");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   ChestStealerModeChoice(String displayName) {
      this.displayName = displayName;
   }
}
