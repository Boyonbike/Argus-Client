package dev.argus.client.utils.movement;

/**
 * Notices a flight that has stopped getting anywhere for any reason. It first asks for a new route from where the player
 * is, and if that keeps failing in the same place, for walking out.
 */
/* Ported from Comet's ElytraStuckWatch. */
public final class ElytraStuckWatch {
    public enum Action { NONE, REPLAN, WALK }

    /** Longer than Baritone's 10 second limit on working out a route, so it isn't interrupted while thinking. */
    public static final int STUCK_TICKS = 300;
    /** Blocks that count as getting somewhere. */
    static final double MOVED = 4;
    /** Blocks from the last stuck place after which the failed tries are forgotten. */
    static final double ESCAPED = 32;
    public static final int REPLANS = 2;

    private boolean started;
    private double x, y, z;
    private boolean stuckBefore;
    private double stuckX, stuckY, stuckZ;
    private int ticks;
    private int rounds;

    /** Called once per tick during a flight. */
    public Action tick(double px, double py, double pz) {
        if (!started || distance(px, py, pz, x, y, z) >= MOVED) {
            started = true;
            x = px;
            y = py;
            z = pz;
            ticks = 0;
            if (stuckBefore && distance(px, py, pz, stuckX, stuckY, stuckZ) >= ESCAPED) {
                stuckBefore = false;
                rounds = 0;
            }
            return Action.NONE;
        }
        if (++ticks < STUCK_TICKS) return Action.NONE;
        ticks = 0;
        stuckBefore = true;
        stuckX = px;
        stuckY = py;
        stuckZ = pz;
        if (++rounds <= REPLANS) return Action.REPLAN;
        rounds = 0;
        return Action.WALK;
    }

    /** A new attempt from here gets the full wait, and failures here are still remembered. */
    public void restart() {
        started = false;
        ticks = 0;
    }

    /** A new trip. */
    public void reset() {
        restart();
        stuckBefore = false;
        rounds = 0;
    }

    private static double distance(double ax, double ay, double az, double bx, double by, double bz) {
        return Math.sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by) + (az - bz) * (az - bz));
    }
}
