package dev.argus.client.network;

import java.util.UUID;

public record AccountEntry(String id, Type type, String username, String uuid, String credential) {
    public enum Type { MICROSOFT, OFFLINE, SESSION, ALTENING }
    public AccountEntry {
        if (id == null || type == null || username == null || uuid == null || credential == null) throw new IllegalArgumentException("Invalid saved account.");
        if (type == Type.OFFLINE && !username.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("Use a username with 1–16 letters, digits or underscores.");
        if (type != Type.OFFLINE && credential.isBlank()) throw new IllegalArgumentException("Enter an account token.");
    }
    public static AccountEntry offline(String name) {
        return new AccountEntry(UUID.randomUUID().toString(), Type.OFFLINE, name.trim(), UUID.nameUUIDFromBytes(("OfflinePlayer:" + name.trim()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(), "");
    }
    public static AccountEntry session(String token) { return new AccountEntry(UUID.randomUUID().toString(), Type.SESSION, "Session account", "", token.trim()); }
    public static AccountEntry altening(String token) { return new AccountEntry(UUID.randomUUID().toString(), Type.ALTENING, "Altening account", "", token.trim()); }
    @Override public String toString() { return username + " (" + type + ")"; }
}
