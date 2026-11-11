package dev.argus.client.managers;

import dev.argus.client.utils.inventory.RegistryContainer;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;

public abstract class AbstractRegistry<T> implements RegistryContainer {
   protected final Collection<T> entries;

   protected AbstractRegistry(Collection<T> entries) {
      this.entries = entries;
   }

   @Override
   public Collection<T> getRegistry() {
      return this.entries;
   }

   public Optional<T> findFirst(Predicate<? super T> predicate) {
      for (T entry : this.entries) {
         if (predicate.test(entry)) {
            return Optional.of(entry);
         }
      }

      return Optional.empty();
   }

   public T findFirstOrNull(Predicate<? super T> predicate) {
      for (T entry : this.entries) {
         if (predicate.test(entry)) {
            return entry;
         }
      }

      return null;
   }
}
