package dev.argus.client.settings.options;

import dev.argus.client.ClientAccess;
import dev.argus.client.modules.hud.WelcomerModule;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.chat.Component;

public enum WelcomerMode implements ClientAccess, NamedEntry {
   NAME("Name") {
      @Override
      public Component createText(WelcomerModule ignoredWelcomer) {
         Minecraft minecraft = ClientAccess.argusclientMinecraft;
         User user = minecraft.getUser();
         String username = user.getName();
         String greeting = "Hello " + username + " :')";
         return Component.literal(greeting);
      }
   },
   CUSTOM("Custom") {
      @Override
      public Component createText(WelcomerModule welcomer) {
         Setting<String> textSetting = welcomer.textSetting;
         String text = textSetting.getValue();
         Minecraft minecraft = ClientAccess.argusclientMinecraft;
         User user = minecraft.getUser();
         text = text.replace("{name}", user.getName());
         return Component.literal(text);
      }
   };

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   WelcomerMode(String displayName) {
      this.displayName = displayName;
   }

   public Component createText(WelcomerModule ignoredWelcomer) {
      return null;
   }
}
