package dev.argus.client.utils.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.argus.client.utils.movement.ElytraEscape.Cell;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class CrawlRouteTest {
    /** Everything is solid unless set, as unloaded terrain counts. */
    private static final class World implements ElytraEscape.Cells {
        final Map<BlockPos, Cell> cells = new HashMap<>();

        World fill(int x0, int y0, int z0, int x1, int y1, int z1, Cell cell) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) cells.put(new BlockPos(x, y, z), cell);
            return this;
        }

        @Override
        public Cell at(int x, int y, int z) {
            return cells.getOrDefault(new BlockPos(x, y, z), Cell.SOLID);
        }
    }

    private static final BlockPos START = new BlockPos(0, 10, 0);

    @Test
    void crawlsAlongATunnelToWhereItOpensUp() {
        // A one-high tunnel along x that opens to two high at x = 6.
        World world = new World().fill(0, 10, 0, 6, 10, 0, Cell.OPEN).fill(6, 11, 0, 6, 11, 0, Cell.OPEN);
        List<BlockPos> route = CrawlRoute.route(START, world);
        assertEquals(START, route.get(0));
        assertEquals(new BlockPos(6, 10, 0), route.get(route.size() - 1));
        assertEquals(7, route.size());
    }

    @Test
    void alreadyStandingIsJustTheStart() {
        World world = new World().fill(0, 10, 0, 0, 11, 0, Cell.OPEN);
        assertEquals(List.of(START), CrawlRoute.route(START, world));
    }

    @Test
    void dropsDownButNeverClimbs() {
        // Standing room one block up is out of reach; standing room down a drop isn't.
        World world = new World().fill(0, 10, 0, 3, 10, 0, Cell.OPEN).fill(-1, 10, 0, -1, 12, 0, Cell.OPEN)
            .fill(3, 8, 0, 3, 9, 0, Cell.OPEN);
        world.fill(-1, 10, 0, -1, 10, 0, Cell.SOLID);
        List<BlockPos> route = CrawlRoute.route(START, world);
        assertEquals(new BlockPos(3, 8, 0), route.get(route.size() - 1));
    }

    @Test
    void noStandingRoomInReachIsNull() {
        World world = new World().fill(-5, 10, -5, 5, 10, 5, Cell.OPEN);
        assertNull(CrawlRoute.route(START, world));
    }
}
