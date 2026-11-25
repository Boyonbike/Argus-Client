package dev.argus.client.utils.movement;

import dev.argus.client.utils.movement.ElytraStuckWatch.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ElytraStuckWatchTest {
    private static Action wait(ElytraStuckWatch watch, double x) {
        Action action = Action.NONE;
        for (int tick = 0; tick < ElytraStuckWatch.STUCK_TICKS + 1 && action == Action.NONE; tick++) action = watch.tick(x, 64, 0);
        return action;
    }

    @Test void movingNeverCountsAsStuck() {
        ElytraStuckWatch watch = new ElytraStuckWatch();
        for (int tick = 0; tick < 5000; tick++) assertEquals(Action.NONE, watch.tick(tick * 5, 64, 0));
    }

    @Test void driftingInPlaceStillCounts() {
        ElytraStuckWatch watch = new ElytraStuckWatch();
        Action action = Action.NONE;
        for (int tick = 0; tick < ElytraStuckWatch.STUCK_TICKS + 1 && action == Action.NONE; tick++) action = watch.tick(tick % 2, 64, 0);
        assertEquals(Action.REPLAN, action);
    }

    @Test void replansTwiceThenWalksThenStartsOver() {
        ElytraStuckWatch watch = new ElytraStuckWatch();
        assertEquals(Action.REPLAN, wait(watch, 0));
        watch.restart();
        assertEquals(Action.REPLAN, wait(watch, 0));
        watch.restart();
        assertEquals(Action.WALK, wait(watch, 0));
        watch.restart();
        assertEquals(Action.REPLAN, wait(watch, 0));
    }

    @Test void gettingWellAwayForgetsTheFailedTries() {
        ElytraStuckWatch watch = new ElytraStuckWatch();
        assertEquals(Action.REPLAN, wait(watch, 0));
        assertEquals(Action.NONE, watch.tick(100, 64, 0));
        assertEquals(Action.REPLAN, wait(watch, 100));
        assertEquals(Action.NONE, watch.tick(200, 64, 0));
        assertEquals(Action.REPLAN, wait(watch, 200));
        assertEquals(Action.REPLAN, wait(watch, 200));
    }

    @Test void aSmallMoveKeepsTheFailedTries() {
        ElytraStuckWatch watch = new ElytraStuckWatch();
        assertEquals(Action.REPLAN, wait(watch, 0));
        assertEquals(Action.NONE, watch.tick(10, 64, 0));
        assertEquals(Action.REPLAN, wait(watch, 10));
        assertEquals(Action.NONE, watch.tick(20, 64, 0));
        assertEquals(Action.WALK, wait(watch, 20));
    }
}
