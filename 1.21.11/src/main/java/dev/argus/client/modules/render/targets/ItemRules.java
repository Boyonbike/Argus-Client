package dev.argus.client.modules.render.targets;

import dev.argus.client.settings.NamedEntry;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Enchant requirements for dropped-item targets (after Trouser Streak's Advanced Item ESP). */
public final class ItemRules {
   public enum EnchantMode implements NamedEntry {
      /** Enchants don't matter. */
      IGNORE("Ignore"),
      /** Gear must carry at least one enchant; other items always pass. */
      ANY("Any"),
      /** Gear must carry every enchant in its category's list; other items always pass. */
      SPECIFIC("Specific");

      private final String name;

      EnchantMode(String name) {
         this.name = name;
      }

      @Override
      public String getName() {
         return this.name;
      }
   }

   /** Gear categories with their own enchant list. {@link #OTHER} items are never held to enchant rules. */
   public enum Category {
      TOOL,
      SWORD,
      ARMOR,
      MACE,
      TRIDENT,
      OTHER
   }

   private ItemRules() {
   }

   /**
    * Whether an item with these enchant ids passes. Ids are compared without a namespace and ignoring case, so
    * "minecraft:mending" and "Mending" both match "mending".
    */
   public static boolean passesEnchants(EnchantMode mode, Category category, Collection<String> present, Collection<String> required) {
      if (mode == EnchantMode.IGNORE || category == Category.OTHER) {
         return true;
      }

      if (mode == EnchantMode.ANY) {
         return !present.isEmpty();
      }

      Set<String> have = new HashSet<>();
      for (String id : present) {
         have.add(normalize(id));
      }

      for (String id : required) {
         if (!have.contains(normalize(id))) {
            return false;
         }
      }

      return true;
   }

   public static String normalize(String id) {
      String lower = id.trim().toLowerCase(Locale.ROOT);
      int colon = lower.indexOf(':');
      return colon >= 0 ? lower.substring(colon + 1) : lower;
   }

   public static Category categoryOf(ItemStack stack) {
      if (stack.is(ItemTags.SWORDS)) {
         return Category.SWORD;
      }

      if (stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)
         || stack.is(Items.SHEARS) || stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FISHING_ROD)) {
         return Category.TOOL;
      }

      if (stack.is(ItemTags.HEAD_ARMOR) || stack.is(ItemTags.CHEST_ARMOR) || stack.is(ItemTags.LEG_ARMOR) || stack.is(ItemTags.FOOT_ARMOR)
         || stack.is(Items.ELYTRA) || stack.is(Items.SHIELD)) {
         return Category.ARMOR;
      }

      if (stack.is(Items.MACE)) {
         return Category.MACE;
      }

      return stack.is(Items.TRIDENT) ? Category.TRIDENT : Category.OTHER;
   }

   /** The enchant ids on a stack, without namespaces. */
   public static Set<String> enchantIds(ItemStack stack) {
      Set<String> ids = new HashSet<>();
      stack.getEnchantments().keySet().forEach(holder -> holder.unwrapKey().ifPresent(key -> ids.add(key.identifier().getPath())));
      return ids;
   }
}
