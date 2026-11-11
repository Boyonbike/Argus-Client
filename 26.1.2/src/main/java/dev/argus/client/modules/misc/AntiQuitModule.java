package dev.argus.client.modules.misc;

import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;

public class AntiQuitModule extends Module {
   public final Setting<Boolean> disconnectSetting = this.add(new BooleanSetting("Disconnect", false));
   public final Setting<Boolean> gameCloseSetting = this.add(new BooleanSetting("GameClose", false));

   public AntiQuitModule() {
      super("AntiQuit", "Prevents you from quitting the game/server accidentally.", ModuleCategory.MISC, "antidisconnect");
   }
}
