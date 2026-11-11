package dev.argus.client.network;

import java.io.IOException;
import java.util.concurrent.*;
import net.fabricmc.loader.api.FabricLoader;

public final class ProfileRuntime {
    public static final ExecutorService IO = Executors.newFixedThreadPool(8, r -> { Thread t = new Thread(r, "Argus connections"); t.setDaemon(true); return t; });
    private static ConnectionProfiles profiles;
    private static boolean attempted;
    private static String error = "";
    private ProfileRuntime() {}
    public static synchronized ConnectionProfiles profiles() {
        if (!attempted) {
            attempted = true;
            try { profiles = new ConnectionProfiles(FabricLoader.getInstance().getGameDir().resolve("argusclient/connections.json")); error = ""; }
            catch (IOException e) { error = e.getMessage(); }
        }
        return profiles;
    }
    public static synchronized void retry() { attempted = false; profiles = null; }
    public static synchronized String error() { return error; }
    public static ProxyEntry activeProxy() { var store = profiles(); return store == null ? null : store.activeProxy(); }
}
