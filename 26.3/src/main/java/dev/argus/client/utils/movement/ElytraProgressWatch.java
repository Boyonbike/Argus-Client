package dev.argus.client.utils.movement;

/**
 * Notices a flight that keeps moving but isn't getting closer to where it's going, such as a loop near an object, which
 * {@link ElytraStuckWatch} can't see because the player never stays in one place. Each round without progress tries harder.
 */
/* Ported from Comet's ElytraProgressWatch. */
public final class ElytraProgressWatch {
    public enum Action { NONE, REPLAN, REBUILD, UNSTICK }

    /** A route can head away from the destination for a while to get around something, so this is generous. */
    public static final int WINDOW_TICKS = 1200;
    /** Blocks closer to the destination that count as getting somewhere. */
    static final double GAIN = 64;

    private boolean started;
    private double best;
    private int destinationX, destinationZ, ticks, rounds;

    /** Once per tick of a flight. A different destination is a new start. */
    public Action tick(double remaining, int destinationX, int destinationZ) {
        if (!started || destinationX != this.destinationX || destinationZ != this.destinationZ) {
            started = true;
            this.destinationX = destinationX;
            this.destinationZ = destinationZ;
            best = remaining;
            ticks = rounds = 0;
            return Action.NONE;
        }
        if (remaining <= best - GAIN) {
            best = remaining;
            ticks = rounds = 0;
            return Action.NONE;
        }
        if (++ticks < WINDOW_TICKS) return Action.NONE;
        ticks = 0;
        rounds++;
        if (rounds == 1) return Action.REPLAN;
        if (rounds == 2) return Action.REBUILD;
        rounds = 0;
        return Action.UNSTICK;
    }

    /** A new trip. A new route on the same trip keeps counting, because a loop can come back through any number of them. */
    public void reset() {
        started = false;
        ticks = rounds = 0;
    }
}
