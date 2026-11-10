package dev.argus.client.network;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

/**
 * Launchers such as Prism only refresh the Minecraft token when the launcher itself starts, so a game launched
 * from a long-running launcher can receive an already-expired token and every server join fails with
 * "Invalid session". When that happens and the same Microsoft account is saved in Argus, log it in again silently:
 * once in the background as soon as the game reaches its menus, and again (blocking) if a join is still rejected.
 */
public final class SessionRecovery {
    private static final long RECHECK_NANOS = 10L * 60 * 1_000_000_000L;
    private static final long JOIN_WAIT_SECONDS = 45;
    private static volatile String status = "";
    private static volatile User checkedUser;
    private static long lastCheck = Long.MIN_VALUE;
    /** The refresh currently running, if any; completes with true once a refreshed session is live. */
    private static CompletableFuture<Boolean> inFlight;

    private SessionRecovery() {}

    /** Empty unless a refresh is in progress or just failed. */
    public static String status(Minecraft mc) { return mc.getUser() == checkedUser ? status : ""; }

    /** Checks the current token in the background (at most every ten minutes) and refreshes it if it was rejected. */
    public static synchronized void checkSoon(Minecraft mc) {
        long now = System.nanoTime();
        User user = mc.getUser();
        if (running() || (user == checkedUser && lastCheck != Long.MIN_VALUE && now - lastCheck < RECHECK_NANOS)) return;
        if (!refreshable(user)) return;
        lastCheck = now;
        inFlight = start(mc, user, true);
    }

    /**
     * Called from Minecraft's login thread after the session server rejected the join. Waits for (or starts) a refresh
     * of the saved account and returns true only once a new session has been applied, so the caller can retry.
     */
    public static boolean recoverRejectedJoin(Minecraft mc) {
        CompletableFuture<Boolean> refresh;
        synchronized (SessionRecovery.class) {
            User user = mc.getUser();
            if (running()) refresh = inFlight;
            else if (!refreshable(user)) return false;
            else { lastCheck = System.nanoTime(); refresh = inFlight = start(mc, user, false); }
        }
        try { return refresh.get(JOIN_WAIT_SECONDS, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        catch (Exception e) { return false; }
    }

    private static boolean running() { return inFlight != null && !inFlight.isDone(); }

    private static boolean refreshable(User user) {
        String token = user.getAccessToken();
        return token != null && !token.isBlank() && token.length() >= 20; // offline/cracked sessions have nothing to refresh
    }

    private static CompletableFuture<Boolean> start(Minecraft mc, User user, boolean verifyFirst) {
        var result = new CompletableFuture<Boolean>();
        String token = user.getAccessToken();
        String uuid = user.getProfileId().toString();
        var proxy = mc.getProxy(); var directory = mc.gameDirectory;
        status = "";
        checkedUser = user;
        ProfileRuntime.IO.execute(() -> {
            try {
                if (verifyFirst && AccountAuthenticator.tokenAccepted(token)) { status = ""; result.complete(false); return; }
                var store = ProfileRuntime.profiles();
                var saved = store == null ? null : store.accounts().stream()
                        .filter(a -> a.type() == AccountEntry.Type.MICROSOFT && a.uuid().equalsIgnoreCase(uuid)).findFirst().orElse(null);
                if (saved == null) { status = "Session expired · restart your launcher or log in under Accounts"; result.complete(false); return; }
                status = "Session expired · refreshing…";
                var prepared = AccountSession.prepare(AccountAuthenticator.authenticate(saved), proxy, directory);
                mc.execute(() -> {
                    try {
                        // The user may have switched accounts or joined a world while we were refreshing.
                        if (mc.getUser() != user) { result.complete(false); return; }
                        if (mc.level != null || mc.getConnection() != null) { status = ""; result.complete(false); return; }
                        if (ProfileRuntime.profiles() != store || !store.accounts().contains(saved)) {
                            status = "Saved account changed · log in under Accounts";
                            result.complete(false);
                            return;
                        }
                        store.putAccount(prepared.login().account()); // persists the rotated refresh token
                        AccountSession.apply(mc, prepared);
                        status = "";
                        result.complete(true);
                    } catch (Exception e) {
                        status = "Couldn't refresh session · log in under Accounts";
                        result.complete(false);
                    }
                });
            } catch (Exception e) {
                status = "Couldn't refresh session · log in under Accounts";
                result.complete(false);
            }
        });
        return result;
    }
}
