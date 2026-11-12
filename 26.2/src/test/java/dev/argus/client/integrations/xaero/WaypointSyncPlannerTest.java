package dev.argus.client.integrations.xaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.argus.client.integrations.xaero.WaypointSyncPlanner.ArgusAdd;
import dev.argus.client.integrations.xaero.WaypointSyncPlanner.ArgusPoint;
import dev.argus.client.integrations.xaero.WaypointSyncPlanner.ArgusRemove;
import dev.argus.client.integrations.xaero.WaypointSyncPlanner.ArgusUpdate;
import dev.argus.client.integrations.xaero.WaypointSyncPlanner.ExternalPoint;
import dev.argus.client.integrations.xaero.WaypointSyncPlanner.Link;
import dev.argus.client.integrations.xaero.WaypointSyncPlanner.Plan;
import dev.argus.client.integrations.xaero.WaypointSyncPlanner.World;
import dev.argus.client.integrations.xaero.WaypointSyncPlanner.XaeroAdd;
import dev.argus.client.integrations.xaero.WaypointSyncPlanner.XaeroRemove;
import dev.argus.client.integrations.xaero.WaypointSyncPlanner.XaeroUpdate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WaypointSyncPlannerTest {
    private static final String SERVER = "6b6t.org", OW = "overworld", NETHER = "nether", WORLD = "Multiplayer_6b6t/dim%0/mw$default",
            NWORLD = "Multiplayer_6b6t/dim%-1/mw$default", SET = "gui.xaero_default";

    private static ArgusPoint argus(String name, String dim, int x, int y, int z) { return new ArgusPoint(name, dim, x, y, z, true); }
    private static ExternalPoint xaero(String name, int x, int y, int z) { return new ExternalPoint(name, SET, x, y, z, true, new Object()); }
    private static World world(ExternalPoint... points) { return new World(OW, WORLD, List.of(points)); }
    private static Link link(String name, int x, int y, int z) { return new Link(SERVER, OW, WORLD, name, name, SET, x, y, z, true); }

    @Test
    void newArgusWaypointIsCopiedToXaero() {
        Plan plan = WaypointSyncPlanner.plan(SERVER, List.of(argus("Base", OW, 1, 2, 3)), List.of(world()), List.of());
        assertEquals(List.of(new XaeroAdd(OW, WORLD, "Base", 1, 2, 3, true)), plan.ops());
    }

    @Test
    void newXaeroWaypointIsCopiedToArgusAndLinked() {
        ExternalPoint x = xaero("Farm", 10, 64, -5);
        Plan plan = WaypointSyncPlanner.plan(SERVER, List.of(), List.of(world(x)), List.of());
        assertEquals(List.of(new ArgusAdd("Farm", OW, 10, 64, -5, true, "Farm", SET, WORLD)), plan.ops());
        assertEquals(List.of(link("Farm", 10, 64, -5)), plan.links());
    }

    @Test
    void inSyncDoesNothing() {
        ExternalPoint x = xaero("Base", 1, 2, 3);
        Plan plan = WaypointSyncPlanner.plan(SERVER, List.of(argus("Base", OW, 1, 2, 3)), List.of(world(x)), List.of(link("Base", 1, 2, 3)));
        assertTrue(plan.isEmpty());
        assertEquals(List.of(link("Base", 1, 2, 3)), plan.links());
    }

    @Test
    void deletedInXaeroIsDeletedInArgus() {
        Plan plan = WaypointSyncPlanner.plan(SERVER, List.of(argus("Base", OW, 1, 2, 3), argus("Farm", OW, 9, 9, 9)),
                List.of(world(xaero("Farm", 9, 9, 9))), List.of(link("Base", 1, 2, 3), link("Farm", 9, 9, 9)));
        assertEquals(List.of(new ArgusRemove("Base")), plan.ops());
        assertEquals(List.of(link("Farm", 9, 9, 9)), plan.links());
    }

    @Test
    void deletedInArgusIsDeletedInXaero() {
        ExternalPoint base = xaero("Base", 1, 2, 3);
        Plan plan = WaypointSyncPlanner.plan(SERVER, List.of(argus("Farm", OW, 9, 9, 9)), List.of(world(base, xaero("Farm", 9, 9, 9))),
                List.of(link("Base", 1, 2, 3), link("Farm", 9, 9, 9)));
        assertEquals(List.of(new XaeroRemove(OW, base.handle())), plan.ops());
    }

    @Test
    void movedInXaeroMovesArgus() {
        Plan plan = WaypointSyncPlanner.plan(SERVER, List.of(argus("Base", OW, 1, 2, 3)), List.of(world(xaero("Base", 50, 70, 50))), List.of(link("Base", 1, 2, 3)));
        assertEquals(List.of(new ArgusUpdate("Base", 50, 70, 50, true)), plan.ops());
        assertEquals(List.of(link("Base", 50, 70, 50)), plan.links());
    }

    @Test
    void hiddenInArgusIsDisabledInXaero() {
        ExternalPoint x = xaero("Base", 1, 2, 3);
        Plan plan = WaypointSyncPlanner.plan(SERVER, List.of(new ArgusPoint("Base", OW, 1, 2, 3, false)), List.of(world(x)), List.of(link("Base", 1, 2, 3)));
        assertEquals(List.of(new XaeroUpdate(OW, x.handle(), 1, 2, 3, false)), plan.ops());
    }

    @Test
    void xaeroWinsWhenBothChanged() {
        Plan plan = WaypointSyncPlanner.plan(SERVER, List.of(argus("Base", OW, 5, 5, 5)), List.of(world(xaero("Base", 7, 7, 7))), List.of(link("Base", 1, 2, 3)));
        assertEquals(List.of(new ArgusUpdate("Base", 7, 7, 7, true)), plan.ops());
    }

    @Test
    void existingSameNamePairsInsteadOfDuplicatingAndArgusPositionWins() {
        ExternalPoint x = xaero("Base", 100, 64, 100);
        Plan plan = WaypointSyncPlanner.plan(SERVER, List.of(argus("base", OW, 101, 64, 100)), List.of(world(x)), List.of());
        assertEquals(List.of(new XaeroUpdate(OW, x.handle(), 101, 64, 100, true)), plan.ops());
        assertEquals(1, plan.links().size());
    }

    @Test
    void sameNameInAnotherDimensionGetsASuffix() {
        World nether = new World(NETHER, NWORLD, List.of(xaero("Base", 12, 100, 12)));
        Plan plan = WaypointSyncPlanner.plan(SERVER, List.of(argus("Base", OW, 1, 2, 3)), List.of(world(xaero("Base", 1, 2, 3)), nether),
                List.of(link("Base", 1, 2, 3)));
        assertEquals(List.of(new ArgusAdd("Base (Nether)", NETHER, 12, 100, 12, true, "Base", SET, NWORLD)), plan.ops());
    }

    @Test
    void unloadedXaeroWorldNeverCausesMassDeletion() {
        Plan plan = WaypointSyncPlanner.plan(SERVER, List.of(argus("Base", OW, 1, 2, 3), argus("Farm", OW, 9, 9, 9)), List.of(world()),
                List.of(link("Base", 1, 2, 3), link("Farm", 9, 9, 9)));
        assertTrue(plan.isEmpty());
        assertEquals(2, plan.links().size());
    }

    @Test
    void anotherXaeroSubWorldLeavesLinksAlone() {
        World other = new World(OW, "Multiplayer_6b6t/dim%0/mw$other", List.of());
        Plan plan = WaypointSyncPlanner.plan(SERVER, List.of(argus("Base", OW, 1, 2, 3)), List.of(other), List.of(link("Base", 1, 2, 3)));
        assertTrue(plan.isEmpty(), "must neither delete nor re-export into the other sub-world: " + plan.ops());
        assertEquals(List.of(link("Base", 1, 2, 3)), plan.links());
    }

    @Test
    void dimensionWithoutXaeroWorldWaits() {
        Plan plan = WaypointSyncPlanner.plan(SERVER, List.of(argus("Portal", NETHER, 1, 2, 3)), List.of(world()), List.of());
        assertTrue(plan.isEmpty());
    }

    @Test
    void uniqueNameCountsUp() {
        Set<String> taken = new HashSet<>(List.of("base", "base (nether)"));
        assertEquals("Base (Nether) 2", WaypointSyncPlanner.uniqueName("Base", NETHER, taken));
    }
}
