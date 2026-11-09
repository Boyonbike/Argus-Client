package dev.argus.client.gui;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** A small window shows the arrangement squeezed, never rewrites it, and a wide one shows it unchanged. */
final class WindowFitTest {
    private static final List<WindowFit.Box> WIDE = List.of(
            new WindowFit.Box(10, 64, 112, 120, 1000),
            new WindowFit.Box(400, 64, 112, 200, 1000),
            new WindowFit.Box(878, 64, 112, 90, 1000));

    @Test
    void wideEnoughScreenShowsTheArrangementUnchanged() {
        for (int width : new int[]{1000, 1400}) {
            int[][] shown = WindowFit.fit(width, 6, WIDE);
            for (int i = 0; i < WIDE.size(); i++) {
                assertEquals(WIDE.get(i).x(), shown[i][0]);
                assertEquals(WIDE.get(i).y(), shown[i][1]);
            }
        }
    }

    @Test
    void narrowScreenKeepsEveryWindowOnScreenAndApart() {
        for (int width : new int[]{240, 320, 500, 800}) {
            int[][] shown = WindowFit.fit(width, 6, WIDE);
            for (int i = 0; i < WIDE.size(); i++) {
                var box = WIDE.get(i);
                assertTrue(shown[i][0] >= 10 && shown[i][0] + box.width() <= Math.max(10 + box.width(), width - 10), "off screen at " + width);
                for (int j = 0; j < i; j++) {
                    var other = WIDE.get(j);
                    assertFalse(shown[i][0] < shown[j][0] + other.width() && shown[i][0] + box.width() > shown[j][0]
                            && shown[i][1] < shown[j][1] + other.height() && shown[i][1] + box.height() > shown[j][1],
                            "windows " + i + "/" + j + " overlap at " + width);
                }
            }
            // Left-to-right order survives the squeeze.
            assertTrue(shown[0][0] <= shown[1][0] && shown[1][0] <= shown[2][0]);
        }
    }

    @Test
    void windowsMovedInCompactViewStayPutAndOthersMoveAroundThem() {
        var boxes = List.of(
                new WindowFit.Box(10, 64, 112, 120, 1000),
                new WindowFit.Box(20, 70, 112, 100, 400, true));
        int[][] shown = WindowFit.fit(400, 6, boxes);
        assertEquals(20, shown[1][0]); assertEquals(70, shown[1][1]);
        assertTrue(shown[0][1] >= 70 + 100, "squeezed window should drop below the one placed in compact view");
    }

    @Test
    void autoAndLegacyWindowsAreOnlyKeptOnScreen() {
        assertEquals(10, WindowFit.toScreenX(-30, 112, 400, WindowFit.AUTO));
        assertEquals(400 - 112 - 10, WindowFit.toScreenX(900, 112, 400, WindowFit.UNKNOWN));
    }
}
