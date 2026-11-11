package dev.argus.client.utils.inventory;

import dev.argus.client.utils.collections.NamedRegistryEntry;
import dev.argus.client.utils.collections.RegistryGroupMatcher;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ItemRegistryGroupMatcher implements RegistryGroupMatcher {
   @Override
   public Identifier getRegistryId() {
      return Registries.ITEM.identifier();
   }

   @Override
   public Collection getGroupNames() {
      Registry blockRegistry = BuiltInRegistries.BLOCK;
      return RegistryGroupMatcher.forRegistry(blockRegistry).getGroupNames();
   }

   @Override
   public Collection getGroupEntries(String groupName) {
      Registry blockRegistry = BuiltInRegistries.BLOCK;
      Collection blockEntries = RegistryGroupMatcher.forRegistry(blockRegistry).getGroupEntries(groupName);
      if (!blockEntries.isEmpty()) {
         Stream blockStream = blockEntries.stream();
         Function<NamedRegistryEntry, NamedRegistryEntry> toItemEntry = ItemRegistryGroupMatcher::asItemEntry;
         return blockStream.map(toItemEntry).toList();
      } else {
         return Collections.emptyList();
      }
   }

   private static NamedRegistryEntry asItemEntry(NamedRegistryEntry entry) {
      return new NamedRegistryEntry(entry.name(), itemForEntry(entry));
   }

   private static Item itemForEntry(NamedRegistryEntry entry) {
      return ((Block)entry.value()).asItem();
   }
}
