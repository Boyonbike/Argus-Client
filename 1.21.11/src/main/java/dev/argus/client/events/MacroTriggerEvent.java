package dev.argus.client.events;

import dev.argus.client.modules.misc.Macro;

public final class MacroTriggerEvent extends CancellableEvent {
   private final Macro macro;

   public MacroTriggerEvent(Macro macro) {
      this.macro = macro;
   }

   public Macro getMacro() {
      return this.macro;
   }
}
