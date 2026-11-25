package dev.argus.client.utils.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SetbackPaceTest {
    @Test void startsAtFullSpeed() {
        assertEquals(1, new SetbackPace(.1).share(0), 1e-9);
    }

    @Test void halvesOnASetbackAndABurstCountsOnce() {
        SetbackPace pace = new SetbackPace(.1);
        assertTrue(pace.setback(100));
        assertFalse(pace.setback(101));
        assertFalse(pace.setback(100 + SetbackPace.BURST_TICKS - 1));
        assertEquals(.5, pace.share(100), 1e-9);
        assertTrue(pace.setback(100 + SetbackPace.BURST_TICKS));
        assertEquals(.25, pace.share(100 + SetbackPace.BURST_TICKS), 1e-9);
    }

    @Test void neverDropsBelowTheMinimum() {
        SetbackPace pace = new SetbackPace(.3);
        for (int i = 0; i < 20; i++) pace.setback(i * SetbackPace.BURST_TICKS);
        assertEquals(.3, pace.share(20 * SetbackPace.BURST_TICKS), 1e-9);
    }

    @Test void holdsThenBuildsBackGradually() {
        SetbackPace pace = new SetbackPace(.1);
        pace.setback(0);
        for (int tick = 1; tick <= SetbackPace.HOLD_TICKS; tick++) assertEquals(.5, pace.share(tick), 1e-9);
        double rising = pace.share(SetbackPace.HOLD_TICKS + 1);
        assertTrue(rising > .5 && rising < .51, "rising " + rising);
        assertEquals(1, pace.share(1000), 1e-9);
    }

    @Test void repeatedCallsInATickDoNotAdvanceIt() {
        SetbackPace pace = new SetbackPace(.1);
        pace.setback(0);
        double once = pace.share(100);
        assertEquals(once, pace.share(100), 1e-9);
    }

    @Test void aResetIsFullSpeedAgain() {
        SetbackPace pace = new SetbackPace(.1);
        pace.setback(0);
        pace.reset();
        assertEquals(1, pace.share(1), 1e-9);
        assertTrue(pace.setback(2));
    }

    @Test void correctionBurstsExtendTheCleanFlightHold() {
        SetbackPace pace = new SetbackPace(.1);
        pace.setback(0);
        assertFalse(pace.setback(9));
        assertEquals(.5, pace.share(45), 1e-9);
        assertTrue(pace.share(50) > .5);
    }

    @Test void sparseSamplingOnlyRecoversTimeAfterTheHold() {
        SetbackPace pace = new SetbackPace(.1);
        pace.setback(0);
        assertEquals(.505, pace.share(SetbackPace.HOLD_TICKS + 1), 1e-9);
    }
}
