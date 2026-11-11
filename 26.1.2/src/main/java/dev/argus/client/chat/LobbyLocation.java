/* Adapted from Comet. SPDX-License-Identifier: GPL-3.0-only */

package dev.argus.client.chat;

import java.util.Locale;

/**
 * Where a recovery is, from the server type shown on the F3 screen. Types must match in full, ignoring capitals,
 * spaces and colour codes, so an ordinary Paper server is not mistaken for this network. Its lobbies share one type and differ by dimension. Only the waiting area,
 * whose server type is unconfirmed, still needs its spawn coordinates.
 */
public enum LobbyLocation {
    NONE,
    GAME_SERVER,
    CRACKED_LOBBY,
    LOBBY,
    WAITING_AREA;

    private static final String LOBBY_BRAND = "paper(fastervelocity)";
    private static final String GAME_BRAND = "fox(fastervelocity)";
    private static final String END = "minecraft:the_end";
    private static final String OVERWORLD = "minecraft:overworld";
    private static final double WAITING_X = 29.5, WAITING_Y = 19.06, WAITING_Z = 18.5;

    public boolean lobby() {
        return this == CRACKED_LOBBY || this == LOBBY || this == WAITING_AREA;
    }

    /** Only a cracked account logs in, and only in the known lobby, so the password goes nowhere else. */
    public boolean requiresLogin(boolean crackedAccount) {
        return this == CRACKED_LOBBY && crackedAccount;
    }

    /**
     * The area to keep after rescanning the same world and server. The waiting area stays put once seen, because
     * being nudged off its spawn would otherwise read as the cracked lobby and send /login.
     */
    public LobbyLocation afterRescan(LobbyLocation detected) {
        return this == WAITING_AREA || detected == NONE ? this : detected;
    }

    public static LobbyLocation detect(String brand, String dimension, double x, double y, double z) {
        if (dimension == null) return NONE;
        String type = brand == null ? "" : ChatSender.plain(brand).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (type.equals(GAME_BRAND)) return GAME_SERVER;
        if (dimension.equals(END) && Math.abs(WAITING_Y - y) <= 1
            && Math.abs(WAITING_X - x) <= 3 && Math.abs(WAITING_Z - z) <= 3) return WAITING_AREA;
        if (!type.equals(LOBBY_BRAND)) return NONE;
        return switch (dimension) {
            case END -> CRACKED_LOBBY;
            case OVERWORLD -> LOBBY;
            default -> NONE;
        };
    }
}
