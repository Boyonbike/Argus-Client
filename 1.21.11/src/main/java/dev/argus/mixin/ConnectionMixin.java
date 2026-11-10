package dev.argus.mixin;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.modules.exploit.PingSpoofModule;
import dev.argus.client.modules.misc.NoPacketKickModule;
import dev.argus.client.utils.network.PingSpoofDelayHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
   private static NoPacketKickModule noPacketKickModule = ClientContext.getModule(NoPacketKickModule.class);
   private static final PingSpoofModule pingSpoofModule = ClientContext.getModule(PingSpoofModule.class);
   @Shadow
   private Channel channel;
   @Shadow
   @Final
   private PacketFlow receiving;

   @Inject(method = "channelActive", at = @At("TAIL"))
   private void installPingSpoofDelayHandler(ChannelHandlerContext context, CallbackInfo callback) {
      if (this.receiving == PacketFlow.CLIENTBOUND) {
         ChannelPipeline pipeline = context.pipeline();
         if (pipeline.get("argusclient_delayed_handler") == null) {
            pipeline.addFirst(
               "argusclient_delayed_handler",
               new PingSpoofDelayHandler(pingSpoofModule, () -> ((Connection)(Object)this).getPacketListener() instanceof ClientGamePacketListener)
            );
         }
      }
   }

   @Inject(
      method = {
            "channelRead0(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V",
            "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V"
      },
      at = @At("HEAD"),
      cancellable = true
   )
   public void onPacketReceive(ChannelHandlerContext context, Packet packet, CallbackInfo callback) {
      if (this.channel.isOpen() && packet != null) {
         try {
            PacketEvent.Receive event = new PacketEvent.Receive(packet, (Connection)(Object)this);
            ClientAccess.eventBus.post(event);
            if (event.isCancelled()) {
               callback.cancel();
            }
         } catch (Exception var5) {
         }
      }
   }

   @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
   private void onPacketSend(Packet packet, ChannelFutureListener listener, boolean flush, CallbackInfo callback) {
      if (this.receiving == PacketFlow.CLIENTBOUND) {
         try {
            PacketEvent.Send event = new PacketEvent.Send(packet, (Connection)(Object)this);
            ClientAccess.eventBus.post(event);
            if (event.isCancelled()) {
               callback.cancel();
            }
         } catch (Exception var6) {
         }
      }
   }

   @Inject(method = "doSendPacket", at = @At("TAIL"))
   private void onPacketPostSend(Packet packet, ChannelFutureListener listener, boolean flush, CallbackInfo callback) {
      if (this.receiving == PacketFlow.CLIENTBOUND) {
         try {
            PacketEvent.PostSend event = new PacketEvent.PostSend(packet, (Connection)(Object)this);
            ClientAccess.eventBus.post(event);
         } catch (Exception var6) {
         }
      }
   }

   @Inject(method = "exceptionCaught", at = @At("HEAD"), cancellable = true)
   private void onConnectionException(ChannelHandlerContext context, Throwable failure, CallbackInfo callback) {
      if (noPacketKickModule.isToggled()) {
         noPacketKickModule.logInterceptedException(failure);
         callback.cancel();
      }
   }
}
