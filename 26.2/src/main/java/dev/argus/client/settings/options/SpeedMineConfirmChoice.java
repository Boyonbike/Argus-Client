package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

/** How long Instant rebreaks wait for the server to confirm the previous rebreak. */
public enum SpeedMineConfirmChoice implements NamedEntry {
   /** Wait for the server's air update between rebreaks. */
   STRICT("Strict"),
   /** Never wait; rebreak as soon as the replacement appears. */
   STRONG("Strong"),
   /** Strict until one rebreak has been confirmed, then Strong. */
   BYPASS("Bypass");

   private final String name;

   SpeedMineConfirmChoice(String name) {
      this.name = name;
   }

   @Override
   public String getName() {
      return this.name;
   }
}
