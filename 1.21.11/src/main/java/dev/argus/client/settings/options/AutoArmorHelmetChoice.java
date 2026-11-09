package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum AutoArmorHelmetChoice implements NamedEntry {
   BEST("Best"),
   TURTLE("Turtle"),
   GOLD("Golden"),
   NONE("None");

   private final String displayName;

   public boolean isNone() {
      return this == NONE;
   }

   public boolean isTurtle() {
      return this == TURTLE;
   }

   public boolean isGold() {
      return this == GOLD;
   }

   @Override
   public String getName() {
      return this.displayName;
   }

   AutoArmorHelmetChoice(String displayName) {
      this.displayName = displayName;
   }
}
