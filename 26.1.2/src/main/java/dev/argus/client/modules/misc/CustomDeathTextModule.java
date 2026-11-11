package dev.argus.client.modules.misc;

import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.StringSetting;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;

public class CustomDeathTextModule extends Module {
   final Setting<CustomDeathTextModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", CustomDeathTextModule.ModeChoice.CUSTOM));
   private final Setting<String> messageSetting;
   private static final String[] randomMessages;

   public Component getDeathText() {
      Setting<CustomDeathTextModule.ModeChoice> modeSettingSnapshot = this.modeSetting;
      if (modeSettingSnapshot.getValue() == CustomDeathTextModule.ModeChoice.CUSTOM) {
         Setting<String> messageSettingSnapshot = this.messageSetting;
         String message = messageSettingSnapshot.getValue();
         String trimmedMessage = message.trim();
         return Component.nullToEmpty(trimmedMessage);
      } else {
         String[] messagesSnapshot = randomMessages;
         ThreadLocalRandom randomSnapshot = ThreadLocalRandom.current();
         int messageCount = randomMessages.length;
         String randomMessage = messagesSnapshot[randomSnapshot.nextInt(messageCount)];
         return Component.literal(randomMessage);
      }
   }

   public CustomDeathTextModule() {
      super("CustomDeathText", "Displays a custom message on your death screen.", ModuleCategory.MISC);
      Predicate<Object> customMode = ignored -> this.modeSetting.getValue() == CustomDeathTextModule.ModeChoice.CUSTOM;
      this.messageSetting = this.add(new StringSetting("Message", "Uh oh.", customMode));
      this.setDrawn(false);
   }

   static {
      String[] messages = new String[]{
         "YOU USELESS SHITSTAIN",
         "GAME OVER MOTHERFUCKER! YOU SUCK!",
         "HOW THE HELL ARE YOU SO BAD AT THIS GAME?",
         "HOW DO YOU FUCK UP SO BADLY JESUS FUCKING CHRIST?",
         "YOU DIED! CONGRATS FUCKFACE",
         "OH MY GOD YOU SUCK! AUTO-UNINSTALLING",
         "HOLY FUCKING SHIT SERIOUSLY",
         "YOU FUCKING RETARD!",
         "YOU DENSE FUCK"
      };
      randomMessages = messages;
   }

   public enum ModeChoice implements NamedEntry {
      CUSTOM("Custom"),
      RANDOM("Random");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
