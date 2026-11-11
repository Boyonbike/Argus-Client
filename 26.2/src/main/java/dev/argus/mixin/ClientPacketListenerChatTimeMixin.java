package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.ClientPacketListenerHandleMoveVehicleEvent;
import dev.argus.client.events.ClientPacketListenerSendChatEvent;
import dev.argus.client.managers.CommandRegistry;
import dev.argus.client.modules.exploit.FastLatencyModule;
import dev.argus.client.modules.render.AmbienceModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerChatTimeMixin {
   private static final AmbienceModule ambienceModule = ClientContext.getModule(AmbienceModule.class);
   private static final FastLatencyModule fastLatencyModule = ClientContext.getModule(FastLatencyModule.class);
   @Shadow
   private ClientLevel level;
   @Unique
   private ClientPacketListenerSendChatEvent sendChatEvent;

   @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
   private void executeClientCommand(String command, CallbackInfo callback) {
      if (command.startsWith(CommandRegistry.getPrefix())) {
         CommandRegistry.executeCommand(command.substring(CommandRegistry.getPrefix().length()));
         callback.cancel();
      }
   }

   @ModifyVariable(
      method = "sendChat",
      at = @At(value = "INVOKE", target = "Ljava/time/Instant;now()Ljava/time/Instant;", shift = Shift.BEFORE),
      argsOnly = true
   )
   private String postSendChatEvent(String message) {
      this.sendChatEvent = new ClientPacketListenerSendChatEvent(message);
      ClientAccess.eventBus.post(this.sendChatEvent);
      return this.sendChatEvent.getMessage();
   }

   @Inject(method = "sendChat", at = @At(value = "INVOKE", target = "Ljava/time/Instant;now()Ljava/time/Instant;", shift = Shift.AFTER), cancellable = true)
   private void cancelSendChatFromEvent(CallbackInfo callback) {
      if (this.sendChatEvent != null && this.sendChatEvent.isCancelled()) {
         callback.cancel();
      }
   }

   @Inject(
      method = "handleSetTime",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
         shift = Shift.AFTER
      ),
      cancellable = true
   )
   private void overrideServerTimeForAmbience(ClientboundSetTimePacket packet, CallbackInfo callback) {
      if (ambienceModule.isToggled() && ambienceModule.worldTimeSetting.getValue()) {
         long dayTime = this.level.getOverworldClockTime();
         for (var clockUpdate : packet.clockUpdates().entrySet()) {
            if (clockUpdate.getKey().is(net.minecraft.world.clock.WorldClocks.OVERWORLD)) {
               dayTime = clockUpdate.getValue().totalTicks();
               break;
            }
         }
         ambienceModule.updateServerTimeSnapshot(packet.gameTime(), dayTime);
         this.level.setTimeFromServer(ambienceModule.getWorldTime());
         callback.cancel();
      }
   }

   @Inject(method = "handleMoveVehicle", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;absSnapTo(DDDFF)V"), cancellable = true)
   private void postMoveVehicleEvent(ClientboundMoveVehiclePacket packet, CallbackInfo callback) {
      Vec3 vehiclePosition = packet.position();
      ClientPacketListenerHandleMoveVehicleEvent event = new ClientPacketListenerHandleMoveVehicleEvent(vehiclePosition);
      ClientAccess.eventBus.post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @WrapWithCondition(method = "applyPlayerInfoUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerInfo;setLatency(I)V"))
   private boolean allowPlayerInfoLatencyUpdate(PlayerInfo playerInfo, int latencyMillis) {
      return ClientAccess.argusclientMinecraft.player == null
         || !fastLatencyModule.isToggled()
         || !playerInfo.getProfile().id().equals(ClientAccess.argusclientMinecraft.player.getUUID());
   }
}
