package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum SocialRole implements NamedEntry {
   FRIEND("friend", "friends"),
   ENEMY("enemy", "enemies");

   private final String name;
   private final String pluralName;

   public String getPluralName() {
      return this.pluralName;
   }

   @Override
   public String getName() {
      return this.name;
   }

   SocialRole(String name, String pluralName) {
      this.name = name;
      this.pluralName = pluralName;
   }
}
