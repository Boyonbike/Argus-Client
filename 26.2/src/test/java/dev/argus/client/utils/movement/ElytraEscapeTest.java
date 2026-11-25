package dev.argus.client.utils.movement;

import dev.argus.client.utils.movement.ElytraEscape.Cell;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ElytraEscapeTest {
    /** Everything is solid unless set, as unloaded terrain counts. */
    private static final class World implements ElytraEscape.Cells {
        final Map<BlockPos, Cell> cells = new HashMap<>();
        World fill(int x0, int y0, int z0, int x1, int y1, int z1, Cell cell) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) cells.put(new BlockPos(x, y, z), cell);
            return this;
        }
        @Override public Cell at(int x, int y, int z) { return cells.getOrDefault(new BlockPos(x, y, z), Cell.SOLID); }
    }

    private static final BlockPos START = new BlockPos(0, 10, 0);

    @Test void anOpenSeaGoesStraightUp() {
        World world = new World().fill(-5, 0, -5, 5, 20, 5, Cell.FLUID).fill(-5, 21, -5, 5, 40, 5, Cell.OPEN);
        List<BlockPos> route = ElytraEscape.route(START, world);
        assertEquals(new BlockPos(0, 21, 0), route.get(route.size() - 1));
        assertEquals(12, route.size());
        for (BlockPos pos : route) assertEquals(0, pos.getX());
    }

    @Test void aRoofedPoolLeavesSidewaysToTheOpening() {
        World world = new World().fill(0, 10, 0, 8, 11, 0, Cell.FLUID).fill(8, 12, 0, 8, 20, 0, Cell.OPEN);
        List<BlockPos> route = ElytraEscape.route(START, world);
        assertEquals(new BlockPos(8, 12, 0), route.get(route.size() - 1));
        assertEquals(START, route.get(0));
    }

    @Test void lavaAndWaterAreTheSameToTheRoute() {
        World world = new World().fill(0, 10, 0, 0, 12, 0, Cell.FLUID).fill(0, 13, 0, 0, 30, 0, Cell.OPEN);
        assertEquals(new BlockPos(0, 13, 0), ElytraEscape.route(START, world).get(3));
    }

    @Test void aGapOneBlockHighDoesNotFitTheBody() {
        World world = new World().fill(0, 10, 0, 8, 10, 0, Cell.FLUID).fill(8, 11, 0, 8, 20, 0, Cell.OPEN);
        assertNull(ElytraEscape.route(START, world));
    }

    @Test void tooLittleHeadroomIsNotAnEscape() {
        World world = new World().fill(0, 10, 0, 0, 11, 0, Cell.FLUID).fill(0, 12, 0, 0, 15, 0, Cell.OPEN);
        assertNull(ElytraEscape.route(START, world));
        world.fill(0, 16, 0, 0, 16, 0, Cell.OPEN);
        assertEquals(new BlockPos(0, 12, 0), ElytraEscape.route(START, world).get(2));
    }

    @Test void aSealedPoolAndAPlaceBeyondReachHaveNoRoute() {
        assertNull(ElytraEscape.route(START, new World().fill(-3, 10, -3, 3, 12, 3, Cell.FLUID)));
        World far = new World().fill(0, 10, 0, 60, 11, 0, Cell.FLUID).fill(60, 12, 0, 60, 20, 0, Cell.OPEN);
        assertNull(ElytraEscape.route(START, far));
    }

    @Test void standingInTheOpenIsAlreadyThere() {
        World world = new World().fill(0, 10, 0, 0, 20, 0, Cell.OPEN);
        assertEquals(List.of(START), ElytraEscape.route(START, world));
    }

    @Test void aHugeSealedLakeStopsSearchingInsteadOfFreezingTheGame() {
        World world = new World().fill(-40, 0, -40, 40, 40, 40, Cell.FLUID);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> assertNull(ElytraEscape.route(START, world)));
    }
}
