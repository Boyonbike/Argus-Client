package dev.argus.client.gui.components;

import dev.argus.client.ClientAccess;
import dev.argus.client.gui.panels.Panel;
import dev.argus.client.utils.inventory.EnchantmentUtils;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

public class EnchantmentListComponent extends AbstractItemListComponent<EnchantmentListEntry> {
   private static ItemStack enchantedBookIcon;

   private static ItemStack enchantedBookIcon() {
      if (enchantedBookIcon == null) {
         enchantedBookIcon = Items.ENCHANTED_BOOK.getDefaultInstance();
         enchantedBookIcon.enchant(EnchantmentUtils.getHolder(Enchantments.FORTUNE), 1337);
      }
      return enchantedBookIcon;
   }

   public EnchantmentListComponent(Panel panel, Predicate<EnchantmentListEntry> filter) {
      super(panel, filter, 256);
   }

   @Override
   public void update(double mouseX, double mouseY) {
      this.entries.clear();
      Registry<Enchantment> registry = ClientAccess.argusclientMinecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

      for (ResourceKey<Enchantment> enchantmentKey : registry.registryKeySet()) {
         Identifier enchantmentId = enchantmentKey.identifier();
         Enchantment enchantment = registry.getValue(enchantmentId);
         int maxLevel = enchantment.getMaxLevel();

         for (int level = 1; level <= maxLevel; level++) {
            this.entries.add(new EnchantmentListEntry(enchantmentKey, level, enchantment.description().getString()));
         }
      }

      this.entries.removeIf(this.filter.negate());
   }

   public String getEntryLabel(EnchantmentListEntry entry) {
      String levelName = Component.translatable("enchantment.level." + entry.level()).getString();
      Enchantment enchantment = (Enchantment)EnchantmentUtils.getHolder(entry.enchantmentKey()).value();
      return enchantment.getMaxLevel() == 1 ? entry.name() : (entry.name() + "").replaceFirst("\u0001", Matcher.quoteReplacement(levelName));
   }

   public ItemStack getEntryIcon(EnchantmentListEntry unusedEntry) {
      return enchantedBookIcon();
   }
}
