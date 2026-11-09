package dev.argus.client.modules.render;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import net.minecraft.client.player.LocalPlayer;

public class NoBobModule extends Module {
   public final Setting<Float> multiplierSetting = this.add(new NumberSetting<>("Multiplier", 0.0F, 0.0F, 1.0F));

   public NoBobModule() {
      super("NoBob", "Modifies the bobbing animation.", ModuleCategory.RENDER);
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (!this.isWorldUnavailable()) {
         Float multiplier = this.multiplierSetting.getValue();
         if (multiplier <= 0.0F) {
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            player.avatarState().walkDist = 0.0F;
         }
      }
   }
}
