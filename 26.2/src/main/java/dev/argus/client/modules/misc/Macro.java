package dev.argus.client.modules.misc;

import dev.argus.client.NamedFeature;
import dev.argus.client.managers.CommandRegistry;
import dev.argus.client.modules.Keybind;
import dev.argus.client.settings.options.MacroType;
import java.util.ArrayList;
import java.util.List;

public abstract class Macro extends NamedFeature {
   protected final List<String> commands = new ArrayList();
   private final MacroType type;
   private final Keybind keybind;
   private boolean toggled;

   public Macro(String name, MacroType type, Keybind keybind) {
      super(name);
      this.type = type;
      this.keybind = keybind;
      this.toggled = true;
   }

   public abstract void run();

   public boolean isToggled() {
      return this.toggled;
   }

   public Keybind getKeybind() {
      return this.keybind;
   }

   public static void executeCommands(String commandGroup) {
      for (String command : commandGroup.split(";")) {
         CommandRegistry.executeCommand(command.trim());
      }
   }

   public List<String> getCommands() {
      return this.commands;
   }

   public void setToggled(boolean toggled) {
      this.toggled = toggled;
   }

   public MacroType getType() {
      return this.type;
   }
}
