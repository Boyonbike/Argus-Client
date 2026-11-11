package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum SpeedMineRebreakChoice implements NamedEntry {
   NONE("None"),
   FAST("Fast"),
   INSTANT("Instant");

   private final String name;

   public boolean isNone() {
      return this == NONE;
   }

   @Override
   public String getName() {
      return this.name;
   }

   SpeedMineRebreakChoice(String name) {
      this.name = name;
   }

   public boolean isInstant() {
      return this == INSTANT;
   }

   public boolean isFast() {
      return this == FAST;
   }
}
