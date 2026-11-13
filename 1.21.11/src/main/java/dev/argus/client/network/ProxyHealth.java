package dev.argus.client.network;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Shared proxy check results, and the launch-time pass over the saved proxies. A proxy that was switched on when the
 * game closed is either dropped (when "Keep proxy on launch" is off) or re-checked along with the rest of the list,
 * so a proxy that died while the game was closed shows up as a warning instead of a silent failed join.
 */
public final class ProxyHealth {
    private static final int TIMEOUT_MS = 5000;
    private static final Map<String, ProxyChecker.Result> RESULTS = new ConcurrentHashMap<>();
    private static volatile boolean launchStarted, launchRunning;
    private static volatile String launchSummary = "";

    private ProxyHealth() {}

    /** Last check of every proxy, by id. Shared with the Proxies screen so launch results show there too. */
    public static Map<String, ProxyChecker.Result> results() { return RESULTS; }

    public static boolean checking() { return launchRunning; }
    /** One line about the launch check, e.g. "3 of 5 proxies responding"; empty before it finishes. */
    public static String launchSummary() { return launchSummary; }

    /** True once a check found the switched-on proxy unreachable. */
    public static boolean activeDead() {
        var active = ProfileRuntime.activeProxy();
        if (active == null) return false;
        var r = RESULTS.get(active.id());
        return r != null && !r.alive();
    }

    /** Warning for menus while the switched-on proxy is known to be dead; empty otherwise. */
    public static String warning() {
        var active = ProfileRuntime.activeProxy();
        if (active == null) return "";
        if (launchRunning && !RESULTS.containsKey(active.id())) return "Checking proxy " + label(active) + "…";
        var r = RESULTS.get(active.id());
        return r != null && !r.alive() ? "Proxy " + label(active) + " is dead (" + r.message().toLowerCase(java.util.Locale.ROOT) + ") · joins will fail until you switch it off" : "";
    }

    private static String label(ProxyEntry p) { return p.name().isBlank() ? p.address() : p.name(); }

    /** Runs once per game launch, the first time the menus open. */
    public static synchronized void checkOnLaunch() {
        if (launchStarted) return;
        launchStarted = true;
        var store = ProfileRuntime.profiles();
        if (store == null) return;
        if (!store.keepProxy() && store.activeProxy() != null) {
            try { store.selectProxy(null); } catch (Exception ignored) {}
        }
        var list = store.proxies();
        if (list.isEmpty()) return;
        launchRunning = true;
        ProfileRuntime.IO.execute(() -> {
            // Check the switched-on proxy first so its warning appears as soon as possible.
            var active = store.activeProxy();
            try (var pool = Executors.newFixedThreadPool(8, r -> { Thread t = new Thread(r, "Argus proxy check"); t.setDaemon(true); return t; })) {
                if (active != null) RESULTS.put(active.id(), ProxyChecker.check(active, TIMEOUT_MS));
                var tasks = new ArrayList<Future<?>>();
                for (var p : list) if (active == null || !p.id().equals(active.id())) tasks.add(pool.submit(() -> RESULTS.put(p.id(), ProxyChecker.check(p, TIMEOUT_MS))));
                for (var task : tasks) try { task.get(); } catch (Exception ignored) {}
            } finally {
                long alive = list.stream().map(p -> RESULTS.get(p.id())).filter(r -> r != null && r.alive()).count();
                launchSummary = alive + " of " + list.size() + " proxies responding";
                launchRunning = false;
            }
        });
    }
}
