package com.jagrosh.discordipc.entities.pipe;

import com.google.gson.JsonObject;
import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.entities.Packet;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PipeHandshakeTest {
    @Test void handshakeTimesOutWhileTheEndpointStaysSilent() {
        var pipe = new TestPipe(null);
        assertTimeoutPreemptively(Duration.ofSeconds(2),
            () -> assertThrows(SocketTimeoutException.class, () -> pipe.readHandshake(100)));
    }

    @Test void handshakeReturnsTheReplyWhenItArrivesBeforeTheDeadline() throws IOException {
        var response = new Packet(Packet.OpCode.FRAME, new JsonObject(), "UTF-8");
        assertSame(response, new TestPipe(response).readHandshake(1_000));
    }

    private static final class TestPipe extends Pipe {
        private final Packet response;
        private final CountDownLatch waiting = new CountDownLatch(1);

        TestPipe(Packet response) {
            super(new IPCClient(123), new HashMap<>());
            this.response = response;
        }

        @Override public Packet read() throws IOException {
            if (response != null) return response;
            try {
                waiting.await();
                throw new IOException("Connection closed");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Read interrupted", exception);
            }
        }

        @Override public void close() { waiting.countDown(); }
        @Override public void write(byte[] bytes) {}
        @Override public void registerApp(String id, String command) {}
        @Override public void registerSteamGame(String id, String command) {}
    }
}
