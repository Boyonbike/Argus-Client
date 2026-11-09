package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum InventorySorterScopeChoice implements NamedEntry {
   INVENTORY("Inventory", 9, 36),
   HOTBAR("Hotbar", 0, 9),
   BOTH("Both", 0, 36);

   private final String displayName;
   private final int startInclusive;
   private final int endExclusive;

   @Override
   public String getName() {
      return this.displayName;
   }

   InventorySorterScopeChoice(String displayName, int startInclusive, int endExclusive) {
      this.displayName = displayName;
      this.startInclusive = startInclusive;
      this.endExclusive = endExclusive;
   }

   public int getStartInclusive() {
      return this.startInclusive;
   }

   public int getEndExclusive() {
      return this.endExclusive;
   }
}
