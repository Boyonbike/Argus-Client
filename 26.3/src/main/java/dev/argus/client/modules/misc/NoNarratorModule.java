package dev.argus.client.modules.misc;

import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;

public class NoNarratorModule extends Module {
   public NoNarratorModule() {
      super("NoNarrator", "Disables the narrator feature.", ModuleCategory.MISC);
      this.setDrawn(false);
   }
}
