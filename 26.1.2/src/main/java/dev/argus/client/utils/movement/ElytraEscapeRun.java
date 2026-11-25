package dev.argus.client.utils.movement;

/**
 * Getting out of somewhere Baritone's elytra pathfinder finds no way out of: come down, run a set distance towards the
 * destination, and take off again from there. Only the plan and its timing; the caller does the landing, the walking and
 * the take-off.
 */
public final class ElytraEscapeRun {
    public enum Step {
        /** Nothing to do this tick. */
        NONE,
        /** Just landed: start walking to {@link #targetX()}, {@link #targetZ()}. */
        WALK,
        /** Far enough, or the walk ended or ran out of time: take off again. */
        DONE
    }

    /** Ticks allowed to come down before running anyway. */
    static final int LAND_TICKS = 200;
    /** Ticks a walk gets before Baritone not pathing counts as it having stopped. */
    static final int WALK_GRACE_TICKS = 40;
    /** Ticks per block allowed for the run, well over walking pace, plus a fixed allowance for getting round things. */
    static final int TICKS_PER_BLOCK = 10, WALK_SLACK_TICKS = 400;
    /** Blocks short of the full distance that still count as far enough. */
    static final double CLOSE_ENOUGH = 2;
    /** Runs in a row without a steady glide in between, after which the caller falls back to what it did before. */
    public static final int MAX_RUNS = 5;

    private enum Phase { IDLE, LANDING, WALKING }

    private Phase phase = Phase.IDLE;
    private double startX, startZ, distance;
    private int targetX, targetZ;
    private int phaseStart;
    private int runs;

    /** Whether another run may start: fewer than {@link #MAX_RUNS} since the last steady glide. */
    public boolean canRun() {
        return runs < MAX_RUNS;
    }

    /** A steady glide since the last run: the runs so far worked. */
    public void glided() {
        runs = 0;
    }

    /** Starts a run from here, {@code distance} blocks towards the destination, or to it when that's nearer. */
    public void start(double x, double z, double destinationX, double destinationZ, double distance, int tick) {
        double dx = destinationX - x, dz = destinationZ - z;
        double length = Math.hypot(dx, dz);
        double scale = length <= distance || length < 1e-6 ? 1 : distance / length;
        startX = x;
        startZ = z;
        this.distance = Math.min(distance, length);
        targetX = (int) Math.floor(x + dx * scale);
        targetZ = (int) Math.floor(z + dz * scale);
        phase = Phase.LANDING;
        phaseStart = tick;
        runs++;
    }

    /** Called once per tick while {@link #active()}. {@code grounded} is on the ground or in a liquid; {@code pathing} is Baritone walking. */
    public Step tick(double x, double z, boolean grounded, boolean pathing, int tick) {
        int elapsed = tick - phaseStart;
        switch (phase) {
            case LANDING -> {
                if (grounded || elapsed >= LAND_TICKS || elapsed < 0) {
                    phase = Phase.WALKING;
                    phaseStart = tick;
                    return Step.WALK;
                }
                return Step.NONE;
            }
            case WALKING -> {
                boolean far = Math.hypot(x - startX, z - startZ) >= distance - CLOSE_ENOUGH;
                boolean stopped = elapsed >= WALK_GRACE_TICKS && !pathing;
                boolean late = elapsed >= distance * TICKS_PER_BLOCK + WALK_SLACK_TICKS || elapsed < 0;
                if (far || stopped || late) {
                    phase = Phase.IDLE;
                    return Step.DONE;
                }
                return Step.NONE;
            }
            default -> {
                return Step.NONE;
            }
        }
    }

    public boolean active() {
        return phase != Phase.IDLE;
    }

    /** Coming down before the run, under the caller's power. */
    public boolean landing() {
        return phase == Phase.LANDING;
    }

    public int targetX() {
        return targetX;
    }

    public int targetZ() {
        return targetZ;
    }

    /** Stops a run without counting it as done, for the end of a trip. */
    public void cancel() {
        phase = Phase.IDLE;
    }

    /** A new trip. */
    public void reset() {
        phase = Phase.IDLE;
        runs = 0;
    }
}
