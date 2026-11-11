package dev.argus.client.settings.options;

import dev.argus.client.modules.Keybind;
import dev.argus.client.modules.misc.Macro;
import dev.argus.client.utils.ElapsedTimer;

public class DoubleTapMacro extends Macro {
   private final ElapsedTimer tapTimer = new ElapsedTimer();

   public DoubleTapMacro(String name, Keybind keybind) {
      super(name, MacroType.DOUBLE_TAP, keybind);
   }

   @Override
   public void run() {
      if (this.tapTimer.hasElapsed(300L)) {
         this.tapTimer.reset();
      } else {
         for (String command : this.commands) {
            Macro.executeCommands(command);
         }

         this.tapTimer.clear();
      }
   }
}
