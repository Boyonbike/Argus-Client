package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum DimensionType implements NamedEntry {
   OVERWORLD("overworld", "Overworld"),
   THE_NETHER("the_nether", "Nether"),
   THE_END("the_end", "End");

   private final String name;
   private final String displayName;

   public boolean isEnd() {
      return this == THE_END;
   }

   public boolean isOverworld() {
      return this == OVERWORLD;
   }

   public boolean isNether() {
      return this == THE_NETHER;
   }

   @Override
   public String getName() {
      return this.name;
   }

   DimensionType(String name, String displayName) {
      this.name = name;
      this.displayName = displayName;
   }

   public String getDisplayName() {
      return this.displayName;
   }
}
