package dev.argus.client.gui;

import java.util.*;

/**
 * Shows a saved window arrangement on a screen narrower than the one it was arranged on,
 * without touching the arrangement itself. Horizontal positions are squeezed proportionally
 * and windows that end up overlapping are pushed down, so the canvas stays usable; once the
 * screen is wide enough again the original arrangement is shown unchanged.
 */
final class WindowFit {
    static final int MARGIN = 10, SPACING = 8;
    /** Layout width of a window that was never placed by hand: it follows the default layout. */
    static final int AUTO = -1;
    /** Layout width of a window saved before layout widths were recorded: shown as-is, only kept on screen. */
    static final int UNKNOWN = 0;

    private WindowFit() {}

    /**
     * A window as arranged: position, size and the viewport width it was arranged on.
     * A {@code fixed} box (placed by hand in the compact view) is never pushed; others move around it.
     */
    record Box(int x, int y, int width, int height, int layoutWidth, boolean fixed) {
        Box(int x, int y, int width, int height, int layoutWidth) { this(x, y, width, height, layoutWidth, false); }
    }

    static boolean squeezed(int viewportWidth, int layoutWidth) { return layoutWidth > viewportWidth; }

    /** Display x of a window placed at {@code x} on a {@code layoutWidth}-wide screen. */
    static int toScreenX(int x, int width, int viewportWidth, int layoutWidth) {
        int max = Math.max(MARGIN, viewportWidth - width - MARGIN);
        if (!squeezed(viewportWidth, layoutWidth)) return Math.clamp(x, MARGIN, max);
        float span = layoutWidth - width - 2 * MARGIN, room = Math.max(0, viewportWidth - width - 2 * MARGIN);
        if (span <= 0) return Math.clamp(x, MARGIN, max);
        return Math.clamp(MARGIN + Math.round((x - MARGIN) * room / span), MARGIN, max);
    }

    /** Display positions ({x, y} per box, same order) for a {@code viewportWidth}-wide screen. */
    static int[][] fit(int viewportWidth, int minY, List<Box> boxes) {
        int n = boxes.size();
        int[][] out = new int[n][2];
        boolean anySqueezed = false;
        for (int i = 0; i < n; i++) {
            Box box = boxes.get(i);
            out[i][0] = toScreenX(box.x(), box.width(), viewportWidth, box.layoutWidth());
            out[i][1] = Math.max(minY, box.y());
            anySqueezed |= box.fixed() || squeezed(viewportWidth, box.layoutWidth());
        }
        if (!anySqueezed) return out;
        // Settle top to bottom: each window drops below anything already placed that it now overlaps.
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, Comparator.<Integer>comparingInt(i -> out[i][1]).thenComparingInt(i -> out[i][0]));
        List<Integer> placed = new ArrayList<>();
        for (int i = 0; i < n; i++) if (boxes.get(i).fixed()) placed.add(i);
        for (int i : order) {
            if (boxes.get(i).fixed()) continue;
            Box box = boxes.get(i);
            for (boolean moved = true; moved; ) {
                moved = false;
                for (int j : placed) {
                    Box other = boxes.get(j);
                    if (out[i][0] < out[j][0] + other.width() && out[i][0] + box.width() > out[j][0]
                            && out[i][1] < out[j][1] + other.height() && out[i][1] + box.height() > out[j][1]) {
                        out[i][1] = out[j][1] + other.height() + SPACING;
                        moved = true;
                    }
                }
            }
            placed.add(i);
        }
        return out;
    }
}
