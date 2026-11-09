package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum SpeedMineValue121Choice implements NamedEntry {
   OFF("Off"),
   TICK("Tick"),
   SLOW("Slow");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   SpeedMineValue121Choice(String displayName) {
      this.displayName = displayName;
   }
}
