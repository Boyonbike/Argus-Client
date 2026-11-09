package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;
import dev.argus.client.utils.inventory.InventorySortStrategy;

public enum InventorySorterModeChoice implements NamedEntry {
   TYPE("Type", ComparatorInventorySortStrategy.BY_TYPE),
   NAME("Name", ComparatorInventorySortStrategy.BY_NAME),
   KIT("Kit", KitInventorySortStrategy.INSTANCE);

   private final String displayName;
   private final InventorySortStrategy strategy;

   @Override
   public String getName() {
      return this.displayName;
   }

   InventorySorterModeChoice(String displayName, InventorySortStrategy strategy) {
      this.displayName = displayName;
      this.strategy = strategy;
   }

   public InventorySortStrategy getStrategy() {
      return this.strategy;
   }
}
