package dev.argus.client;

import com.mojang.serialization.Lifecycle;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;

/** Bootstraps registries and binds item/block components, which 26.1.2 does lazily. */
public final class TestBootstrap {
   private TestBootstrap() {
   }

   public static void bootStrap() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
      HolderLookup.Provider builtins = HolderLookup.Provider.create(
         BuiltInRegistries.REGISTRY.stream().map(registry -> (HolderLookup.RegistryLookup<?>) registry));
      BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(new TestProvider(builtins)).forEach(pending -> pending.apply());
   }

   /** Supplies empty tags and stand-alone holders for data-pack registries tests do not load. */
   private static final class TestProvider implements HolderLookup.Provider {
      private final HolderLookup.Provider builtins;
      private final Map<ResourceKey<? extends Registry<?>>, HolderLookup.RegistryLookup<?>> dummies = new HashMap<>();

      private TestProvider(HolderLookup.Provider builtins) {
         this.builtins = builtins;
      }

      @Override
      public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
         return Stream.concat(builtins.listRegistryKeys(), dummies.keySet().stream()).distinct();
      }

      @Override
      @SuppressWarnings({"unchecked", "rawtypes"})
      public <T> Optional<? extends HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
         Optional<? extends HolderLookup.RegistryLookup<T>> found = builtins.lookup(key);
         if (found.isPresent()) {
            return found;
         }
         return Optional.of((HolderLookup.RegistryLookup<T>) dummies.computeIfAbsent(key, k -> new DummyLookup(k)));
      }
   }

   private static final class DummyLookup<T> implements HolderLookup.RegistryLookup<T> {
      private final ResourceKey<? extends Registry<? extends T>> key;

      private DummyLookup(ResourceKey<? extends Registry<? extends T>> key) {
         this.key = key;
      }

      @Override
      public ResourceKey<? extends Registry<? extends T>> key() {
         return key;
      }

      @Override
      public Lifecycle registryLifecycle() {
         return Lifecycle.stable();
      }

      @Override
      public Optional<Holder.Reference<T>> get(ResourceKey<T> key) {
         return Optional.of(Holder.Reference.createStandAlone(this, key));
      }

      @Override
      public Optional<HolderSet.Named<T>> get(TagKey<T> tag) {
         return Optional.of(HolderSet.emptyNamed(this, tag));
      }

      @Override
      public Stream<Holder.Reference<T>> listElements() {
         return Stream.empty();
      }

      @Override
      public Stream<HolderSet.Named<T>> listTags() {
         return Stream.empty();
      }
   }
}
