package dev.argus.client.modules.client;

import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.entities.ActivityType;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.entities.StatusDisplayType;
import com.mojang.logging.LogUtils;
import dev.argus.client.ArgusClient;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftTickEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import org.slf4j.Logger;

public class DiscordModule extends Module {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final Setting<Boolean> serverSetting = this.add(new BooleanSetting("Server", false));
   private CompletableFuture<IPCClient> connectionFuture;
   private long startTimestamp = System.currentTimeMillis();
   private final ElapsedTimer updateTimer = new ElapsedTimer();

   public DiscordModule() {
      super("Discord", "Displays Argus Client as your Discord activity.", ModuleCategory.CLIENT);
      this.setDrawn(false);
   }

   @Override
   public void onEnable() {
      this.closeClient();
      CompletableFuture<IPCClient> pending = CompletableFuture.supplyAsync(() -> {
         IPCClient client = new IPCClient(1162833254580228176L);

         try {
            client.connect();
            return client;
         } catch (Exception failure) {
            try {
               client.close();
            } catch (Exception cleanup) {
               failure.addSuppressed(cleanup);
            }

            throw new CompletionException(failure);
         }
      });
      this.connectionFuture = pending;
      pending.whenComplete((client, failure) -> {
         if (failure != null) {
            LOGGER.warn("Discord connection failed: {}", failure.getClass().getSimpleName());
            ClientAccess.argusclientMinecraft.execute(() -> {
               if (this.connectionFuture == pending && this.isToggled()) {
                  this.toggle();
               }
            });
         }
      });
   }

   @Override
   public void onDisable() {
      this.closeClient();
   }

   @EventHandler
   private void onTick(MinecraftTickEvent event) {
      try {
         CompletableFuture<IPCClient> pendingConnection = this.connectionFuture;
         if (pendingConnection == null || !pendingConnection.isDone()) {
            return;
         }

         ElapsedTimer timerSnapshot = this.updateTimer;
         if (!timerSnapshot.hasElapsed(2500L)) {
            return;
         }

         timerSnapshot = this.updateTimer;
         timerSnapshot.reset();
         IPCClient ipcClient = this.connectionFuture.getNow(null);
         if (ipcClient == null) {
            return;
         }

         RichPresence.Builder presenceBuilder = new RichPresence.Builder().setName(ArgusClient.NAME);
         long timestamp = this.startTimestamp;
         presenceBuilder.setStartTimestamp(timestamp);
         ActivityType activityType = ActivityType.Playing;
         presenceBuilder.setActivityType(activityType);
         StatusDisplayType displayType = StatusDisplayType.Name;
         presenceBuilder.setStatusDisplayType(displayType);
         Setting<Boolean> serverVisibilitySetting = this.serverSetting;
         Boolean showServer = serverVisibilitySetting.getValue();
         if (showServer) {
            String serverDetails = "Not playing";
            if (!this.isWorldUnavailable()) {
               ClientPacketListener connection = ClientAccess.argusclientMinecraft.player.connection;
               ServerData serverData = connection.getServerData();
               Minecraft minecraft = ClientAccess.argusclientMinecraft;
               String serverLocation;
               if (!minecraft.isLocalServer() && serverData != null) {
                  String serverAddress = serverData.ip;
                  serverLocation = "on " + serverAddress;
               } else {
                  serverLocation = "singleplayer";
               }

               serverDetails = "Playing " + serverLocation;
            }

            presenceBuilder.setDetails(serverDetails);
         }

         RichPresence presence = presenceBuilder.build();
         ipcClient.sendRichPresence(presence);
      } catch (Exception var18) {
      }
   }

   public void closeClient() {
      CompletableFuture<IPCClient> pending = this.connectionFuture;
      this.connectionFuture = null;
      if (pending != null) {
         pending.thenAccept(client -> {
            if (client != null) {
               try {
                  client.sendRichPresence(null);
               } catch (Exception failure) {
                  LOGGER.debug("Could not clear Discord activity", failure);
               } finally {
                  try {
                     client.close();
                  } catch (Exception failure) {
                     LOGGER.debug("Could not close Discord connection", failure);
                  }
               }
            }
         });
      }
   }
}
