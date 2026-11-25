package dev.argus.client.utils.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ElytraRouteStallTest {
    private static int asked(ElytraRouteStall stall, boolean blocked, boolean moved, int ticks) {
        int asked = 0;
        for (int i = 0; i < ticks; i++) if (stall.tick(blocked, moved)) asked++;
        return asked;
    }

    @Test void asksAfterBeingHeldAndThenLeavesItToTheStuckWatch() {
        ElytraRouteStall stall = new ElytraRouteStall();
        assertEquals(0, asked(stall, true, false, ElytraRouteStall.HELD_TICKS - 1));
        assertEquals(1, asked(stall, true, false, 1));
        assertEquals(1, asked(stall, true, false, ElytraRouteStall.HELD_TICKS));
        assertEquals(0, asked(stall, true, false, 1000), "gives the stuck watch time to act");
    }

    @Test void gettingSomewhereStartsTheTriesOver() {
        ElytraRouteStall stall = new ElytraRouteStall();
        assertEquals(2, asked(stall, true, false, 1000));
        assertEquals(0, asked(stall, false, true, ElytraRouteStall.MOVING_TICKS));
        assertEquals(2, asked(stall, true, false, 1000));
    }

    @Test void aMomentOfMovingDoesNotStartTheTriesOver() {
        ElytraRouteStall stall = new ElytraRouteStall();
        assertEquals(2, asked(stall, true, false, 1000));
        assertEquals(0, asked(stall, false, true, ElytraRouteStall.MOVING_TICKS - 1));
        assertEquals(0, asked(stall, true, false, 1000));
    }

    @Test void waitingOnAChunkIsNotBeingHeld() {
        ElytraRouteStall stall = new ElytraRouteStall();
        assertEquals(0, asked(stall, false, false, 1000));
    }
}
