package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum BlockerModeChoice implements NamedEntry {
   DAMAGE("Damage"),
   DESTROY("Destroy"),
   INSTANT("Instant");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   BlockerModeChoice(String displayName) {
      this.displayName = displayName;
   }
}
