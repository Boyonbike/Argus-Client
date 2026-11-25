package dev.argus.client.utils.movement;

import dev.argus.client.utils.movement.ElytraProgressWatch.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ElytraProgressWatchTest {
    private static final double FAR = 7_000_000;

    private static Action wait(ElytraProgressWatch watch, double remaining) {
        Action action = Action.NONE;
        for (int tick = 0; tick < ElytraProgressWatch.WINDOW_TICKS; tick++) action = watch.tick(remaining, 1, 2);
        return action;
    }

    @Test void steadyProgressNeverTriggersIt() {
        ElytraProgressWatch watch = new ElytraProgressWatch();
        double remaining = FAR;
        for (int tick = 0; tick < 20000; tick++) {
            assertEquals(Action.NONE, watch.tick(remaining, 1, 2));
            remaining -= .5;
        }
    }

    @Test void aLoopThatKeepsMovingIsCaughtAndEachRoundTriesHarder() {
        ElytraProgressWatch watch = new ElytraProgressWatch();
        watch.tick(FAR, 1, 2);
        assertEquals(Action.REPLAN, wait(watch, FAR + 30));
        assertEquals(Action.REBUILD, wait(watch, FAR - 30));
        assertEquals(Action.UNSTICK, wait(watch, FAR));
        assertEquals(Action.REPLAN, wait(watch, FAR), "and round again");
    }

    @Test void realProgressStartsTheRoundsOver() {
        ElytraProgressWatch watch = new ElytraProgressWatch();
        watch.tick(FAR, 1, 2);
        assertEquals(Action.REPLAN, wait(watch, FAR));
        assertEquals(Action.NONE, watch.tick(FAR - ElytraProgressWatch.GAIN, 1, 2));
        assertEquals(Action.REPLAN, wait(watch, FAR - ElytraProgressWatch.GAIN));
    }

    @Test void aDetourLosingGroundStillCountsItsWayBackFromTheBest() {
        ElytraProgressWatch watch = new ElytraProgressWatch();
        watch.tick(FAR, 1, 2);
        for (int tick = 0; tick < 600; tick++) assertEquals(Action.NONE, watch.tick(FAR + 500, 1, 2));
        assertEquals(Action.NONE, watch.tick(FAR - 64, 1, 2));
        for (int tick = 1; tick < ElytraProgressWatch.WINDOW_TICKS; tick++) assertEquals(Action.NONE, watch.tick(FAR - 64, 1, 2));
    }

    @Test void aNewDestinationAndANewTripStartAgain() {
        ElytraProgressWatch watch = new ElytraProgressWatch();
        watch.tick(FAR, 1, 2);
        for (int tick = 0; tick < ElytraProgressWatch.WINDOW_TICKS - 1; tick++) watch.tick(FAR, 1, 2);
        assertEquals(Action.NONE, watch.tick(FAR, 9, 9));
        for (int tick = 0; tick < ElytraProgressWatch.WINDOW_TICKS - 1; tick++) assertEquals(Action.NONE, watch.tick(FAR, 9, 9));
        watch.reset();
        assertEquals(Action.NONE, watch.tick(FAR, 1, 2));
        assertEquals(Action.REPLAN, wait(watch, FAR));
    }
}
