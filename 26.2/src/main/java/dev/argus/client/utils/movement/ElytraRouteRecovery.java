package dev.argus.client.utils.movement;

/**
 * Baritone lands wherever its route runs out. When it can't work out the next segment and the player is near the end of
 * what it has, it marks the route complete, however far the destination still is, and picks a landing spot right there.
 * This notices that from Baritone's chat messages, which read the same in every build, and asks for a route from wherever
 * the player is instead. A landing it really means, near the destination or with worn-out wings, is left alone.
 */
/* Ported from Comet's ElytraRouteRecovery. */
public final class ElytraRouteRecovery {
    public enum Action { NONE, REPLAN, REBUILD }

    /** Ticks between tries, because each one throws away the current route. */
    static final int RETRY_TICKS = 20;
    /** A new route that also runs out here gets a pathfinder rebuilt from scratch, at most this often. */
    static final int REBUILD_TICKS = 100;
    /** Ticks a warning of an emergency landing lasts. Baritone repeats it every tick for as long as it's true. */
    static final int EMERGENCY_TICKS = 40;
    /** Blocks from the last try after which the route has clearly got somewhere, so tries start over. */
    static final double MOVED = 64;

    private boolean routeEnded, landingAhead, tried;
    private int tries, lastTry, lastRebuild, emergencyTick;
    private boolean rebuilt, emergency;
    private double triedX, triedZ;

    /** Baritone said its route is complete or that it's going to land. */
    public void routeEnded() {
        routeEnded = true;
    }

    /** Baritone said it's landing to save the wings. */
    public void emergencyLanding(int tick) {
        emergency = true;
        emergencyTick = tick;
    }

    /** True from Baritone's route ending until a new one starts: its destination is then only a landing spot. */
    public boolean landingAhead() {
        return landingAhead;
    }

    /** Once per tick during a flight Baritone is in control of. */
    public Action tick(int tick, double x, double z, double remaining, double near, boolean usableElytra) {
        if (emergency && (tick < emergencyTick || tick - emergencyTick > EMERGENCY_TICKS)) emergency = false;
        if (tried && Math.hypot(x - triedX, z - triedZ) >= MOVED) {
            tried = false;
            tries = 0;
        }
        if (!routeEnded) return Action.NONE;
        landingAhead = true;
        if (emergency || remaining < near || !usableElytra) {
            routeEnded = false;
            return Action.NONE;
        }
        if (tried && tick >= lastTry && tick - lastTry < RETRY_TICKS) return Action.NONE;
        routeEnded = false;
        boolean rebuild = tries > 0 && (!rebuilt || tick < lastRebuild || tick - lastRebuild >= REBUILD_TICKS);
        tried = true;
        tries++;
        lastTry = tick;
        triedX = x;
        triedZ = z;
        if (rebuild) {
            rebuilt = true;
            lastRebuild = tick;
        }
        return rebuild ? Action.REBUILD : Action.REPLAN;
    }

    /** A flight or new route has started, so nothing said about the old route counts. Tries here are still remembered. */
    public void routeStarted() {
        routeEnded = landingAhead = false;
    }

    /** A new trip. */
    public void reset() {
        routeStarted();
        tried = rebuilt = emergency = false;
        tries = 0;
    }
}
