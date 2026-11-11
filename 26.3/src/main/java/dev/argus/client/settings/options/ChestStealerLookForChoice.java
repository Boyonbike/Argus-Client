package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum ChestStealerLookForChoice implements NamedEntry {
   NAME("Name"),
   ITEM("Item");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   ChestStealerLookForChoice(String displayName) {
      this.displayName = displayName;
   }
}
