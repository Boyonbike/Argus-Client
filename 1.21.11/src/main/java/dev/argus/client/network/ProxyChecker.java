package dev.argus.client.network;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Checks SOCKS negotiation and credentials. This is handshake latency, not server ping. */
public final class ProxyChecker {
    public record Result(boolean alive, long latency, String message) {}
    private ProxyChecker() {}
    public static Result check(ProxyEntry proxy, int timeout) {
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(proxy.host(), proxy.port()), timeout); socket.setSoTimeout(timeout);
            var in = new DataInputStream(socket.getInputStream()); var out = new DataOutputStream(socket.getOutputStream());
            if (proxy.type() == ProxyEntry.Type.SOCKS5) {
                boolean auth = !proxy.username().isEmpty();
                out.write(new byte[]{5, 1, (byte) (auth ? 2 : 0)}); out.flush();
                if (in.readUnsignedByte() != 5) return new Result(false, 0, "Invalid SOCKS5 response");
                int method = in.readUnsignedByte();
                if (method == 2 && auth) {
                    byte[] user = proxy.username().getBytes(StandardCharsets.UTF_8), password = proxy.password().getBytes(StandardCharsets.UTF_8);
                    out.writeByte(1); out.writeByte(user.length); out.write(user); out.writeByte(password.length); out.write(password); out.flush();
                    if (in.readUnsignedByte() != 1 || in.readUnsignedByte() != 0) return new Result(false, 0, "Credentials rejected");
                } else if (method != 0 || auth) return new Result(false, 0, "Authentication method rejected");
            } else {
                // SOCKS4 has no standalone greeting: request a connection to the proxy's own endpoint.
                out.writeByte(4); out.writeByte(1); out.writeShort(proxy.port());
                var resolved = InetAddress.getByName(proxy.host());
                if (!(resolved instanceof Inet4Address)) return new Result(false, 0, "SOCKS4 requires IPv4");
                out.write(resolved.getAddress()); out.write(proxy.username().getBytes(StandardCharsets.UTF_8)); out.writeByte(0); out.flush();
                if (in.readUnsignedByte() != 0 || in.readUnsignedByte() != 90) return new Result(false, 0, "SOCKS4 connection rejected");
                if (in.readNBytes(6).length != 6) return new Result(false, 0, "Incomplete SOCKS4 response");
            }
            return new Result(true, Math.max(1, (System.nanoTime() - start) / 1_000_000), "Alive");
        } catch (SocketTimeoutException e) { return new Result(false, 0, "Timed out"); }
        catch (IOException | IllegalArgumentException e) { return new Result(false, 0, "Unavailable"); }
    }
}
