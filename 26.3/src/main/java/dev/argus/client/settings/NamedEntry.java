package dev.argus.client.settings;

import net.minecraft.network.chat.Component;

public interface NamedEntry {
   String getName();

   default NamedEntry withName(String name) {
      return () -> name;
   }

   default Component getDisplayComponent() {
      return Component.literal(this.getName());
   }
}
