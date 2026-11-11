package dev.argus.client.modules.hud;

import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.WelcomerMode;
import java.awt.Color;
import net.minecraft.network.chat.Component;

public final class GreetingNotification extends HudNotification {
   private final WelcomerModule welcomer;

   @Override
   public Color getColor() {
      WelcomerModule colorOwner = this.welcomer;
      WelcomerModule positionOwner = this.welcomer;
      return colorOwner.getHudColor(positionOwner.getRenderer().getY());
   }

   @Override
   public void update() {
      if (this.welcomer != null) {
         Setting<WelcomerMode> modeSetting = this.welcomer.modeSetting;
         WelcomerMode modeValue = modeSetting.getValue();
         WelcomerModule textOwner = this.welcomer;
         Component greeting = modeValue.createText(textOwner);
         super.text = greeting.getString();
      }
   }

   public GreetingNotification(WelcomerModule welcomer) {
      super("", 604800000L, null);
      this.welcomer = welcomer;
   }
}
