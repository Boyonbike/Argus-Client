package dev.argus.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The auto gap for HUD elements: finds the spot closest to where an element was dropped that keeps
 * at least {@code gap} from every other element and stays inside the safe zones. When the nearest
 * way out is blocked by a screen edge it tries the other sides and corners, so an element dropped
 * close to an edge is never left overlapping its neighbour.
 */
final class HudGap {
    record Rect(int x, int y, int w, int h) {}

    private static final int ROUNDS = 4, MAX_CANDIDATES = 4000;

    private HudGap() {}

    /** @return {x, y} for a {@code w}×{@code h} element wanted at ({@code x}, {@code y}); bounds are for its top-left corner. */
    static int[] resolve(int x, int y, int w, int h, List<Rect> others, int gap, int minX, int maxX, int minY, int maxY) {
        maxX = Math.max(minX, maxX); maxY = Math.max(minY, maxY);
        int sx = Math.clamp(x, minX, maxX), sy = Math.clamp(y, minY, maxY);
        if (gap <= 0 || others.isEmpty()) return new int[]{sx, sy};
        Set<Long> seen = new LinkedHashSet<>();
        List<int[]> round = new ArrayList<>(List.of(new int[]{sx, sy}));
        seen.add(key(sx, sy));
        int[] best = null; long bestDistance = Long.MAX_VALUE;
        int[] leastBad = {sx, sy}; long leastOverlap = Long.MAX_VALUE;
        for (int r = 0; r <= ROUNDS && !round.isEmpty(); r++) {
            List<int[]> next = new ArrayList<>();
            for (int[] c : round) {
                long overlap = 0;
                for (Rect o : others) overlap += overlap(c[0], c[1], w, h, o, gap);
                long distance = (long) (c[0] - x) * (c[0] - x) + (long) (c[1] - y) * (c[1] - y);
                if (overlap == 0) { if (distance < bestDistance) { best = c; bestDistance = distance; } continue; }
                if (overlap < leastOverlap) { leastOverlap = overlap; leastBad = c; }
                if (r == ROUNDS) continue;
                for (Rect o : others) {
                    if (overlap(c[0], c[1], w, h, o, gap) == 0) continue;
                    int[][] pushes = {
                            {o.x() - gap - w, c[1]}, {o.x() + o.w() + gap, c[1]},
                            {c[0], o.y() - gap - h}, {c[0], o.y() + o.h() + gap}};
                    for (int[] p : pushes) {
                        if (p[0] < minX || p[0] > maxX || p[1] < minY || p[1] > maxY) continue;
                        if (seen.size() < MAX_CANDIDATES && seen.add(key(p[0], p[1]))) next.add(p);
                    }
                }
            }
            // Later rounds only move further away, so stop once a clean spot nearer than all of them exists.
            if (best != null) {
                boolean nearer = false;
                for (int[] p : next) if (dist(p, x, y) < bestDistance) { nearer = true; break; }
                if (!nearer) break;
            }
            round = next;
        }
        return best != null ? best : leastBad;
    }

    private static long dist(int[] p, int x, int y) { return (long) (p[0] - x) * (p[0] - x) + (long) (p[1] - y) * (p[1] - y); }

    private static long overlap(int x, int y, int w, int h, Rect o, int gap) {
        long ox = Math.min(x + w, o.x() + o.w() + gap) - Math.max(x, o.x() - gap);
        long oy = Math.min(y + h, o.y() + o.h() + gap) - Math.max(y, o.y() - gap);
        return ox > 0 && oy > 0 ? ox * oy : 0;
    }

    private static long key(int x, int y) { return ((long) x << 32) ^ (y & 0xffffffffL); }
}
