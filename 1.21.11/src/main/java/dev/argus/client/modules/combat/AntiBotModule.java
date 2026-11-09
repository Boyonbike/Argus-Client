package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.commands.FakeplayerCommand;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.player.Player;

public class AntiBotModule extends Module {
   private final Setting<Boolean> illegalCharsSetting = this.add(new BooleanSetting("IllegalChars", false));
   private final Setting<Boolean> illegalNamesSetting = this.add(new BooleanSetting("IllegalNames", false));

   @EventHandler
   private void onPlayerTick(LocalPlayerTickEvent event) {
      Player lastBot = null;

      for (Player candidate : ClientAccess.argusclientMinecraft.level.players()) {
         if (this.isBot(candidate)) {
            lastBot = candidate;
         }
      }

      if (lastBot != null) {
         RemovalReason removalReason = RemovalReason.KILLED;
         lastBot.setRemoved(removalReason);
      }
   }

   private boolean isBot(Player candidate) {
      if (ClientAccess.argusclientMinecraft.player != candidate && !(candidate instanceof FakeplayerCommand.SimulatedPlayer)) {
         String name = candidate.getGameProfile().name();
         if (name == null) {
            return true;
         }

         int nameLength = name.length();
         if (this.illegalNamesSetting.getValue()) {
            if (nameLength < 3) {
               return true;
            }

            if (nameLength > 16) {
               return true;
            }
         }

         if (this.illegalCharsSetting.getValue()) {
            for (int characterIndex = 0; characterIndex < nameLength; characterIndex++) {
               char character = name.charAt(characterIndex);
               char lowercaseCharacter = Character.toLowerCase(character);
               if ((lowercaseCharacter < '0' || lowercaseCharacter > '9') && lowercaseCharacter != '_') {
                  if (lowercaseCharacter < 'a') {
                     return true;
                  }

                  if (lowercaseCharacter > 'z') {
                     return true;
                  }
               }
            }
         }

         ClientPacketListener connectionSnapshot = ClientAccess.argusclientMinecraft.player.connection;
         UUID profileId = candidate.getGameProfile().id();
         PlayerInfo playerInfo = connectionSnapshot.getPlayerInfo(profileId);
         return playerInfo == null || playerInfo.getProfile() == null && candidate.hasCustomName();
      } else {
         return false;
      }
   }

   public AntiBotModule() {
      super("AntiBot", "Removes the bots that will be spawned by some anti cheats.", ModuleCategory.COMBAT);
   }
}
