package dev.argus.client.network;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import java.net.InetSocketAddress;

public final class ProxyConnector {
    private ProxyConnector() {}
    public static void install(ChannelPipeline pipeline, ProxyEntry proxy) {
        if (proxy == null) return;
        var address = new InetSocketAddress(proxy.host(), proxy.port());
        if (proxy.type() == ProxyEntry.Type.SOCKS4) pipeline.addFirst("argus-proxy", new Socks4ProxyHandler(address, proxy.username()));
        else pipeline.addFirst("argus-proxy", new Socks5ProxyHandler(address, proxy.username().isEmpty() ? null : proxy.username(), proxy.password().isEmpty() ? null : proxy.password()));
    }
}
