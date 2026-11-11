package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum AuraSwapChoice implements NamedEntry {
   NORMAL("Normal"),
   SWAPBACK("SwapBack"),
   SILENT("Silent");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   AuraSwapChoice(String displayName) {
      this.displayName = displayName;
   }
}
