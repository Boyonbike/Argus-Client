package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum SpeedMineAutoSwapChoice implements NamedEntry {
   NONE("None"),
   NORMAL("Normal"),
   SILENT("Silent");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   SpeedMineAutoSwapChoice(String displayName) {
      this.displayName = displayName;
   }
}
