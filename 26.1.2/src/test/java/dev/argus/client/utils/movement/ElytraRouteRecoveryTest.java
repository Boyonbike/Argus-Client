package dev.argus.client.utils.movement;

import dev.argus.client.utils.movement.ElytraRouteRecovery.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ElytraRouteRecoveryTest {
    private static final double FAR = 7_500_000, NEAR = 128;

    private static Action tick(ElytraRouteRecovery recovery, int tick) {
        return recovery.tick(tick, 0, 0, FAR, NEAR, true);
    }

    @Test void nothingHappensWhileTheRouteIsFine() {
        ElytraRouteRecovery recovery = new ElytraRouteRecovery();
        for (int t = 0; t < 100; t++) assertEquals(Action.NONE, tick(recovery, t));
        assertFalse(recovery.landingAhead());
    }

    @Test void aRouteEndingFarFromTheDestinationAsksForANewOneAtOnce() {
        ElytraRouteRecovery recovery = new ElytraRouteRecovery();
        recovery.routeEnded();
        assertEquals(Action.REPLAN, tick(recovery, 10));
        assertEquals(Action.NONE, tick(recovery, 11), "one message is one try");
    }

    @Test void theSameRouteEndingAgainRebuildsThePathfinder() {
        ElytraRouteRecovery recovery = new ElytraRouteRecovery();
        recovery.routeEnded();
        assertEquals(Action.REPLAN, tick(recovery, 10));
        recovery.routeStarted();
        recovery.routeEnded();
        assertEquals(Action.NONE, tick(recovery, 11), "waits between tries");
        assertTrue(recovery.landingAhead());
        assertEquals(Action.REBUILD, tick(recovery, 10 + ElytraRouteRecovery.RETRY_TICKS));
    }

    @Test void rebuildsAreRationedAndPlainTriesFillTheGaps() {
        ElytraRouteRecovery recovery = new ElytraRouteRecovery();
        int t = 0;
        recovery.routeEnded();
        assertEquals(Action.REPLAN, tick(recovery, t));
        t += ElytraRouteRecovery.RETRY_TICKS;
        recovery.routeEnded();
        assertEquals(Action.REBUILD, tick(recovery, t));
        t += ElytraRouteRecovery.RETRY_TICKS;
        recovery.routeEnded();
        assertEquals(Action.REPLAN, tick(recovery, t));
        t += ElytraRouteRecovery.REBUILD_TICKS;
        recovery.routeEnded();
        assertEquals(Action.REBUILD, tick(recovery, t));
    }

    @Test void gettingSomewhereStartsTheTriesOver() {
        ElytraRouteRecovery recovery = new ElytraRouteRecovery();
        recovery.routeEnded();
        assertEquals(Action.REPLAN, recovery.tick(0, 0, 0, FAR, NEAR, true));
        assertEquals(Action.NONE, recovery.tick(5, ElytraRouteRecovery.MOVED, 0, FAR, NEAR, true));
        recovery.routeEnded();
        assertEquals(Action.REPLAN, recovery.tick(6, ElytraRouteRecovery.MOVED, 0, FAR, NEAR, true));
    }

    @Test void aLandingNearTheDestinationIsLeftAlone() {
        ElytraRouteRecovery recovery = new ElytraRouteRecovery();
        recovery.routeEnded();
        assertEquals(Action.NONE, recovery.tick(0, 0, 0, NEAR - 1, NEAR, true));
        assertTrue(recovery.landingAhead(), "its destination is now only the landing spot");
    }

    @Test void aLandingWithWornOutWingsIsLeftAlone() {
        ElytraRouteRecovery recovery = new ElytraRouteRecovery();
        recovery.routeEnded();
        assertEquals(Action.NONE, recovery.tick(0, 0, 0, FAR, NEAR, false));
        recovery.routeEnded();
        recovery.emergencyLanding(1);
        assertEquals(Action.NONE, tick(recovery, 1));
        assertEquals(Action.NONE, tick(recovery, 2));
    }

    @Test void anEmergencyWarningWearsOffOnceBaritoneStopsRepeatingIt() {
        ElytraRouteRecovery recovery = new ElytraRouteRecovery();
        recovery.emergencyLanding(0);
        assertEquals(Action.NONE, tick(recovery, 1));
        recovery.routeEnded();
        assertEquals(Action.REPLAN, tick(recovery, ElytraRouteRecovery.EMERGENCY_TICKS + 1));
    }

    @Test void aNewRouteClearsTheLandingAndANewTripClearsTheTries() {
        ElytraRouteRecovery recovery = new ElytraRouteRecovery();
        recovery.routeEnded();
        assertEquals(Action.REPLAN, tick(recovery, 0));
        assertTrue(recovery.landingAhead());
        recovery.routeStarted();
        assertFalse(recovery.landingAhead());
        recovery.reset();
        recovery.routeEnded();
        assertEquals(Action.REPLAN, tick(recovery, 1), "a new trip doesn't wait or rebuild");
    }

    @Test void aRestartedTickCounterDoesNotHoldTriesBack() {
        ElytraRouteRecovery recovery = new ElytraRouteRecovery();
        recovery.routeEnded();
        assertEquals(Action.REPLAN, tick(recovery, 5000));
        recovery.routeEnded();
        assertEquals(Action.REBUILD, tick(recovery, 3));
    }
}
