package dev.argus.client.utils.collections;

import dev.argus.client.utils.inventory.ItemRegistryGroupMatcher;
import dev.argus.client.utils.world.BlockRegistryGroupMatcher;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public interface RegistryGroupMatcher {
   List<RegistryGroupMatcher> MATCHERS = createDefaultMatchers();
   EmptyRegistryGroupMatcher EMPTY = new EmptyRegistryGroupMatcher();

   Identifier getRegistryId();

   Collection getGroupNames();

   Collection getGroupEntries(String var1);

   static RegistryGroupMatcher forRegistry(Registry registry) {
      for (RegistryGroupMatcher matcher : MATCHERS) {
         ResourceKey registryKey = registry.key();
         Identifier registryId = registryKey.identifier();
         if (registryId.equals(matcher.getRegistryId())) {
            return matcher;
         }
      }

      return EMPTY;
   }

   private static List createDefaultMatchers() {
      BlockRegistryGroupMatcher blocks = new BlockRegistryGroupMatcher();
      ItemRegistryGroupMatcher items = new ItemRegistryGroupMatcher();
      return List.of(blocks, items);
   }
}
