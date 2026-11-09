package dev.argus.client.settings.options;

import dev.argus.client.modules.Keybind;
import dev.argus.client.modules.misc.Macro;

public class QueuedMacro extends Macro {
   private int nextCommandIndex = 0;

   public QueuedMacro(String name, Keybind keybind) {
      super(name, MacroType.QUEUE, keybind);
   }

   @Override
   public void run() {
      if (!this.commands.isEmpty()) {
         this.nextCommandIndex = this.nextCommandIndex % this.commands.size();
         String commandGroup = (String)this.commands.get(this.nextCommandIndex);
         String[] commands = commandGroup.split(";");

         for (String command : commands) {
            Macro.executeCommands(command);
         }

         this.nextCommandIndex++;
      }
   }
}
