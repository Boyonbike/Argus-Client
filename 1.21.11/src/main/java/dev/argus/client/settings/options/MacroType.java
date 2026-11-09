package dev.argus.client.settings.options;

import dev.argus.client.modules.Keybind;
import dev.argus.client.modules.misc.HoldMacro;
import dev.argus.client.modules.misc.Macro;
import dev.argus.client.settings.NamedEntry;
import java.util.function.BiFunction;

public enum MacroType implements NamedEntry {
   SIMPLE("simple", SimpleMacro::new),
   QUEUE("queue", QueuedMacro::new),
   DOUBLE_TAP("double_tap", DoubleTapMacro::new),
   HOLD("hold", HoldMacro::new);

   private final String name;
   private final BiFunction<String, Keybind, Macro> factory;

   @Override
   public String getName() {
      return this.name;
   }

   MacroType(String name, BiFunction<String, Keybind, Macro> factory) {
      this.name = name;
      this.factory = factory;
   }

   public Macro create(String name, Keybind keybind) {
      return this.factory.apply(name, keybind);
   }

   public static MacroType fromName(String name) {
      for (MacroType type : values()) {
         String typeName = type.getName();
         if (typeName.equalsIgnoreCase(name)) {
            return type;
         }
      }

      return null;
   }
}
