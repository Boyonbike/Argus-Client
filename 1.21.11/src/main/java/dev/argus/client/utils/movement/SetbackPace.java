package dev.argus.client.utils.movement;

/**
 * Slows down when the server pushes back and builds up again gradually, as a share of full speed. A burst of setbacks from
 * one rejected move counts once, so a single hiccup doesn't drop speed to the floor.
 */
/* Ported from Comet's SetbackPace. */
public final class SetbackPace {
    static final int BURST_TICKS = 10;
    /** Ticks with no setback before speed starts to build back up. */
    static final int HOLD_TICKS = 40;
    static final double RISE_PER_TICK = .005;

    private final double minimum;
    private double share = 1;
    private boolean setBack;
    private int lastSetback;
    private int lastCorrection;
    private int lastTick;

    public SetbackPace(double minimum) {
        this.minimum = minimum;
    }

    /** @return whether this slowed anything down, rather than being part of the same burst */
    public boolean setback(int tick) {
        lastCorrection = tick;
        if (setBack && tick - lastSetback < BURST_TICKS) return false;
        setBack = true;
        lastSetback = lastTick = tick;
        share = Math.max(minimum, share / 2);
        return true;
    }

    /** The share of full speed for this tick. Further calls in the same tick don't advance it. */
    public double share(int tick) {
        if (setBack && tick != lastTick) {
            int cleanTicks = Math.max(0, tick - Math.max(lastTick, lastCorrection + HOLD_TICKS));
            share = Math.min(1, share + RISE_PER_TICK * cleanTicks);
            lastTick = tick;
        }
        return share;
    }

    public void reset() {
        share = 1;
        setBack = false;
    }
}
