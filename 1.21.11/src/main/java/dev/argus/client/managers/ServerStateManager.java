package dev.argus.client.managers;

import com.mojang.authlib.GameProfile;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.ConnectScreenConnectEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.modules.exploit.FastLatencyModule;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.world.WorldUtils;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;

public final class ServerStateManager implements ClientAccess {
   private static FastLatencyModule fastLatency = ClientContext.getModule(FastLatencyModule.class);
   private static final Pattern LATENCY_PATTERN = Pattern.compile("\\d+ ping");
   private final ArrayDeque<Float> tickRateSamples = new ArrayDeque<>(20);
   private final ElapsedTimer setbackTimer = new ElapsedTimer();
   private final ElapsedTimer teleportAckTimer = new ElapsedTimer();
   private final ElapsedTimer packetFlowTimer = new ElapsedTimer();
   private final ElapsedTimer peakLatencyTimer = new ElapsedTimer();
   private float latestTickRate;
   private float averageTickRate;
   private long lastPacketMillis;
   private long lastEntityPacketMillis;
   private long lastTimeUpdateMillis;
   private int teleportId;
   private boolean serverSprinting;
   private boolean serverSneaking;
   private boolean inEnd;
   private ServerData serverData;
   private int footerLatencyMillis;
   private int peakLatencyMillis;
   public boolean autoLogged;

   public boolean isServerSneaking() {
      return this.serverSneaking;
   }

   public int getTeleportId() {
      return this.teleportId;
   }

   public ElapsedTimer getTeleportAckTimer() {
      return this.teleportAckTimer;
   }

   public float getTickRate() {
      return this.averageTickRate;
   }

   public long getLastPacketTime() {
      return this.getLastPacketTime(ServerStateManager.PacketActivity.ANY);
   }

   public boolean isServerSprinting() {
      return this.serverSprinting;
   }

   public int getPlayerLatencyMillis(Player player) {
      ClientPacketListener capturedConnection = ClientAccess.argusclientMinecraft.player.connection;
      GameProfile profile = player.getGameProfile();
      UUID profileId = profile.id();
      PlayerInfo playerInfo = capturedConnection.getPlayerInfo(profileId);
      return playerInfo == null ? 0 : playerInfo.getLatency();
   }

   public float getLatestTickRate() {
      return this.latestTickRate;
   }

   public ElapsedTimer getSetbackTimer() {
      return this.setbackTimer;
   }

   public int getLatencyMillis() {
      if (ClientAccess.argusclientMinecraft.player == null) {
         return 0;
      }

      FastLatencyModule fastLatencyModule = fastLatency;
      if (fastLatencyModule.isToggled()) {
         return fastLatency.getLatencyMillis();
      }

      LocalPlayer latencyPlayer = ClientAccess.argusclientMinecraft.player;
      int playerLatency = this.getPlayerLatencyMillis(latencyPlayer);
      return playerLatency == 0 ? this.footerLatencyMillis : playerLatency;
   }

   public int getLatencyTicks() {
      return (int)Math.ceil(this.getLatencyMillis() / 50.0F);
   }

   public int getPeakLatencyMillis() {
      return this.peakLatencyMillis;
   }

   public ServerData getServerData() {
      return this.serverData;
   }

   public ServerStateManager() {
      ClientAccess.eventBus.subscribe(this);
   }

   public boolean hasElapsedSinceSetback(long durationMillis) {
      ElapsedTimer timer = this.getSetbackTimer();
      return timer.hasElapsed(durationMillis);
   }

   public void setServerData(ServerData data) {
      this.serverData = data;
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      long packetAgeMillis = System.currentTimeMillis() - this.lastPacketMillis;
      if (packetAgeMillis > 500L) {
         this.packetFlowTimer.reset();
      }

      int latencyMillis = this.getLatencyMillis();
      if (this.peakLatencyTimer.hasElapsed(10.0, TimeUnit.SECONDS) || latencyMillis > this.peakLatencyMillis) {
         this.peakLatencyMillis = latencyMillis;
         this.peakLatencyTimer.reset();
      }

      DimensionType dimension = WorldUtils.getDimension();
      this.inEnd = dimension.isEnd();
   }

   public long getLastPacketTime(ServerStateManager.PacketActivity activity) {
      return switch (activity) {
         case ANY -> this.lastPacketMillis;
         case ENTITY -> this.lastEntityPacketMillis;
      };
   }

   @EventHandler(priority = 200)
   private void onConnectionSendPacketEvent(PacketEvent.Send event) {
      if (event.getPacket() instanceof ServerboundPlayerCommandPacket commandPacket) {
         if (commandPacket.getAction() == Action.START_SPRINTING) {
            if (this.serverSprinting) {
               event.cancel();
            }

            this.serverSprinting = true;
         }

         if (commandPacket.getAction() == Action.STOP_SPRINTING) {
            if (!this.serverSprinting) {
               event.cancel();
            }

            this.serverSprinting = false;
         }
      }

      if (event.getPacket() instanceof ServerboundPlayerInputPacket inputPacket) {
         Input extractedInput;
         try {
            extractedInput = inputPacket.input();
         } catch (Throwable inputFailure) {
            throw new MatchException(inputFailure.toString(), inputFailure);
         }

         this.serverSneaking = extractedInput.shift();
      }

      if (event.getPacket() instanceof ServerboundAcceptTeleportationPacket) {
         this.teleportAckTimer.reset();
      }
   }

   public boolean hasStablePacketFlowFor(long durationMillis) {
      return this.packetFlowTimer.hasElapsed(durationMillis);
   }

   @EventHandler
   private void onConnectScreenConnectEvent(ConnectScreenConnectEvent event) {
      this.autoLogged = false;
      this.footerLatencyMillis = 0;
      this.peakLatencyMillis = 0;
      this.peakLatencyTimer.setTime(-1L);
   }

   private void recordReceivedPacket(Packet packet) {
      if (packet instanceof ClientboundMoveEntityPacket || packet instanceof ClientboundRotateHeadPacket || this.inEnd) {
         this.lastEntityPacketMillis = System.currentTimeMillis();
      }

      this.lastPacketMillis = System.currentTimeMillis();
   }

   public float getTickRateScale() {
      return this.averageTickRate / 20.0F;
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      Packet receivedPacket = event.getPacket();
      this.recordReceivedPacket(receivedPacket);
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket positionPacket) {
         this.teleportId = positionPacket.id();
         ElapsedTimer ackTimer = this.teleportAckTimer;
         if (ackTimer.hasElapsed(50L)) {
            this.setbackTimer.reset();
         }

         LocalPlayer sprintPlayer = ClientAccess.argusclientMinecraft.player;
         this.serverSprinting = sprintPlayer.isSprinting();
      }

      if (event.getPacket() instanceof ClientboundSetTimePacket) {
         if (this.lastTimeUpdateMillis != 0L) {
            int sampleCount = this.tickRateSamples.size();
            if (sampleCount > 20) {
               this.tickRateSamples.poll();
            }

            float maxRate = 20.0F;
            float rateScale = 20.0F;
            float periodScale = 1000.0F;
            float measuredTickRate = rateScale * (periodScale / (float)(System.currentTimeMillis() - this.lastTimeUpdateMillis));
            float upperClampedRate = Math.min(maxRate, measuredTickRate);
            this.latestTickRate = Math.max(0.0F, upperClampedRate);
            ArrayDeque<Float> samples = this.tickRateSamples;
            float capturedRate = this.latestTickRate;
            samples.add(capturedRate);
            float rateSum = 0.0F;

            for (Float sample : this.tickRateSamples) {
               float sampleMax = 20.0F;
               float sampleRate = sample;
               float upperClampedSample = Math.min(sampleMax, sampleRate);
               rateSum += Math.max(0.0F, upperClampedSample);
            }

            if (!this.tickRateSamples.isEmpty()) {
               rateSum /= this.tickRateSamples.size();
            }

            double averageAsDouble = rateSum;
            float averagedRate;
            if (averageAsDouble > 19.95) {
               averagedRate = 20.0F;
            } else {
               averagedRate = rateSum;
            }

            this.averageTickRate = averagedRate;
         }

         this.lastTimeUpdateMillis = System.currentTimeMillis();
      }

      if (event.getPacket() instanceof ClientboundBundlePacket bundlePacket) {
         for (Packet<?> subpacket : bundlePacket.subPackets()) {
            EventBus eventBus = ClientAccess.eventBus;
            PacketEvent.Receive subpacketEvent = new PacketEvent.Receive(subpacket, event.getConnection());
            eventBus.post(subpacketEvent);
         }
      }

      if (event.getPacket() instanceof ClientboundTabListPacket tabListPacket) {
         ClientPacketListener serverConnection = ClientAccess.argusclientMinecraft.player.connection;
         ServerData connectedServer = serverConnection.getServerData();
         if (connectedServer == null) {
            return;
         }

         Component footerComponent = tabListPacket.footer();
         String footer = footerComponent.getString();
         Matcher latencyMatcher = LATENCY_PATTERN.matcher(footer);
         if (latencyMatcher.find()) {
            String latencyText = latencyMatcher.group();

            try {
               int textLength = latencyText.length();
               int suffixEnd = textLength - 5;
               latencyText = latencyText.substring(0, suffixEnd).trim();
               this.footerLatencyMillis = Integer.parseInt(latencyText);
            } catch (Throwable var17) {
            }
         }
      }
   }

   public static void bindModules(ModuleRegistry registry) {
      fastLatency = (FastLatencyModule)registry.getModule(FastLatencyModule.class);
   }

   public enum PacketActivity {
      ANY,
      ENTITY;
   }
}
