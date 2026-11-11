package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.ForcedEntityLayers;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.entity.SidePlayer;
import dev.argus.client.utils.network.PacketSender;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class FakeLagModule extends Module {
   private final Queue<Packet<?>> bufferedPackets = new ArrayDeque<>();
   final Setting<FakeLagModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", FakeLagModule.ModeChoice.BLINK));
   private final Setting<Boolean> cancelSetting = this.add(new BooleanSetting("Cancel", false));
   private final Setting<Integer> timeoutSetting;
   private final Setting<Boolean> autoDisableSetting;
   private final Setting<Boolean> onDamageSetting;
   private final Setting<Boolean> onRubberBandSetting;
   private final Setting<Float> onTimeSetting;
   protected final Setting<Boolean> renderSetting;
   protected final Setting<Boolean> chamsSetting;
   protected final Setting<Color> fillSetting;
   protected final Setting<Color> outlineSetting;
   protected final Setting<Float> lineWidthSetting;
   private final Setting<Boolean> packetsSetting;
   private final Setting<Boolean> movementSetting;
   private final Setting<Boolean> interactSetting;
   private final Setting<Boolean> swingSetting;
   private final Setting<Boolean> swapSetting;
   private final Setting<Boolean> interactEntitySetting;
   private final Setting<Boolean> diggingSetting;
   private final Setting<Boolean> pingSetting;
   private final Setting<Boolean> allSetting;
   private final ElapsedTimer lastReleaseTimer;
   private final ElapsedTimer enabledDurationTimer;
   private final ElapsedTimer pulseTimer;
   SidePlayer bufferedPlayerSnapshot;
   private AABB bufferedPositionBounds;

   private void releaseBufferedPackets() {
      while (!this.bufferedPackets.isEmpty()) {
         Packet<?> packet = this.bufferedPackets.poll();
         PacketSender.sendDirect(packet);
      }

      this.bufferedPositionBounds = this.captureTrackedPositionBounds();
      if (this.modeSetting.getValue() == FakeLagModule.ModeChoice.PULSE) {
         this.lastReleaseTimer.reset();
      }
   }

   @Override
   public String getInfo() {
      float elapsedSeconds = (float)this.lastReleaseTimer.elapsedMillis() / 1000.0F;
      return "%.1f".formatted(Math.max(elapsedSeconds - 0.1F, 0.0F));
   }

   @Override
   public void onEnable() {
      this.lastReleaseTimer.reset();
      this.bufferedPackets.clear();
      this.bufferedPlayerSnapshot = null;
      if (this.isWorldUnavailable()) {
         this.disable();
      } else {
         this.bufferedPositionBounds = this.captureTrackedPositionBounds();
         this.enabledDurationTimer.reset();
         this.pulseTimer.reset();
      }
   }

   @Override
   public void onDisable() {
      if (!this.isWorldUnavailable()) {
         this.releaseBufferedPackets();
      }
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (this.modeSetting.getValue() == FakeLagModule.ModeChoice.PULSE && this.pulseTimer.hasElapsed(this.timeoutSetting.getValue().longValue())) {
         this.releaseBufferedPackets();
         this.pulseTimer.reset();
      }

      if (this.autoDisableSetting.getValue() && !this.onTimeSetting.isAtMinimum()) {
         double durationSeconds = this.onTimeSetting.getValue().floatValue();
         if (this.enabledDurationTimer.hasElapsed(durationSeconds, TimeUnit.SECONDS)) {
            this.toggle();
         }
      }
   }

   @EventHandler(priority = -200)
   private void onConnectionSendPacketEvent(PacketEvent.Send event) {
      if (this.bufferedPackets.isEmpty()) {
         this.bufferedPlayerSnapshot = new SidePlayer(ClientAccess.argusclientMinecraft.player);
         ((ForcedEntityLayers)this.bufferedPlayerSnapshot).argusclient$setForcedLayers(true);
         SidePlayer snapshot = this.bufferedPlayerSnapshot;
         Vec3 trackedPosition = ClientContext.playerStateTracker.getPacketPosition();
         snapshot.setPos(trackedPosition);
      }

      Packet<?> packetToClassify = event.getPacket();
      if (this.shouldBufferPacket(packetToClassify)) {
         if (!event.isCancelled()) {
            Queue<Packet<?>> packetQueue = this.bufferedPackets;
            Packet<?> packetToBuffer = event.getPacket();
            packetQueue.add(packetToBuffer);
            event.cancel();
         }
      }
   }

   @EventHandler
   private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
      this.disable();
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         if (this.modeSetting.getValue() == FakeLagModule.ModeChoice.PULSE) {
            this.pulseTimer.clear();
         }

         if (this.autoDisableSetting.getValue() && this.onRubberBandSetting.getValue()) {
            this.toggle();
         }
      }

      if (event.getPacket() instanceof ClientboundDamageEventPacket damagePacket) {
         int damagedEntityId = damagePacket.entityId();
         if (damagedEntityId == ClientAccess.argusclientMinecraft.player.getId() && this.autoDisableSetting.getValue() && this.onDamageSetting.getValue()) {
            this.toggle();
         }
      }
   }

   private AABB captureTrackedPositionBounds() {
      AABB bounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
      Vec3 bottomCenter = bounds.getBottomCenter();
      double originScale = -1.0;
      Vec3 originOffset = bottomCenter.scale(originScale);
      bounds = bounds.move(originOffset);
      Vec3 trackedPosition = ClientContext.playerStateTracker.getPacketPosition();
      return bounds.move(trackedPosition);
   }

   private boolean shouldBufferPacket(Packet<?> packet) {
      return packet instanceof ServerboundMovePlayerPacket && this.movementSetting.getValue()
         || packet instanceof ServerboundUseItemOnPacket && this.interactSetting.getValue()
         || (packet instanceof ServerboundInteractPacket || packet instanceof ServerboundAttackPacket) && this.interactEntitySetting.getValue()
         || packet instanceof ServerboundPlayerActionPacket && this.diggingSetting.getValue()
         || packet instanceof ServerboundPunchPacket && this.swingSetting.getValue()
         || packet instanceof ServerboundSetCarriedItemPacket && this.swapSetting.getValue()
         || (
               packet instanceof ServerboundPongPacket
                  || packet instanceof ServerboundKeepAlivePacket
                  || packet instanceof ServerboundResourcePackPacket
                  || packet instanceof ServerboundPingRequestPacket
            )
            && this.pingSetting.getValue()
         || this.allSetting.getValue();
   }

   public FakeLagModule() {
      super("FakeLag", "Cancels movement packets until toggled off.", ModuleCategory.MOVEMENT);
      String timeoutName = "Timeout";
      Predicate<Object> timeoutVisibility = ignored -> this.modeSetting.getValue() == FakeLagModule.ModeChoice.PULSE;
      this.timeoutSetting = this.add(new NumberSetting<>(timeoutName, 200, 50, 1000, timeoutVisibility).withUnit("ms"));
      this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", false).markVisibilityDependency());
      this.onDamageSetting = this.add(new BooleanSetting("OnDamage", false).dependsOn(this.autoDisableSetting));
      this.onRubberBandSetting = this.add(new BooleanSetting("OnRubberBand", false).dependsOn(this.autoDisableSetting));
      NumberSetting<Float> onTimeSettingValue = new NumberSetting<>("OnTime", 0.0F, 0.0F, 5.0F);
      onTimeSettingValue.withUnit("s");
      this.onTimeSetting = this.add(onTimeSettingValue.dependsOn(this.autoDisableSetting));
      this.renderSetting = this.add(new BooleanSetting("Render", true).markVisibilityDependency());
      this.chamsSetting = this.add(new BooleanSetting("Chams", false).dependsOn(this.renderSetting));
      Predicate<Object> fillVisibility = ignored -> this.renderSetting.isExpanded();
      this.fillSetting = this.add(new ColorSetting("Fill", new Color(1354603007, true), fillVisibility));
      Predicate<Object> outlineVisibility = ignored -> this.renderSetting.isExpanded();
      Color outlineDefault = new Color(1186830847, true);
      this.outlineSetting = this.add(new ColorSetting("Outline", outlineDefault, outlineVisibility));
      Predicate<Object> lineWidthVisibility = ignored -> this.renderSetting.isExpanded();
      this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 2.0F, lineWidthVisibility));
      this.packetsSetting = this.add(new BooleanSetting("Packets", false).markAsGroup().markVisibilityDependency());
      this.movementSetting = this.add(new BooleanSetting("Movement", true).dependsOn(this.packetsSetting));
      this.interactSetting = this.add(new BooleanSetting("Interact", true).dependsOn(this.packetsSetting));
      this.swingSetting = this.add(new BooleanSetting("Swing", true).dependsOn(this.packetsSetting));
      this.swapSetting = this.add(new BooleanSetting("Swap", true).dependsOn(this.packetsSetting));
      this.interactEntitySetting = this.add(new BooleanSetting("InteractEntity", false).dependsOn(this.packetsSetting));
      this.diggingSetting = this.add(new BooleanSetting("Digging", false).dependsOn(this.packetsSetting));
      this.pingSetting = this.add(new BooleanSetting("Ping", false).dependsOn(this.packetsSetting));
      this.allSetting = this.add(new BooleanSetting("All", false).dependsOn(this.packetsSetting));
      this.lastReleaseTimer = new ElapsedTimer();
      this.enabledDurationTimer = new ElapsedTimer();
      this.pulseTimer = new ElapsedTimer();
      Setting<?> visibilitySetting = this.timeoutSetting;
      Predicate modeVisibility = ignored -> this.modeSetting.getValue() == FakeLagModule.ModeChoice.PULSE;
      visibilitySetting.addVisibilityPredicate(modeVisibility);
      visibilitySetting = this.cancelSetting;
      modeVisibility = ignored -> this.modeSetting.getValue() == FakeLagModule.ModeChoice.BLINK;
      visibilitySetting.addVisibilityPredicate(modeVisibility);
      Setting<Boolean> cancelCallbackSetting = this.cancelSetting;
      Runnable cancelCallback = () -> {
         Setting<Boolean> callbackSetting = this.cancelSetting;
         if (callbackSetting.getValue()) {
            Setting<Boolean> cancelOption = this.cancelSetting;
            cancelOption.setValue(false);
            cancelOption = this.cancelSetting;
            if (cancelOption.isVisible()) {
               if (!this.isWorldUnavailable()) {
                  if (this.isToggled()) {
                     AABB rollbackBounds = this.bufferedPositionBounds;
                     Vec3 rollbackPosition = BoundingBoxUtils.getBottomCenter(rollbackBounds);
                     ClientAccess.argusclientMinecraft.player.xo = rollbackPosition.x;
                     ClientAccess.argusclientMinecraft.player.yo = rollbackPosition.y;
                     ClientAccess.argusclientMinecraft.player.zo = rollbackPosition.z;
                     ClientAccess.argusclientMinecraft.player.setPos(rollbackPosition);
                     ClientAccess.argusclientMinecraft.player.setDeltaMovement(Vec3.ZERO);
                     this.bufferedPackets.clear();
                     this.disable();
                  }
               }
            }
         }
      };
      cancelCallbackSetting.setChangeCallback(cancelCallback);
      EventBus eventBus = ClientAccess.eventBus;
      FakeLagRenderer renderer = new FakeLagRenderer(this);
      eventBus.subscribe(renderer);
   }

   public enum ModeChoice implements NamedEntry {
      BLINK("Blink"),
      PULSE("Pulse");

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
