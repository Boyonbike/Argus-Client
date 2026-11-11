package dev.argus.client.settings.options;

import dev.argus.client.modules.Keybind;
import dev.argus.client.modules.misc.Macro;

public class SimpleMacro extends Macro {
   public SimpleMacro(String name, Keybind keybind) {
      super(name, MacroType.SIMPLE, keybind);
   }

   @Override
   public void run() {
      for (String command : this.commands) {
         Macro.executeCommands(command);
      }
   }
}
