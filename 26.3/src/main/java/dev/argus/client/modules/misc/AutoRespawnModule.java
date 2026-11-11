package dev.argus.client.modules.misc;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.TaskScheduler;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.StringSetting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.chat.ChatUtils;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;

public class AutoRespawnModule extends Module {
   private final ElapsedTimer respawnTimer = new ElapsedTimer();
   private final Setting<Boolean> saySetting;
   private final Setting<String> messageSetting;
   private volatile ClientPacketListener deathConnection;

   @Override
   public void onToggle() {
      this.deathConnection = null;
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (ClientAccess.argusclientMinecraft.gui.screen() instanceof DeathScreen) {
         ElapsedTimer timer = this.respawnTimer;
         if (timer.hasElapsed(100L)) {
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            this.deathConnection = player.connection;
            player.respawn();
            this.respawnTimer.reset();
         }
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (player == null) {
         return;
      }
      if (event.getPacket() instanceof ClientboundPlayerCombatKillPacket death && death.playerId() == player.getId()) {
         this.deathConnection = player.connection;
         this.respawnTimer.reset();
      } else if (event.getPacket() instanceof ClientboundRespawnPacket respawn
         && !respawn.shouldKeep(ClientboundRespawnPacket.KEEP_ENTITY_DATA) && this.deathConnection == player.connection) {
         this.deathConnection = null;
         if (this.saySetting.getValue()) {
            ClientPacketListener connection = player.connection;
            ClientContext.taskScheduler.scheduleRotationTask(() -> {
               if (this.isToggled() && this.saySetting.getValue()
                  && ClientAccess.argusclientMinecraft.getConnection() == connection) {
                  ChatUtils.sendChatOrCommand(this.messageSetting.getValue());
               }
            }, 2);
         }
      }
   }

   public AutoRespawnModule() {
      super("AutoRespawn", "Respawns automatically.", ModuleCategory.MISC);
      Boolean sayDefault = false;
      BooleanSetting say = new BooleanSetting("Say", sayDefault);
      say.markVisibilityDependency();
      this.saySetting = this.add(say);
      StringSetting message = new StringSetting("Message", "/kit cattyn");
      Setting<?>[] dependencies = new Setting[]{this.saySetting};
      this.messageSetting = this.add(message.dependsOn(dependencies));
      this.setDrawn(false);
   }
}
