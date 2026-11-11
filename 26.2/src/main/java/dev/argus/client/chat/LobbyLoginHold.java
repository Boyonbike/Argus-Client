/* Adapted from Comet. SPDX-License-Identifier: GPL-3.0-only */

package dev.argus.client.chat;

/**
 * Remembers the login made on a connection and the wait that follows it. Some servers show a brief dimension change
 * after /login, so movement holds until that has passed and the changed world does not send the password again.
 */
public final class LobbyLoginHold {
    private Object connection;
    private boolean awaitingWorldChange;
    private int ticks;

    /** The password was sent but the outcome is unknown, so the world can still change before any reply arrives. */
    public void sent(Object connection) {
        this.connection = connection;
        awaitingWorldChange = true;
        ticks = 0;
    }

    public void loggedIn(Object connection, int delayTicks) {
        this.connection = connection;
        awaitingWorldChange = false;
        ticks = delayTicks;
    }

    /** A rejected password has to be sent again and gives nothing to wait for. */
    public void rejected() {
        clear();
    }

    /** Restarts the wait for a world change caused by the login; later ones, such as a portal, add no delay. */
    public void worldChanged(Object connection, int delayTicks) {
        if (!loggedInOn(connection) || !awaitingWorldChange && ticks <= 0) return;
        awaitingWorldChange = false;
        ticks = delayTicks;
    }

    public boolean loggedInOn(Object connection) {
        return connection != null && connection == this.connection;
    }

    /** Counts one tick of the wait and returns whether movement should keep holding. */
    public boolean hold(Object connection) {
        if (ticks <= 0) return false;
        if (!loggedInOn(connection)) {
            ticks = 0;
            return false;
        }
        ticks--;
        return true;
    }

    public void clear() {
        connection = null;
        awaitingWorldChange = false;
        ticks = 0;
    }
}
