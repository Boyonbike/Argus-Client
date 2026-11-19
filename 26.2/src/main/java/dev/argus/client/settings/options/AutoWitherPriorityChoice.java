package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum AutoWitherPriorityChoice implements NamedEntry {
   CLOSEST("Closest"),
   FURTHEST("Furthest"),
   RANDOM("Random");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   AutoWitherPriorityChoice(String displayName) {
      this.displayName = displayName;
   }
}
