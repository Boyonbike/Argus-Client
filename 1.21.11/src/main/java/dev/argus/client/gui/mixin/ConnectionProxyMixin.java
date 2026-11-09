package dev.argus.client.gui.mixin;

import dev.argus.client.network.ProfileRuntime;
import dev.argus.client.network.ProxyConnector;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionProxyMixin {
    @Inject(method = "configureSerialization", at = @At("RETURN"))
    private static void argus$proxy(ChannelPipeline pipeline, PacketFlow flow, boolean local, BandwidthDebugMonitor monitor, CallbackInfo ci) {
        if (!local && flow == PacketFlow.CLIENTBOUND) {
            if (ProfileRuntime.profiles() == null) throw new IllegalStateException(ProfileRuntime.error());
            ProxyConnector.install(pipeline, ProfileRuntime.activeProxy());
        }
    }
}
