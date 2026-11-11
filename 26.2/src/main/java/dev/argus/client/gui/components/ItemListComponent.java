package dev.argus.client.gui.components;

import dev.argus.client.gui.panels.Panel;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemListComponent extends AbstractItemListComponent<Item> {
   public ItemListComponent(Panel panel, Predicate<Item> filter) {
      super(panel, filter, BuiltInRegistries.ITEM.size());
   }

   @Override
   public void update(double mouseX, double mouseY) {
      this.entries.clear();
      BuiltInRegistries.ITEM.stream().filter(this.filter).forEach(this.entries::add);
   }

   public String getEntryLabel(Item item) {
      return item.getName(item.getDefaultInstance()).getString();
   }

   public ItemStack getEntryIcon(Item item) {
      return item.getDefaultInstance();
   }
}
