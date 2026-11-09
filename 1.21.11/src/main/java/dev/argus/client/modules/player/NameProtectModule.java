package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.StringSetting;
import java.util.Iterator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;

public class NameProtectModule extends Module {
   public static boolean replacementBypassed = false;
   public final Setting<String> nameSetting = this.add(new StringSetting("Name", "Me"));
   public final Setting<Boolean> othersSetting = this.add(new BooleanSetting("Others", false));
   public final Setting<Boolean> skinSetting = this.add(new BooleanSetting("Skin", true));
   public final Setting<Boolean> slimSetting = this.add(new BooleanSetting("Slim", false).dependsOn(this.skinSetting));

   public String protectNames(String text) {
      if (!replacementBypassed && text != null && !text.isEmpty()) {
         String nameToReplace = ClientAccess.argusclientMinecraft.getUser().getName();
         String protectedName = this.getProtectedName();
         text = this.replaceWholeName(text, nameToReplace, protectedName);
         if (this.othersSetting.getValue() && ClientAccess.argusclientMinecraft.level != null) {
            ClientLevel level = ClientAccess.argusclientMinecraft.level;
            Iterator<? extends AbstractClientPlayer> players = level.players().iterator();

            while (players.hasNext()) {
               nameToReplace = players.next().getGameProfile().name();
               text = this.replaceWholeName(text, nameToReplace, "redacted");
            }
         }

         return text;
      } else {
         return text;
      }
   }

   private String replaceWholeName(String text, String name, String replacement) {
      if (name != null && !name.isEmpty()) {
         StringBuilder result = new StringBuilder(text.length());
         int cursor = 0;
         int nameLength = name.length();

         while (cursor < text.length()) {
            int matchStart = text.indexOf(name, cursor);
            if (matchStart == -1) {
               result.append(text, cursor, text.length());
               break;
            }

            int matchEnd = matchStart + nameLength;
            boolean leftBoundary = matchStart == 0 || !Character.isLetterOrDigit(text.charAt(matchStart - 1));
            boolean rightBoundary = matchEnd == text.length() || !Character.isLetterOrDigit(text.charAt(matchEnd));
            if (leftBoundary && rightBoundary) {
               result.append(text, cursor, matchStart);
               result.append(replacement);
               cursor = matchEnd;
            } else {
               result.append(text, cursor, matchStart + 1);
               cursor = matchStart + 1;
            }
         }

         return result.toString();
      } else {
         return text;
      }
   }

   public String getProtectedName() {
      String name = this.nameSetting.getValue();
      return name.length() > 16 ? name.substring(0, 16) : name;
   }

   public NameProtectModule() {
      super("NameProtect", "Hides your nickname from the curious people.", ModuleCategory.PLAYER);
   }
}
