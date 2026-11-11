package dev.argus.client.modules.misc;

import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;

public class AutoReconnectModule extends Module {
   public final Setting<Integer> minutesSetting = this.add(new NumberSetting<>("Minutes", 0, 0, 15).withUnit("m"));
   public final Setting<Float> secondsSetting = this.add(new NumberSetting<>("Seconds", 5.0F, 0.0F, 59.0F).withUnit("s"));

   public AutoReconnectModule() {
      super("AutoReconnect", "Reconnects to the server automatically if you get kicked.", ModuleCategory.MISC);
      this.secondsSetting.setConfigName("Delay");
      this.setDrawn(false);
   }
}
