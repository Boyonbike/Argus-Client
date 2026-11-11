package dev.argus.client.utils.inventory;

import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;

public final class ItemUtils {
   private static final Component maceDisplayName = createCompatibilityName("1.21 Mace");
   private static final Set<Component> spearDisplayNames;

   public static boolean isSpear(ItemStack stack) {
      DataComponentType<Component> nameComponent = DataComponents.CUSTOM_NAME;
      Component customName = stack.get(nameComponent);
      if (customName != null && spearDisplayNames.contains(customName)) {
         return true;
      }

      TagKey spearTag = ItemTags.SPEARS;
      return stack.is(spearTag);
   }

   private ItemUtils() {
      throw new AssertionError();
   }

   public static boolean isGlass(Item item) {
      return !(item instanceof BlockItem blockItem)
         ? false
         : blockItem.getBlock() instanceof StainedGlassBlock
            || blockItem.getBlock() == Blocks.TINTED_GLASS
            || blockItem.getBlock() == Blocks.GLASS
            || blockItem.getBlock() instanceof StainedGlassPaneBlock
            || blockItem.getBlock() == Blocks.GLASS_PANE;
   }

   public static boolean isMace(ItemStack stack) {
      DataComponentType<Component> nameComponent = DataComponents.CUSTOM_NAME;
      Component customName = stack.get(nameComponent);
      if (maceDisplayName.equals(customName)) {
         return true;
      }

      Item mace = Items.MACE;
      return stack.is(mace);
   }

   public static boolean isShulkerBox(Item item) {
      return item instanceof BlockItem blockItem ? blockItem.getBlock() instanceof ShulkerBoxBlock : false;
   }

   public static boolean isSword(ItemStack stack) {
      TagKey tag = ItemTags.SWORDS;
      return stack.is(tag);
   }

   public static boolean isPickaxe(ItemStack stack) {
      TagKey tag = ItemTags.PICKAXES;
      return stack.is(tag);
   }

   public static boolean isAxe(ItemStack stack) {
      TagKey tag = ItemTags.AXES;
      return stack.is(tag);
   }

   public static boolean isSmithingTemplate(Item item) {
      return item instanceof SmithingTemplateItem;
   }

   public static boolean providesTrimMaterial(Item item) {
      DataComponentMap components = item.components();
      DataComponentType<?> trimMaterialType = DataComponents.PROVIDES_TRIM_MATERIAL;
      return components.has(trimMaterialType);
   }

   public static boolean isFood(Item item) {
      DataComponentMap foodComponents = item.components();
      DataComponentType foodType = DataComponents.FOOD;
      if (foodComponents.has(foodType)) {
         DataComponentMap consumableComponents = item.components();
         DataComponentType consumableType = DataComponents.CONSUMABLE;
         if (consumableComponents.has(consumableType)) {
            return true;
         }
      }

      return false;
   }

   public static boolean isFood(ItemStack stack) {
      Item item = stack.getItem();
      return isFood(item);
   }

   public static int getFireworkDuration(ItemStack stack) {
      DataComponentType<Fireworks> fireworksComponent = DataComponents.FIREWORKS;
      Fireworks fireworks = stack.get(fireworksComponent);
      if (fireworks != null) {
         int flightDuration = 1 + fireworks.flightDuration();
         return flightDuration * 10 + 1;
      } else {
         return 1;
      }
   }

   private static Component createCompatibilityName(String name) {
      MutableComponent literal = Component.literal(name);
      Style nonItalicStyle = Style.EMPTY.withItalic(false);
      ChatFormatting color = ChatFormatting.WHITE;
      return literal.setStyle(nonItalicStyle.withColor(color));
   }

   static {
      Component woodenSpearName = createCompatibilityName("1.21.11 Wooden Spear");
      Component stoneSpearName = createCompatibilityName("1.21.11 Stone Spear");
      Component goldenSpearName = createCompatibilityName("1.21.11 Golden Spear");
      Component copperSpearName = createCompatibilityName("1.21.11 Copper Spear");
      Component diamondSpearName = createCompatibilityName("1.21.11 Diamond Spear");
      Component netheriteSpearName = createCompatibilityName("1.21.11 Netherite Spear");
      spearDisplayNames = Set.of(woodenSpearName, stoneSpearName, goldenSpearName, copperSpearName, diamondSpearName, netheriteSpearName);
   }
}
