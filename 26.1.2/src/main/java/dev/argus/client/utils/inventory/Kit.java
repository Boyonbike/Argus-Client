package dev.argus.client.utils.inventory;

import dev.argus.client.managers.KitRegistry;
import dev.argus.client.settings.NamedEntry;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class Kit implements NamedEntry {
   private final Map<Integer, ItemDescriptor> itemsBySlot = new HashMap();
   private final String name;

   public Map<Integer, ItemDescriptor> getItemsBySlot() {
      return this.itemsBySlot;
   }

   @Override
   public String getName() {
      return this.name;
   }

   public Kit(String name) {
      this.name = name;
   }

   public record ItemDescriptor(int itemId, String potionSignature) {
      public boolean matches(ItemStack stack) {
         Item item = stack.getItem();
         return Item.getId(item) == this.itemId && Objects.equals(KitRegistry.getPotionSignature(stack), this.potionSignature);
      }
   }
}
