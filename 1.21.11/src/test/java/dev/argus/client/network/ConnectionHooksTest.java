package dev.argus.client.network;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.proxy.*;
import java.net.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionHooksTest {
    @Test void installsTheSelectedProtocolAtTheStartOfThePipeline() {
        var channel = new EmbeddedChannel(false, false); ProxyConnector.install(channel.pipeline(), ProxyEntry.parse("socks4://127.0.0.1:1080"));
        assertInstanceOf(Socks4ProxyHandler.class, channel.pipeline().get("argus-proxy"));
        var five = new EmbeddedChannel(false, false); ProxyConnector.install(five.pipeline(), ProxyEntry.parse("127.0.0.1:1080:u:p"));
        assertInstanceOf(Socks5ProxyHandler.class, five.pipeline().get("argus-proxy"));
    }
    @Test void socks5CheckRejectsBadCredentials() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var reply = CompletableFuture.runAsync(() -> {
                try (var socket = server.accept()) {
                    var in = socket.getInputStream(); var out = socket.getOutputStream();
                    assertArrayEquals(new byte[]{5,1,2}, in.readNBytes(3)); out.write(new byte[]{5,2}); out.flush();
                    assertEquals(1, in.read()); int length = in.read(); in.readNBytes(length); int pass = in.read(); in.readNBytes(pass);
                    out.write(new byte[]{1,1}); out.flush();
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            var result = ProxyChecker.check(ProxyEntry.parse("127.0.0.1:" + server.getLocalPort() + ":u:p"), 1000);
            assertFalse(result.alive()); assertEquals("Credentials rejected", result.message()); reply.get(3, TimeUnit.SECONDS);
        }
    }
    @Test void cancelledOrReplacedLoginCannotComplete() {
        var attempt = new LoginAttempt(); long first = attempt.begin(); attempt.cancel(); assertFalse(attempt.accepts(first));
        long second = attempt.begin(); assertFalse(attempt.accepts(first)); assertTrue(attempt.accepts(second));
    }
}
