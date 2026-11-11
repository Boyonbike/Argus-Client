package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum NoSlowModeChoice implements NamedEntry {
   NONE("None"),
   NCP("NCP"),
   GRIMOLD("GrimOld"),
   GRIMV3("Slow"),
   _2B2T("2b2t"),
   CONSTANTIAM("Constantiam");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   NoSlowModeChoice(String displayName) {
      this.displayName = displayName;
   }
}
