package dev.argus.mixin;

import dev.argus.client.modules.movement.BoatPhaseModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import io.netty.channel.ChannelFutureListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells Boat Phase a packet was handed to the connection, on the client thread and before {@code send} returns.
 * {@link dev.argus.client.events.PacketEvent.PostSend} comes from {@code doSendPacket}, which Minecraft queues onto the
 * Netty thread, so it arrives too late for Boat Phase's substep loop. Cancelled packets never reach this point.
 */
@Mixin(Connection.class)
public class BoatPhaseConnectionMixin {
    @Shadow @Final private PacketFlow receiving;

    @Inject(method = "sendPacket", at = @At("RETURN"))
    private void argus$boatPhaseAfterSend(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo info) {
        if (receiving != PacketFlow.CLIENTBOUND || !Minecraft.getInstance().isSameThread()) return;
        BoatPhaseModule module = BoatPhaseModule.active();
        if (module != null) module.afterSend((Connection) (Object) this, packet);
    }
}
