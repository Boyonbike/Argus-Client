package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum AntiPhaseMaterialChoice implements NamedEntry {
   LADDER("Ladder", Items.LADDER),
   VINE("Vine", Items.VINE),
   SCAFFOLDING("Scaffolding", Items.SCAFFOLDING),
   ITEMFRAME("ItemFrame", Items.ITEM_FRAME);

   private final String displayName;
   private final Item item;

   @Override
   public String getName() {
      return this.displayName;
   }

   AntiPhaseMaterialChoice(String displayName, Item item) {
      this.displayName = displayName;
      this.item = item;
   }

   public Item getItem() {
      return this.item;
   }
}
