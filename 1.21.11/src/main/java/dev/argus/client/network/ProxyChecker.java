package dev.argus.client.network;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Checks SOCKS5 negotiation or SOCKS4 TCP reachability. */
public final class ProxyChecker {
    public record Result(boolean alive, long latency, String message) {}
    private ProxyChecker() {}
    public static Result check(ProxyEntry proxy, int timeout) {
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(proxy.host(), proxy.port()), timeout);
            // SOCKS4 negotiation requires a destination. A TCP probe does not depend on proxy routing policy.
            if (proxy.type() == ProxyEntry.Type.SOCKS4) {
                return new Result(true, elapsedMillis(start), "TCP endpoint reachable");
            }
            socket.setSoTimeout(timeout);
            var in = new DataInputStream(socket.getInputStream()); var out = new DataOutputStream(socket.getOutputStream());
            boolean auth = !proxy.username().isEmpty();
            out.write(new byte[]{5, 1, (byte) (auth ? 2 : 0)}); out.flush();
            if (in.readUnsignedByte() != 5) return new Result(false, 0, "Invalid SOCKS5 response");
            int method = in.readUnsignedByte();
            if (method == 2 && auth) {
                byte[] user = proxy.username().getBytes(StandardCharsets.UTF_8), password = proxy.password().getBytes(StandardCharsets.UTF_8);
                out.writeByte(1); out.writeByte(user.length); out.write(user); out.writeByte(password.length); out.write(password); out.flush();
                if (in.readUnsignedByte() != 1 || in.readUnsignedByte() != 0) return new Result(false, 0, "Credentials rejected");
            } else if (method != 0 || auth) return new Result(false, 0, "Authentication method rejected");
            return new Result(true, elapsedMillis(start), "Alive");
        } catch (SocketTimeoutException e) { return new Result(false, 0, "Timed out"); }
        catch (IOException | IllegalArgumentException e) { return new Result(false, 0, "Unavailable"); }
    }

    private static long elapsedMillis(long start) {
        return Math.max(1, (System.nanoTime() - start) / 1_000_000);
    }
}
