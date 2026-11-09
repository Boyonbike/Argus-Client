package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum AuraTypeChoice implements NamedEntry {
   BOX("Box"),
   CIRCLE("Circle");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   AuraTypeChoice(String displayName) {
      this.displayName = displayName;
   }
}
