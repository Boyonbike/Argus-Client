package dev.argus.client.modules;

import dev.argus.client.settings.NamedEntry;

public enum ModuleCategory implements NamedEntry {
   COMBAT("Combat"),
   MISC("Misc"),
   RENDER("Render"),
   MOVEMENT("Movement"),
   PLAYER("Player"),
   EXPLOIT("Exploit"),
   BASE_HUNTING("Base Hunting"),
   CLIENT("Client"),
   HUD("HUD");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   ModuleCategory(String displayName) {
      this.displayName = displayName;
   }
}
