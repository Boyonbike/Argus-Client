package dev.argus.client.utils.movement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * The shortest way out of water or lava to somewhere the wings can open, which a player can follow through liquid or air
 * but not through blocks. It only knows the terrain it is told about, so anything unloaded should count as solid.
 */
/* Ported from Comet's ElytraEscape. */
public final class ElytraEscape {
    public enum Cell { OPEN, FLUID, SOLID }

    public interface Cells {
        Cell at(int x, int y, int z);
    }

    /** Feet to head, then the 3 blocks of headroom a take-off wants. */
    static final int OPEN_CELLS = 5;
    private static final int BODY_CELLS = 2;
    private static final int REACH = 48;
    private static final int MAX_VISITED = 24000;
    // Straight up first: a route of equal length then prefers leaving the liquid soonest.
    private static final int[][] STEPS = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};

    private ElytraEscape() {}

    /** From the start to the nearest open place, both included, or null when none is within reach. */
    public static List<BlockPos> route(BlockPos start, Cells cells) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        BlockPos origin = start.immutable();
        queue.add(origin);
        cameFrom.put(origin, origin);
        while (!queue.isEmpty() && cameFrom.size() < MAX_VISITED) {
            BlockPos pos = queue.poll();
            if (open(pos, cells)) return path(pos, cameFrom);
            for (int[] step : STEPS) {
                BlockPos next = pos.offset(step[0], step[1], step[2]);
                if (Math.abs(next.getX() - origin.getX()) > REACH || Math.abs(next.getY() - origin.getY()) > REACH
                    || Math.abs(next.getZ() - origin.getZ()) > REACH || cameFrom.containsKey(next) || !fits(next, cells)) continue;
                cameFrom.put(next, pos);
                queue.add(next);
            }
        }
        return null;
    }

    private static boolean open(BlockPos pos, Cells cells) {
        for (int up = 0; up < OPEN_CELLS; up++) {
            if (cells.at(pos.getX(), pos.getY() + up, pos.getZ()) != Cell.OPEN) return false;
        }
        return true;
    }

    private static boolean fits(BlockPos pos, Cells cells) {
        for (int up = 0; up < BODY_CELLS; up++) {
            if (cells.at(pos.getX(), pos.getY() + up, pos.getZ()) == Cell.SOLID) return false;
        }
        return true;
    }

    private static List<BlockPos> path(BlockPos end, Map<BlockPos, BlockPos> cameFrom) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos pos = end;
        while (true) {
            path.add(pos);
            BlockPos previous = cameFrom.get(pos);
            if (previous.equals(pos)) break;
            pos = previous;
        }
        Collections.reverse(path);
        return path;
    }
}
