package dev.argus.client.modules.movement;

/**
 * Takes off from flat ground for Baritone's elytra flight. Baritone opens the elytra only once the player falls faster
 * than 0.377 blocks per tick, which a jump from flat ground never reaches, so without a drop it waits forever. This jumps
 * and opens the elytra near the top of the jump, as a double tap of the jump key does, then climbs clear of the floor. Ported from Comet's ElytraTakeoff.
 */
public final class ElytraTakeoff {
    public enum Step { WAIT, JUMP, OPEN, COVERED, GAVE_UP }

    /** Vertical speed near the top of a jump, about 1.2 blocks up. */
    static final double OPEN_BELOW = 0.1;
    /** Ground time before jumping again after being in the air. The module skips this class during Baritone's landing. */
    public static final int RETRY_TICKS = 20;
    public static final int MAX_JUMPS = 3;
    /** Ticks between tries at opening the wings in the air, such as a refused open or floating in lava with no jump. */
    public static final int OPEN_RETRY_TICKS = 10;
    public static final int MAX_OPENS = 6;
    /** Rests between tries before it's time to try somewhere else instead of here. */
    public static final int MAX_ROUNDS = 3;
    /** Ticks of gliding that count as a successful take-off, so a later fall gets its full set of tries again. */
    static final int STABLE_GLIDE_TICKS = 20;
    /** Steepest take-off climb asked for; Baritone's own pitch is kept when it climbs harder. */
    public static final float CLIMB_PITCH = -45;
    /** Blocks of air under the player at which Baritone's steering takes over. */
    public static final double CLIMB_CLEARANCE = 4;
    /** Ends the climb under a low ceiling, where the floor never gets far enough away. */
    public static final int CLIMB_TICKS = 40;

    private int jumps;
    private int groundTicks;
    private boolean airborne;
    private int opens;
    private int openWait;
    private int glideTicks;
    private int pausedTicks;
    private boolean climbing;
    private int climbTicks;
    private int lastClimbTick;
    private int rounds;

    /** Called once per tick while Baritone's elytra process is active and Baritone isn't taking off by itself. */
    public Step next(boolean onGround, boolean fallFlying, double verticalSpeed, boolean clear) {
        if (fallFlying) {
            airborne = true;
            groundTicks = 0;
            pausedTicks = 0;
            // A server that refuses the wings drops them a tick later, which must not hurry the next try.
            if (openWait > 0) openWait--;
            if (++glideTicks >= STABLE_GLIDE_TICKS) jumps = opens = rounds = 0;
            return Step.WAIT;
        }
        glideTicks = 0;
        if (pausedTicks > 0) {
            if (--pausedTicks == 0) jumps = opens = 0;
            return Step.WAIT;
        }
        if (!onGround) {
            airborne = true;
            groundTicks = 0;
            if (openWait > 0) {
                openWait--;
                return Step.WAIT;
            }
            if (verticalSpeed > OPEN_BELOW) return Step.WAIT;
            if (opens >= MAX_OPENS) return Step.GAVE_UP;
            opens++;
            openWait = OPEN_RETRY_TICKS;
            startClimb();
            return Step.OPEN;
        }
        opens = openWait = 0;
        groundTicks++;
        if (airborne && groundTicks < RETRY_TICKS) return Step.WAIT;
        if (jumps >= MAX_JUMPS) return Step.GAVE_UP;
        if (!clear) return Step.COVERED;
        jumps++;
        startClimb();
        return Step.JUMP;
    }

    /** Holds off for a while, then starts over with a full set of tries. */
    public void pause(int ticks) {
        pausedTicks = ticks;
        rounds++;
    }

    /** True once enough rounds of tries have failed in a row that this place is the problem. */
    public boolean outOfRounds() {
        return rounds >= MAX_ROUNDS;
    }

    /** A new trip, or somewhere new to try from. {@link #reset} leaves these, because a new route is still the same place. */
    public void forgetRounds() {
        rounds = 0;
    }

    /** True once the wings have stayed open long enough to count as a real take-off. */
    public boolean stable() {
        return glideTicks >= STABLE_GLIDE_TICKS;
    }

    /** True from a take-off attempt until the climb is finished. */
    public boolean climbing() {
        return climbing;
    }

    /**
     * Baritone's pitch assumes a rocket or a dive at this speed, and without one falls back to wherever the camera points,
     * which can steer straight back into the ground. Climb clear of the floor first and leave steering to Baritone after.
     */
    public float climbPitch(float pitch, int tick, double floorClearance) {
        if (!climbing) return pitch;
        if (tick != lastClimbTick) {
            lastClimbTick = tick;
            climbTicks++;
        }
        if (floorClearance >= CLIMB_CLEARANCE || climbTicks > CLIMB_TICKS) {
            climbing = false;
            return pitch;
        }
        return Math.min(pitch, CLIMB_PITCH);
    }

    private void startClimb() {
        climbing = true;
        climbTicks = 0;
        lastClimbTick = Integer.MIN_VALUE;
    }

    /** @param airborne true when the flight starts in the air, so a later landing waits before jumping */
    public void reset(boolean airborne) {
        jumps = 0;
        groundTicks = 0;
        opens = openWait = glideTicks = pausedTicks = 0;
        climbing = false;
        this.airborne = airborne;
    }
}
