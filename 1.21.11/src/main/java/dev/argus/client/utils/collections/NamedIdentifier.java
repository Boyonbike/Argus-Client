package dev.argus.client.utils.collections;

import net.minecraft.resources.Identifier;

public final class NamedIdentifier extends Identifier {
   public static final NamedIdentifier NONE = new NamedIdentifier("none");
   private final String name;
   private final String category;

   public NamedIdentifier(String category, String name) {
      super(namespaceForCategory(category), "sounds/" + category + "/" + name + ".ogg");
      this.name = name;
      this.category = category;
   }

   public NamedIdentifier(String name) {
      this("system", name);
   }

   private static String namespaceForCategory(String category) {
      return category.equalsIgnoreCase("system") ? "argusclient" : "argusclient-mount";
   }

   public String getCategory() {
      return this.category;
   }

   public String getName() {
      return this.name;
   }
}
