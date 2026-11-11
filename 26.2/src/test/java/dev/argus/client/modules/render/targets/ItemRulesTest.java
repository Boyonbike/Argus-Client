package dev.argus.client.modules.render.targets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.argus.client.modules.render.targets.ItemRules.Category;
import dev.argus.client.modules.render.targets.ItemRules.EnchantMode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ItemRulesTest {
   @Test
   void ignorePassesEverything() {
      assertTrue(ItemRules.passesEnchants(EnchantMode.IGNORE, Category.SWORD, Set.of(), Set.of("sharpness")));
   }

   @Test
   void nonGearIsNeverHeldToEnchantRules() {
      assertTrue(ItemRules.passesEnchants(EnchantMode.ANY, Category.OTHER, Set.of(), Set.of()));
      assertTrue(ItemRules.passesEnchants(EnchantMode.SPECIFIC, Category.OTHER, Set.of(), Set.of("mending")));
   }

   @Test
   void anyNeedsAtLeastOneEnchantOnGear() {
      assertFalse(ItemRules.passesEnchants(EnchantMode.ANY, Category.ARMOR, Set.of(), Set.of()));
      assertTrue(ItemRules.passesEnchants(EnchantMode.ANY, Category.ARMOR, Set.of("protection"), Set.of()));
   }

   @Test
   void specificNeedsEveryListedEnchantIgnoringNamespaceAndCase() {
      List<String> required = List.of("minecraft:Mending", "UNBREAKING");
      assertTrue(ItemRules.passesEnchants(EnchantMode.SPECIFIC, Category.TOOL, Set.of("mending", "unbreaking", "efficiency"), required));
      assertFalse(ItemRules.passesEnchants(EnchantMode.SPECIFIC, Category.TOOL, Set.of("mending"), required));
      assertTrue(ItemRules.passesEnchants(EnchantMode.SPECIFIC, Category.MACE, Set.of(), List.of()));
   }

   @Test
   void normalizeStripsNamespace() {
      assertEquals("mending", ItemRules.normalize(" minecraft:Mending "));
      assertEquals("sharpness", ItemRules.normalize("sharpness"));
   }
}
