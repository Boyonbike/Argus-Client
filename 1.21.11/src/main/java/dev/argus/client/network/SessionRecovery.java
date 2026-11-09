package dev.argus.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

/**
 * Launchers such as Prism only refresh the Minecraft token when the launcher itself starts, so a game launched
 * from a long-running launcher can receive an already-expired token and every server join fails. When that
 * happens and the same Microsoft account is saved in Argus, log it in again silently.
 */
public final class SessionRecovery {
    private static final long RECHECK_NANOS = 10L * 60 * 1_000_000_000L;
    private static volatile boolean running;
    private static volatile String status = "";
    private static volatile User checkedUser;
    private static long lastCheck = Long.MIN_VALUE;

    private SessionRecovery() {}

    /** Empty unless a refresh is in progress or just failed. */
    public static String status(Minecraft mc) { return mc.getUser() == checkedUser ? status : ""; }

    public static synchronized void checkSoon(Minecraft mc) {
        long now = System.nanoTime();
        User user = mc.getUser();
        if (running || (user == checkedUser && lastCheck != Long.MIN_VALUE && now - lastCheck < RECHECK_NANOS)) return;
        String token = user.getAccessToken();
        String uuid = user.getProfileId().toString();
        if (token == null || token.isBlank() || token.length() < 20) return; // offline/cracked sessions have nothing to refresh
        status = "";
        checkedUser = user;
        lastCheck = now; running = true;
        var proxy = mc.getProxy(); var directory = mc.gameDirectory;
        ProfileRuntime.IO.execute(() -> {
            boolean completionQueued = false;
            try {
                if (AccountAuthenticator.tokenAccepted(token)) { status = ""; return; }
                var store = ProfileRuntime.profiles();
                var saved = store == null ? null : store.accounts().stream()
                        .filter(a -> a.type() == AccountEntry.Type.MICROSOFT && a.uuid().equalsIgnoreCase(uuid)).findFirst().orElse(null);
                if (saved == null) { status = "Session expired · restart your launcher or log in under Accounts"; return; }
                status = "Session expired · refreshing…";
                var prepared = AccountSession.prepare(AccountAuthenticator.authenticate(saved), proxy, directory);
                mc.execute(() -> {
                    try {
                        // The user may have joined a server or switched accounts while we were refreshing.
                        if (mc.getUser() != user) return;
                        if (mc.level != null || mc.getConnection() != null) { status = ""; return; }
                        if (ProfileRuntime.profiles() != store || !store.accounts().contains(saved)) {
                            status = "Saved account changed · log in under Accounts";
                            return;
                        }
                        store.putAccount(prepared.login().account()); // persists the rotated refresh token
                        AccountSession.apply(mc, prepared);
                        status = "";
                    } catch (Exception e) { status = "Couldn't refresh session · log in under Accounts"; }
                    finally { running = false; }
                });
                completionQueued = true;
            } catch (Exception e) {
                status = "Couldn't refresh session · log in under Accounts";
            } finally {
                if (!completionQueued) running = false;
            }
        });
    }
}
