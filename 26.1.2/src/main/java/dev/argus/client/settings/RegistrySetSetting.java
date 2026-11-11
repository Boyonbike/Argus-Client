package dev.argus.client.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder.Reference;
import net.minecraft.resources.Identifier;

public final class RegistrySetSetting<E> extends SetSetting<E> {
   /** Settings whose saved entries arrived before their (deferred) registry was populated. */
   private static final List<RegistrySetSetting<?>> PENDING = Collections.synchronizedList(new ArrayList<>());
   private final Registry<E> registry;
   /**
    * Saved entries kept verbatim while {@link #registry} is still empty (e.g. the AutoEat food registry, which on
    * 26.1.2 can only be filled once item components bind on world join). Written back unchanged by {@link #toJson()}
    * so a save before then cannot drop them; cleared by any explicit edit.
    */
   private JsonArray pendingJson;

   @SafeVarargs
   public RegistrySetSetting(String name, Registry<E> entryRegistry, E... defaultEntries) {
      super(name, defaultEntries);
      this.registry = entryRegistry;
   }

   @SafeVarargs
   public RegistrySetSetting(String name, Registry<E> entryRegistry, Predicate<? super Set<E>> visibility, E... defaultEntries) {
      super(name, visibility, defaultEntries);
      this.registry = entryRegistry;
   }

   @Override
   public E parseEntry(String text) {
      Registry<E> lookupRegistry = this.registry;
      Identifier identifier = Identifier.parse(text);
      Optional<Reference<E>> holder = lookupRegistry.get(identifier);
      return holder.map(Reference::value).orElse(null);
   }

   @Override
   public Collection<E> getAvailableEntries() {
      return this.registry.stream().toList();
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json.isJsonArray()) {
         if (this.registry.size() == 0) {
            this.pendingJson = json.getAsJsonArray().deepCopy();
            PENDING.add(this);
            return;
         }

         this.pendingJson = null;
         this.getValue().clear();

         for (JsonElement element : json.getAsJsonArray()) {
            Registry<E> lookupRegistry = this.registry;
            String identifierText = element.getAsString();
            Identifier identifier = Identifier.parse(identifierText);
            // Optional lookup: unknown ids are dropped instead of resolving to a default the registry may not hold.
            lookupRegistry.get(identifier).map(Reference::value).ifPresent(this.getValue()::add);
         }
      }
   }

   @Override
   public JsonElement toJson() {
      return this.pendingJson != null ? this.pendingJson.deepCopy() : super.toJson();
   }

   @Override
   public void reset() {
      this.pendingJson = null;
      super.reset();
   }

   @Override
   public void addEntry(E entry) {
      this.pendingJson = null;
      super.addEntry(entry);
   }

   @Override
   public void removeEntry(E entry) {
      this.pendingJson = null;
      super.removeEntry(entry);
   }

   /** Applies entries that were loaded before their registry was populated. Call once deferred registries install. */
   public static void resolvePending() {
      List<RegistrySetSetting<?>> snapshot;
      synchronized (PENDING) {
         snapshot = new ArrayList<>(PENDING);
         PENDING.clear();
      }

      for (RegistrySetSetting<?> setting : snapshot) {
         JsonArray saved = setting.pendingJson;
         if (saved != null) {
            setting.pendingJson = null;
            setting.fromJson(saved);
         }
      }
   }

   public void modifyEntries(Consumer<Set<E>> editor) {
      this.pendingJson = null;
      editor.accept(this.getValue());
      this.runChangeCallback();
   }

   @Override
   public String formatEntry(E entry) {
      Registry<E> entryRegistry = this.registry;
      Identifier identifier = entryRegistry.getKey(entry);
      return identifier == null ? null : identifier.toShortLanguageKey();
   }

   public Registry<E> getRegistry() {
      return this.registry;
   }
}
