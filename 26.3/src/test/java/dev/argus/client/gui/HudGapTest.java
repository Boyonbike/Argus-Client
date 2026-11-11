package dev.argus.client.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HudGapTest {
    private static boolean clear(int[] p, int w, int h, List<HudGap.Rect> others, int gap) {
        for (HudGap.Rect o : others)
            if (p[0] < o.x() + o.w() + gap && p[0] + w > o.x() - gap && p[1] < o.y() + o.h() + gap && p[1] + h > o.y() - gap) return false;
        return true;
    }

    @Test void noGapOnlyClampsToTheSafeZones() {
        assertArrayEquals(new int[]{4, 6}, HudGap.resolve(-10, 2, 20, 10, List.of(new HudGap.Rect(0, 0, 50, 50)), 0, 4, 300, 6, 200));
    }

    @Test void freeSpotIsKept() {
        assertArrayEquals(new int[]{100, 100}, HudGap.resolve(100, 100, 20, 10, List.of(new HudGap.Rect(0, 0, 20, 20)), 4, 0, 300, 0, 200));
    }

    @Test void pushesOutTheShortestWay() {
        // Overlaps the right edge of the other element by 2, so it moves right to keep the 4 gap.
        int[] p = HudGap.resolve(18, 0, 20, 10, List.of(new HudGap.Rect(0, 0, 20, 10)), 4, 0, 300, 0, 200);
        assertArrayEquals(new int[]{24, 0}, p);
    }

    @Test void nearTheLeftSafeZoneItGoesAnotherWayInsteadOfOverlapping() {
        // Another element sits against the left safe zone (x = 10); the dragged one lands on top of it.
        // Pushing left is blocked by the zone, so it must go right or below, never stay overlapping.
        var others = List.of(new HudGap.Rect(10, 50, 30, 12));
        int[] p = HudGap.resolve(12, 52, 30, 12, others, 6, 10, 290, 10, 190);
        assertTrue(clear(p, 30, 12, others, 6));
        assertTrue(p[0] >= 10 && p[1] >= 10);
    }

    @Test void squeezedIntoACornerItFindsAClearSpot() {
        // Two elements fence the bottom-right corner; the gap must still hold against both.
        var others = List.of(new HudGap.Rect(250, 170, 40, 20), new HudGap.Rect(260, 140, 30, 20));
        int[] p = HudGap.resolve(270, 175, 20, 10, others, 4, 0, 280, 0, 190);
        assertTrue(clear(p, 20, 10, others, 4));
        assertTrue(p[0] <= 280 && p[1] <= 190);
    }

    @Test void stacksOfNeighboursAreAllRespected() {
        var others = List.of(new HudGap.Rect(0, 0, 40, 10), new HudGap.Rect(0, 14, 40, 10), new HudGap.Rect(0, 28, 40, 10));
        int[] p = HudGap.resolve(0, 12, 40, 10, others, 4, 0, 300, 0, 200);
        assertTrue(clear(p, 40, 10, others, 4));
    }
}
