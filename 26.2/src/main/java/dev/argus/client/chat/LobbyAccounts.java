/* Adapted from Comet. SPDX-License-Identifier: GPL-3.0-only */

package dev.argus.client.chat;

import java.util.UUID;

/** Tells cracked sessions from premium ones. */
public final class LobbyAccounts {
    /** The version of the name-based id every offline account gets; Mojang accounts use random version 4 ids. */
    private static final int OFFLINE_ID_VERSION = 3;

    private LobbyAccounts() {}

    public static boolean cracked(UUID id, String token) {
        if (id != null && id.version() == OFFLINE_ID_VERSION) return true;
        return token == null || token.isBlank() || token.equals("0");
    }
}
