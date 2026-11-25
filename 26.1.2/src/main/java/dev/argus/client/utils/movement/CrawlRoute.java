package dev.argus.client.utils.movement;

import dev.argus.client.utils.movement.ElytraEscape.Cell;
import dev.argus.client.utils.movement.ElytraEscape.Cells;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * The shortest crawl to somewhere a player can stand, for a player stuck crawling under a ceiling, where Baritone can't
 * walk because it only knows a two-block-tall player. A crawling body fills one block, moves sideways and drops, but
 * can't climb. Anything the caller can't see, such as unloaded terrain or lava, should count as solid.
 */
public final class CrawlRoute {
    static final int REACH = 24;
    private static final int MAX_VISITED = 6000;
    private static final int[][] STEPS = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};

    private CrawlRoute() {}

    /** From the start to the nearest place with room to stand, both included; just the start when there's room already; null when none is within reach. */
    public static List<BlockPos> route(BlockPos start, Cells cells) {
        BlockPos origin = start.immutable();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        queue.add(origin);
        cameFrom.put(origin, origin);
        while (!queue.isEmpty() && cameFrom.size() < MAX_VISITED) {
            BlockPos pos = queue.poll();
            if (standing(pos, cells)) return path(pos, cameFrom);
            for (int[] step : STEPS) {
                BlockPos next = pos.offset(step[0], step[1], step[2]);
                if (Math.abs(next.getX() - origin.getX()) > REACH || Math.abs(next.getY() - origin.getY()) > REACH
                    || Math.abs(next.getZ() - origin.getZ()) > REACH || cameFrom.containsKey(next)
                    || cells.at(next.getX(), next.getY(), next.getZ()) == Cell.SOLID) continue;
                cameFrom.put(next, pos);
                queue.add(next);
            }
        }
        return null;
    }

    /** Feet and head both clear, on something to stand on: Baritone can walk from here. */
    static boolean standing(BlockPos pos, Cells cells) {
        return cells.at(pos.getX(), pos.getY(), pos.getZ()) != Cell.SOLID
            && cells.at(pos.getX(), pos.getY() + 1, pos.getZ()) != Cell.SOLID
            && cells.at(pos.getX(), pos.getY() - 1, pos.getZ()) == Cell.SOLID;
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
