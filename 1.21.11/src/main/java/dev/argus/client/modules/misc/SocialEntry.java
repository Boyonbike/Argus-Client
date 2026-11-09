package dev.argus.client.modules.misc;

import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.options.SocialRole;

public record SocialEntry(String name, SocialRole role) implements NamedEntry {
   @Override
   public String getName() {
      return this.name;
   }
}
