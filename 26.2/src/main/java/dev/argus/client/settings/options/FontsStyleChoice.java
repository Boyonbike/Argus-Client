package dev.argus.client.settings.options;

import dev.argus.client.settings.NamedEntry;

public enum FontsStyleChoice implements NamedEntry {
   PLAIN("Plain"),
   BOLD("Bold"),
   ITALIC("Italic"),
   BOLDITALIC("BoldItalic");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   FontsStyleChoice(String displayName) {
      this.displayName = displayName;
   }
}
