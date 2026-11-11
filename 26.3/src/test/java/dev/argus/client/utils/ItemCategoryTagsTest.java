package dev.argus.client.utils;

import net.minecraft.tags.ItemTags;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemCategoryTagsTest {
   @Test
   void removedItemClassesMapToTheirTags() {
      assertEquals("minecraft:axes", ItemTags.AXES.location().toString());
      assertEquals("minecraft:beds", ItemTags.BEDS.location().toString());
      assertEquals("minecraft:signs", ItemTags.SIGNS.location().toString());
   }
}
