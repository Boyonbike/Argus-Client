package dev.randomstuff.client.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Headless regression test: all category windows stay reachable without overlapping. */
final class CategoryLayoutTest {
    @Test
    void allCategoriesFitCommonScaledViewportsWithoutOverlap() {
        int[] heights = {43, 84, 200, 55, 122, 31, 300, 70};
        for (int width : new int[]{320, 480, 640, 1920}) {
            int[][] positions = CategoryLayout.arrange(width, heights);
            assertEquals(heights.length, positions.length, "Missing category");
            for (int i = 0; i < positions.length; i++) {
                int x = positions[i][0], y = positions[i][1];
                assertTrue(x >= 10 && x + 112 <= width - 10 && y >= 64,
                        "Unreachable category " + i + " at width " + width);
                for (int j = 0; j < i; j++) {
                    int otherX = positions[j][0], otherY = positions[j][1];
                    assertFalse(x < otherX + 112 && x + 112 > otherX
                            && y < otherY + heights[j] && y + heights[i] > otherY,
                            "Overlapping categories " + i + "/" + j);
                }
            }
        }
    }
}
