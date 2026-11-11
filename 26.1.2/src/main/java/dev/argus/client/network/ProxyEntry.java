package dev.argus.client.network;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record ProxyEntry(String id, String name, Type type, String host, int port, String username, String password) {
    public enum Type { SOCKS4, SOCKS5 }
    public ProxyEntry {
        if (id == null || name == null || type == null || host == null || host.isBlank() || host.contains(" ") || port < 1 || port > 65535 || username == null || password == null)
            throw new IllegalArgumentException("Enter a host and a port between 1 and 65535.");
        if (username.getBytes(StandardCharsets.UTF_8).length > 255 || password.getBytes(StandardCharsets.UTF_8).length > 255)
            throw new IllegalArgumentException("Proxy credentials must be at most 255 bytes.");
    }
    public String address() { return (host.contains(":") ? "[" + host + "]" : host) + ":" + port; }
    @Override public String toString() { return (name.isBlank() ? address() : name) + " (" + type + ")"; }
    public static ProxyEntry parse(String source) {
        try {
            String text = source.trim(), name = "";
            if (!text.contains("://") && text.contains("=")) { int eq = text.indexOf('='); name = text.substring(0, eq).trim(); text = text.substring(eq + 1); }
            Type type = Type.SOCKS5;
            if (text.toLowerCase(java.util.Locale.ROOT).endsWith("@socks4")) { type = Type.SOCKS4; text = text.substring(0, text.length() - 7); }
            else if (text.toLowerCase(java.util.Locale.ROOT).endsWith("@socks5")) text = text.substring(0, text.length() - 7);
            if (!text.contains("://") && !text.contains("@") && !text.startsWith("[")) {
                String[] parts = text.split(":", 4);
                if (parts.length >= 3) text = encode(parts[2]) + (parts.length == 4 ? ":" + encode(parts[3]) : "") + "@" + parts[0] + ":" + parts[1];
            }
            URI uri = URI.create(text.contains("://") ? text : "socks" + (type == Type.SOCKS4 ? "4" : "5") + "://" + text);
            type = switch (uri.getScheme().toLowerCase(java.util.Locale.ROOT)) { case "socks4" -> Type.SOCKS4; case "socks", "socks5" -> Type.SOCKS5; default -> throw new IllegalArgumentException(); };
            if (uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null || (uri.getPath() != null && !uri.getPath().isEmpty())) throw new IllegalArgumentException();
            String[] auth = uri.getRawUserInfo() == null ? new String[]{"", ""} : uri.getRawUserInfo().split(":", 2);
            String host = uri.getHost().replaceAll("^\\[|\\]$", "");
            return new ProxyEntry(UUID.randomUUID().toString(), name, type, host, uri.getPort(), decode(auth[0]), auth.length == 2 ? decode(auth[1]) : "");
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("Use host:port, host:port:user:password or socks5://user:password@host:port."); }
    }
    private static String decode(String s) { return URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8); }
    private static String encode(String s) { return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"); }
}
