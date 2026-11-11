package dev.argus.client.utils.inventory;

import com.mojang.serialization.Lifecycle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class CustomItemRegistries {
   private static final List<CustomItemRegistries.RegistryInstaller> registryInstallers = new ArrayList<>();
   public static final DefaultedRegistry foodItems = createFilteredItemRegistry("food", ItemUtils::isFood);
   public static final DefaultedRegistry smithingTemplates = createFilteredItemRegistry("templates", ItemUtils::isSmithingTemplate);
   public static final DefaultedRegistry trimMaterials = createFilteredItemRegistry("trim_materials", ItemUtils::providesTrimMaterial);

   private CustomItemRegistries() {
   }

   public static void initialize() {
      registryInstallers.forEach(CustomItemRegistries.RegistryInstaller::installContents);
   }

   private static DefaultedRegistry createDeferredRegistry(String path, String defaultEntry, Consumer<WritableRegistry<Item>> installer) {
      Identifier registryId = Identifier.fromNamespaceAndPath("argusclient", path);
      ResourceKey registryKey = ResourceKey.createRegistryKey(registryId);
      DefaultedMappedRegistry registry = new DefaultedMappedRegistry(defaultEntry, registryKey, Lifecycle.stable(), false);
      registryInstallers.add(new CustomItemRegistries.RegistryInstaller(registry, installer));
      return registry;
   }

   private static DefaultedRegistry createFilteredItemRegistry(String path, Predicate<Item> itemFilter) {
      return createDeferredRegistry(
         path,
         "air",
         targetRegistry -> BuiltInRegistries.ITEM
            .listElements()
            .filter(candidateHolder -> itemFilter.test(candidateHolder.value()))
            .forEach(matchingHolder -> {
               ResourceKey itemKey = matchingHolder.key();
               targetRegistry.register(itemKey, matchingHolder.value(), RegistrationInfo.BUILT_IN);
            })
      );
   }

   record RegistryInstaller(WritableRegistry registry, Consumer<WritableRegistry<Item>> installer) {
      public Consumer<WritableRegistry<Item>> getInstaller() {
         return this.installer;
      }

      public void installContents() {
         this.installer.accept(this.registry);
         // 26.1.2 MappedRegistry binds holder values only on freeze(); without it every lookup throws "unbound value".
         this.registry.freeze();
      }

      public WritableRegistry getRegistry() {
         return this.registry;
      }
   }
}
