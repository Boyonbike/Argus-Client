package dev.argus.client.modules.movement;

import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;

public class NoJumpDelayModule extends Module {
   public NoJumpDelayModule() {
      super("NoJumpDelay", "Removes the jumping delay when sprint-jumping.", ModuleCategory.MOVEMENT);
      this.setDrawn(false);
   }
}
