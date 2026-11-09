package dev.argus.client.modules.movement;

import dev.argus.client.settings.Setting;
import java.util.HashMap;
import java.util.Objects;

public class EnumSelectionMap<T> {
   private final HashMap<Enum<?>, T> valuesByChoice = new HashMap<>();
   private final Setting<? extends Enum<?>> choiceSetting;

   public EnumSelectionMap(Setting<? extends Enum<?>> choiceSetting) {
      this.choiceSetting = Objects.requireNonNull(choiceSetting);
   }

   public void register(Enum<?> choice, T value) {
      this.valuesByChoice.put(choice, value);
   }

   public T getSelected() {
      return this.valuesByChoice.get(this.choiceSetting.getValue());
   }
}
