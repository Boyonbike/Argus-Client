package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.Setting;
import java.util.Collection;
import java.util.Objects;

public enum ListFilterMode implements NamedEntry {
   ANY("Any"),
   WHITELIST("WhiteList"),
   BLACKLIST("BlackList");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   ListFilterMode(String displayName) {
      this.displayName = displayName;
   }

   public static boolean matches(ListFilterMode mode, Object value, Setting<?> setting) {
      Objects.requireNonNull(mode);
      Setting<?> collectionSetting = setting;
      return matches(mode, value, (Collection)collectionSetting.getValue());
   }

   public static boolean matches(ListFilterMode mode, Object value, Collection entries) {
      return switch (mode) {
         case ANY -> true;
         case WHITELIST -> entries.contains(value);
         case BLACKLIST -> !entries.contains(value);
      };
   }

   public boolean isFiltering() {
      return !this.isAny();
   }

   public boolean isAny() {
      return this == ANY;
   }
}
