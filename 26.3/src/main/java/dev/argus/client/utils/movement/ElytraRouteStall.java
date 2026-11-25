package dev.argus.client.utils.movement;

/**
 * Flight that keeps strictly to the route won't go round anything, so a route that has become blocked needs replacing quickly.
 * {@link ElytraStuckWatch} carries on behind this, because a new route doesn't always help: after a few tries here it gets
 * the time it needs to act.
 */
/* Ported from Comet's ElytraRouteStall. */
public final class ElytraRouteStall {
    /** Ticks held by something in the way before asking for a new route. */
    static final int HELD_TICKS = 40;
    /** Tries before leaving it to {@link ElytraStuckWatch}. */
    static final int TRIES = 2;
    /** Ticks of moving that show the route got somewhere, so the tries start over. */
    static final int MOVING_TICKS = 40;

    private int held, moving, tries;

    /** @return true to ask for a new route. Called once per tick of following the route. */
    public boolean tick(boolean blocked, boolean moved) {
        if (blocked) {
            moving = 0;
            if (++held < HELD_TICKS || tries >= TRIES) return false;
            held = 0;
            tries++;
            return true;
        }
        held = 0;
        if (moved && ++moving >= MOVING_TICKS) tries = 0;
        return false;
    }

    /** A new trip. */
    public void reset() {
        held = moving = tries = 0;
    }
}
