package dev.argus.client.gui.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;

final class RegistryEntryCatalog {
   private static final List<Block> BLOCK_ENTRIES = createBlockEntries();
   private static final List<Item> ITEM_ENTRIES = createItemEntries();
   private static final List<EntityType<?>> ENTITY_ENTRIES = createEntityEntries();
   private static final Map<Registry<?>, RegistryEntryCatalog.EntryList> ENTRIES_BY_REGISTRY = Map.of(
      BuiltInRegistries.BLOCK,
      RegistryEntryCatalog.EntryList.of(BLOCK_ENTRIES),
      BuiltInRegistries.ITEM,
      RegistryEntryCatalog.EntryList.of(ITEM_ENTRIES),
      BuiltInRegistries.ENTITY_TYPE,
      RegistryEntryCatalog.EntryList.of(ENTITY_ENTRIES)
   );

   private RegistryEntryCatalog() {
      throw new AssertionError();
   }

   public static RegistryEntryCatalog.EntryList entriesFor(Registry<?> registry) {
      return ENTRIES_BY_REGISTRY.getOrDefault(registry, RegistryEntryCatalog.EntryList.empty());
   }

   private static List<Block> createBlockEntries() {
      List<Block> specialBlocks = new ArrayList<>();
      specialBlocks.add(Blocks.OBSIDIAN);
      specialBlocks.add(Blocks.ENDER_CHEST);
      specialBlocks.add(Blocks.NETHER_PORTAL);
      specialBlocks.add(Blocks.END_PORTAL);
      List<Block> shulkerBoxes = new ArrayList<>();
      List<Block> ores = new ArrayList<>();
      List<Block> storageBlocks = new ArrayList<>();

      for (Block block : BuiltInRegistries.BLOCK) {
         if (block.getDescriptionId().contains("_ore")) {
            ores.add(block);
         }

         if (block instanceof ShulkerBoxBlock) {
            shulkerBoxes.add(block);
         }

         if (block instanceof ChestBlock || block instanceof HopperBlock || block instanceof BarrelBlock) {
            storageBlocks.add(block);
         }
      }

      return flatten(shulkerBoxes, ores, storageBlocks, specialBlocks);
   }

   private static List<Item> createItemEntries() {
      List<Item> items = new ArrayList<>();
      items.add(Items.ENCHANTED_GOLDEN_APPLE);
      items.add(Items.GOLDEN_APPLE);
      BLOCK_ENTRIES.stream().map(Block::asItem).filter(candidateItem -> candidateItem != Items.AIR).forEach(items::add);
      return Collections.unmodifiableList(items);
   }

   private static List<EntityType<?>> createEntityEntries() {
      return List.of(EntityType.LLAMA, EntityType.MULE, EntityType.DONKEY, EntityType.ITEM_FRAME, EntityType.ITEM, EntityType.ENDER_PEARL);
   }

   @SafeVarargs
   private static <T> List<T> flatten(List<? extends T>... categories) {
      List<T> entries = new ArrayList<>();

      for (List<? extends T> category : categories) {
         entries.addAll(category);
      }

      return Collections.unmodifiableList(entries);
   }

   public record EntryList(List<?> orderedEntries, Set<?> entrySet) {
      public static RegistryEntryCatalog.EntryList empty() {
         return of(Collections.emptyList());
      }

      public static RegistryEntryCatalog.EntryList of(List<?> entries) {
         return new RegistryEntryCatalog.EntryList(entries, Set.copyOf(entries));
      }

      public boolean contains(Object entry) {
         return this.entrySet.contains(entry);
      }
   }
}
