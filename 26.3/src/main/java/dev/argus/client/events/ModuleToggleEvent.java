package dev.argus.client.events;

import dev.argus.client.modules.Module;

public final class ModuleToggleEvent {
   private final Module module;

   public ModuleToggleEvent(Module module) {
      this.module = module;
   }

   public Module getModule() {
      return this.module;
   }
}
