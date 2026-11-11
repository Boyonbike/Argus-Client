package dev.argus.client.modules.misc;

import dev.argus.client.ClientAccess;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import org.slf4j.Logger;

public class NoPacketKickModule extends Module {
   public final Setting<Boolean> messageSetting = this.add(new BooleanSetting("Message", false));

   public NoPacketKickModule() {
      super("NoPacketKick", "Cancels broken packets that may cause you getting kicked.", ModuleCategory.MISC);
      this.setDrawn(false);
   }

   public void logInterceptedException(Throwable exception) {
      if (this.messageSetting.getValue()) {
         try {
            Logger logger = ClientAccess.logger;
            String message = exception.getLocalizedMessage();
            logger.warn("Intercepted exception: {}", message);
         } catch (Throwable var4) {
         }
      }
   }
}
