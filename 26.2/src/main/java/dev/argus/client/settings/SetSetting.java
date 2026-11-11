package dev.argus.client.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public abstract class SetSetting<E> extends Setting<Set<E>> {
   private E lastChangedEntry;

   public Set<String> getFormattedValues() {
      ObjectOpenHashSet<String> formattedValues = new ObjectOpenHashSet();

      for (E entry : this.getValue()) {
         String formattedEntry = this.formatEntry(entry);
         formattedValues.add(formattedEntry);
      }

      return formattedValues;
   }

   @SafeVarargs
   public SetSetting(String name, E... defaultEntries) {
      super(name, new ObjectOpenHashSet());
      this.initializeEntries(defaultEntries);
   }

   @SafeVarargs
   public SetSetting(String name, Predicate<? super Set<E>> visibility, E... defaultEntries) {
      super(name, new ObjectOpenHashSet(), visibility);
      this.initializeEntries(defaultEntries);
   }

   private void initializeEntries(E... defaultEntries) {
      ObjectOpenHashSet<E> initialValues = new ObjectOpenHashSet();
      this.setValue(initialValues);
      this.getDefaultValue().addAll(List.of(defaultEntries));
      this.getValue().addAll(List.of(defaultEntries));
   }

   @Override
   public void reset() {
      Set<E> values = this.getValue();
      values.clear();
      Collection<E> defaults = this.getDefaultValue();
      values.addAll(defaults);
   }

   public abstract E parseEntry(String var1);

   public void removeEntry(E entry) {
      if (entry != null) {
         Set<E> values = this.getValue();
         values.remove(entry);
         this.lastChangedEntry = entry;
         this.runChangeCallback();
      }
   }

   public abstract Collection<E> getAvailableEntries();

   @Override
   public JsonElement toJson() {
      JsonArray json = new JsonArray();

      for (E entry : this.getValue()) {
         String formattedEntry = this.formatEntry(entry);
         json.add(formattedEntry);
      }

      return json;
   }

   public boolean containsEntry(Object entry) {
      if (entry == null) {
         return false;
      }

      Set<E> values = this.getValue();
      return values.contains(entry);
   }

   public void removeParsedEntry(String text) {
      if (text != null) {
         E entry = this.parseEntry(text);
         this.removeEntry(entry);
      }
   }

   public boolean containsParsedEntry(String text) {
      if (text == null) {
         return false;
      }

      E entry = this.parseEntry(text);
      return this.containsEntry(entry);
   }

   public abstract String formatEntry(E var1);

   @Override
   public void parseValue(String text) {
      E entry = this.parseEntry(text);
      if (entry == null) {
         throw new IllegalArgumentException();
      }

      if (this.containsEntry(entry)) {
         this.removeEntry(entry);
      } else {
         this.addEntry(entry);
      }
   }

   public void addEntry(E entry) {
      if (entry != null) {
         Set<E> values = this.getValue();
         values.add(entry);
         this.lastChangedEntry = entry;
         this.runChangeCallback();
      }
   }

   public Collection<String> getFormattedAvailableEntries() {
      HashSet<String> formattedEntries = new HashSet<>();

      for (E entry : this.getAvailableEntries()) {
         String formattedEntry = this.formatEntry(entry);
         formattedEntries.add(formattedEntry);
      }

      return formattedEntries;
   }

   public void addParsedEntry(String text) {
      if (text != null) {
         E entry = this.parseEntry(text);
         this.addEntry(entry);
      }
   }

   public E getLastChangedEntry() {
      return this.lastChangedEntry;
   }
}
