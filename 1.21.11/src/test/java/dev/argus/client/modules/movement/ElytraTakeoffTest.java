package dev.argus.client.modules.movement;
import dev.argus.client.modules.movement.ElytraTakeoff.Step;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ElytraTakeoffTest {
    // Vertical speed over a vanilla jump: up at 0.42 less gravity each tick, then down.
    private static final double[] JUMP = {0.3332, 0.2481, 0.1645, 0.0819, 0.0011, -0.0784, -0.1552};

    @Test void jumpsThenOpensNearTheTop() {
        ElytraTakeoff t = new ElytraTakeoff();
        assertEquals(Step.JUMP, t.next(true, false, 0, true));
        assertEquals(Step.WAIT, t.next(false, false, JUMP[0], true));
        assertEquals(Step.WAIT, t.next(false, false, JUMP[1], true));
        assertEquals(Step.WAIT, t.next(false, false, JUMP[2], true));
        assertEquals(Step.OPEN, t.next(false, false, JUMP[3], true));
        assertEquals(Step.WAIT, t.next(false, true, JUMP[4], true));
    }
    @Test void waitsBetweenTriesAtOpening() {
        ElytraTakeoff t = new ElytraTakeoff();
        t.next(true, false, 0, true);
        assertEquals(Step.OPEN, t.next(false, false, JUMP[4], true));
        assertEquals(Step.WAIT, t.next(false, false, JUMP[5], true));
        assertEquals(Step.WAIT, t.next(false, false, JUMP[6], true));
    }
    @Test void opensWhenAlreadyFalling() {
        ElytraTakeoff t = new ElytraTakeoff();
        assertEquals(Step.OPEN, t.next(false, false, -0.5, true));
    }
    @Test void waitsOnTheGroundBeforeJumpingAgain() {
        ElytraTakeoff t = new ElytraTakeoff();
        t.next(true, false, 0, true);
        t.next(false, false, JUMP[4], true);
        for (int i = 1; i < ElytraTakeoff.RETRY_TICKS; i++) assertEquals(Step.WAIT, t.next(true, false, 0, true));
        assertEquals(Step.JUMP, t.next(true, false, 0, true));
    }
    @Test void landingAfterAFlightIsNotATakeoff() {
        ElytraTakeoff t = new ElytraTakeoff();
        t.reset(true);
        assertEquals(Step.WAIT, t.next(true, false, 0, true));
    }
    @Test void givesUpAfterMaxJumps() {
        ElytraTakeoff t = new ElytraTakeoff();
        for (int jump = 0; jump < ElytraTakeoff.MAX_JUMPS; jump++) {
            assertEquals(Step.JUMP, t.next(true, false, 0, true));
            t.next(false, false, JUMP[0], true);
            for (int i = 1; i < ElytraTakeoff.RETRY_TICKS; i++) t.next(true, false, 0, true);
        }
        assertEquals(Step.GAVE_UP, t.next(true, false, 0, true));
    }
    @Test void coveredDoesNotJumpOrCountAJump() {
        ElytraTakeoff t = new ElytraTakeoff();
        for (int tick = 0; tick <= ElytraTakeoff.MAX_JUMPS; tick++) assertEquals(Step.COVERED, t.next(true, false, 0, false));
        assertEquals(Step.JUMP, t.next(true, false, 0, true));
    }
    @Test void climbsAfterJumpingUntilClearOfTheFloor() {
        ElytraTakeoff t = new ElytraTakeoff();
        assertFalse(t.climbing());
        assertEquals(Step.JUMP, t.next(true, false, 0, true));
        assertTrue(t.climbing());
        // Baritone or the camera asking to dive near the floor is overruled, and a steeper climb is kept.
        assertEquals(ElytraTakeoff.CLIMB_PITCH, t.climbPitch(30, 1, 1), 0);
        assertEquals(-70, t.climbPitch(-70, 2, 1), 0);
        assertTrue(t.climbing());
        assertEquals(30, t.climbPitch(30, 3, ElytraTakeoff.CLIMB_CLEARANCE), 0);
        assertFalse(t.climbing());
        assertEquals(30, t.climbPitch(30, 4, 1), 0);
    }
    @Test void aLowCeilingEndsTheClimbInsteadOfHoldingItForever() {
        ElytraTakeoff t = new ElytraTakeoff();
        t.next(true, false, 0, true);
        for (int tick = 1; tick <= ElytraTakeoff.CLIMB_TICKS; tick++) {
            assertEquals(ElytraTakeoff.CLIMB_PITCH, t.climbPitch(10, tick, 1), 0);
            // Several moves in a tick count once.
            assertEquals(ElytraTakeoff.CLIMB_PITCH, t.climbPitch(10, tick, 1), 0);
        }
        assertEquals(10, t.climbPitch(10, ElytraTakeoff.CLIMB_TICKS + 1, 1), 0);
        assertFalse(t.climbing());
    }
    @Test void openingWithoutAJumpAlsoClimbsAndResetStopsIt() {
        ElytraTakeoff t = new ElytraTakeoff();
        // Floating in lava is never on the ground, so the wings open with no jump first.
        assertEquals(Step.OPEN, t.next(false, false, 0, true));
        assertTrue(t.climbing());
        t.reset(false);
        assertFalse(t.climbing());
    }
    @Test void aFlightAlreadyInTheAirIsLeftToBaritone() {
        ElytraTakeoff t = new ElytraTakeoff();
        t.reset(true);
        t.next(false, true, 0, true);
        assertFalse(t.climbing());
    }
    @Test void keepsTryingToOpenWhileInTheAirThenGivesUp() {
        ElytraTakeoff t = new ElytraTakeoff();
        // Floating in lava is never on the ground, so nothing else would ever try again.
        for (int open = 0; open < ElytraTakeoff.MAX_OPENS; open++) {
            assertEquals(Step.OPEN, t.next(false, false, 0, true), "open " + open);
            for (int wait = 0; wait < ElytraTakeoff.OPEN_RETRY_TICKS; wait++) assertEquals(Step.WAIT, t.next(false, false, 0, true));
        }
        assertEquals(Step.GAVE_UP, t.next(false, false, 0, true));
    }
    @Test void droppingOutOfAFlightGetsItsTriesBack() {
        // A real glide, not just a moment of one, resets the count.
        ElytraTakeoff flown = new ElytraTakeoff();
        for (int jump = 0; jump < ElytraTakeoff.MAX_JUMPS; jump++) {
            assertEquals(Step.JUMP, flown.next(true, false, 0, true));
            for (int i = 0; i < ElytraTakeoff.STABLE_GLIDE_TICKS; i++) flown.next(false, true, 0, true);
            for (int i = 1; i < ElytraTakeoff.RETRY_TICKS; i++) assertEquals(Step.WAIT, flown.next(true, false, 0, true));
        }
        assertEquals(Step.JUMP, flown.next(true, false, 0, true));
    }
    @Test void reopeningAfterAFallClimbsAgain() {
        ElytraTakeoff t = new ElytraTakeoff();
        t.reset(true);
        t.next(false, true, 0, true);
        assertFalse(t.climbing());
        assertEquals(Step.OPEN, t.next(false, false, -.5, true));
        assertTrue(t.climbing());
    }
    @Test void wingsTheServerDropsAtOnceDoNotHurryTheNextTry() {
        ElytraTakeoff t = new ElytraTakeoff();
        assertEquals(Step.OPEN, t.next(false, false, 0, true));
        // Open for a tick on the client, then the server refuses them.
        assertEquals(Step.WAIT, t.next(false, true, 0, true));
        for (int wait = 1; wait < ElytraTakeoff.OPEN_RETRY_TICKS; wait++) assertEquals(Step.WAIT, t.next(false, false, 0, true), "wait " + wait);
        assertEquals(Step.OPEN, t.next(false, false, 0, true));
    }
    @Test void aPauseStartsOverWithAFullSetOfTries() {
        ElytraTakeoff t = new ElytraTakeoff();
        for (int open = 0; open < ElytraTakeoff.MAX_OPENS; open++) {
            t.next(false, false, 0, true);
            for (int wait = 0; wait < ElytraTakeoff.OPEN_RETRY_TICKS; wait++) t.next(false, false, 0, true);
        }
        assertEquals(Step.GAVE_UP, t.next(false, false, 0, true));
        t.pause(100);
        for (int tick = 0; tick < 100; tick++) assertEquals(Step.WAIT, t.next(false, false, 0, true));
        assertEquals(Step.OPEN, t.next(false, false, 0, true));
    }
    @Test void glidingCancelsAPauseAndCountsAsStableAfterAWhile() {
        ElytraTakeoff t = new ElytraTakeoff();
        t.pause(100);
        assertFalse(t.stable());
        for (int tick = 0; tick < ElytraTakeoff.STABLE_GLIDE_TICKS; tick++) t.next(false, true, 0, true);
        assertTrue(t.stable());
        assertEquals(Step.OPEN, t.next(false, false, 0, true));
        assertFalse(t.stable());
    }

    @Test void roundsOfFailedTriesRunOutUntilAGlideOrANewPlace() {
        ElytraTakeoff t = new ElytraTakeoff();
        for (int round = 1; round < ElytraTakeoff.MAX_ROUNDS; round++) {
            t.pause(1);
            assertFalse(t.outOfRounds());
        }
        t.reset(false);
        t.pause(1);
        assertTrue(t.outOfRounds(), "a new route is still the same place");
        t.forgetRounds();
        assertFalse(t.outOfRounds());
        for (int round = 0; round < ElytraTakeoff.MAX_ROUNDS; round++) t.pause(1);
        assertTrue(t.outOfRounds());
        for (int tick = 0; tick < ElytraTakeoff.STABLE_GLIDE_TICKS; tick++) t.next(false, true, 0, true);
        assertFalse(t.outOfRounds(), "a real glide means this place is fine");
    }
}
