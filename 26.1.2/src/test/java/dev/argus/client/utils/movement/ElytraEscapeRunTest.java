package dev.argus.client.utils.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.argus.client.utils.movement.ElytraEscapeRun.Step;
import org.junit.jupiter.api.Test;

class ElytraEscapeRunTest {
    @Test
    void targetsTheDistanceTowardsTheDestination() {
        ElytraEscapeRun run = new ElytraEscapeRun();
        run.start(0.5, 0.5, 1000.5, 0.5, 48, 0);
        assertEquals(48, run.targetX());
        assertEquals(0, run.targetZ());
    }

    @Test
    void stopsAtANearerDestination() {
        ElytraEscapeRun run = new ElytraEscapeRun();
        run.start(0.5, 0.5, 10.5, 0.5, 48, 0);
        assertEquals(10, run.targetX());
    }

    @Test
    void landsThenWalksThenTakesOffWhenFarEnough() {
        ElytraEscapeRun run = new ElytraEscapeRun();
        run.start(0, 0, 1000, 0, 48, 0);
        assertTrue(run.landing());
        assertEquals(Step.NONE, run.tick(0, 0, false, false, 5));
        assertEquals(Step.WALK, run.tick(0, 0, true, false, 10));
        assertFalse(run.landing());
        assertEquals(Step.NONE, run.tick(20, 0, true, true, 50));
        assertEquals(Step.DONE, run.tick(46.5, 0, true, true, 100));
        assertFalse(run.active());
    }

    @Test
    void walksAnywayWhenItNeverLands() {
        ElytraEscapeRun run = new ElytraEscapeRun();
        run.start(0, 0, 1000, 0, 48, 0);
        assertEquals(Step.WALK, run.tick(0, 0, false, false, ElytraEscapeRun.LAND_TICKS));
    }

    @Test
    void takesOffWhenBaritoneStopsWalking() {
        ElytraEscapeRun run = new ElytraEscapeRun();
        run.start(0, 0, 1000, 0, 48, 0);
        run.tick(0, 0, true, false, 0);
        assertEquals(Step.NONE, run.tick(5, 0, true, false, ElytraEscapeRun.WALK_GRACE_TICKS - 1));
        assertEquals(Step.DONE, run.tick(5, 0, true, false, ElytraEscapeRun.WALK_GRACE_TICKS));
    }

    @Test
    void takesOffWhenTheRunTakesTooLong() {
        ElytraEscapeRun run = new ElytraEscapeRun();
        run.start(0, 0, 1000, 0, 48, 0);
        run.tick(0, 0, true, true, 0);
        int late = 48 * ElytraEscapeRun.TICKS_PER_BLOCK + ElytraEscapeRun.WALK_SLACK_TICKS;
        assertEquals(Step.NONE, run.tick(5, 0, true, true, late - 1));
        assertEquals(Step.DONE, run.tick(5, 0, true, true, late));
    }

    @Test
    void limitsRunsInARowUntilAGlide() {
        ElytraEscapeRun run = new ElytraEscapeRun();
        for (int i = 0; i < ElytraEscapeRun.MAX_RUNS; i++) {
            assertTrue(run.canRun());
            run.start(0, 0, 1000, 0, 48, 0);
            run.cancel();
        }
        assertFalse(run.canRun());
        run.glided();
        assertTrue(run.canRun());
    }
}
