package dev.argus.client.utils.movement;

import dev.argus.client.utils.movement.ElytraRouteFollower.Motion;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ElytraRouteFollowerTest {
    private static List<BlockPos> route(int... xyz) {
        List<BlockPos> nodes = new ArrayList<>();
        for (int i = 0; i < xyz.length; i += 3) nodes.add(new BlockPos(xyz[i], xyz[i + 1], xyz[i + 2]));
        return nodes;
    }

    /** Distance from a point to the nearest part of a route. */
    private static double offRoute(double x, double y, double z, List<BlockPos> nodes) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i + 1 < nodes.size(); i++) {
            double ax = nodes.get(i).getX() + .5, ay = nodes.get(i).getY() + .5, az = nodes.get(i).getZ() + .5;
            double bx = nodes.get(i + 1).getX() + .5 - ax, by = nodes.get(i + 1).getY() + .5 - ay, bz = nodes.get(i + 1).getZ() + .5 - az;
            double t = Math.max(0, Math.min(1, ((x - ax) * bx + (y - ay) * by + (z - az) * bz) / (bx * bx + by * by + bz * bz)));
            best = Math.min(best, Math.sqrt(Math.pow(ax + bx * t - x, 2) + Math.pow(ay + by * t - y, 2) + Math.pow(az + bz * t - z, 2)));
        }
        return best;
    }

    /** Flies a route tick by tick and says how far off it any point of any tick's straight move got. */
    private static final class Flown {
        double x, y, z, worst;
        int ticks;
        final ElytraRouteFollower follower = new ElytraRouteFollower();

        Flown(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        void fly(List<BlockPos> nodes, double speed, double tolerance, int maximumTicks) {
            for (int tick = 0; tick < maximumTicks; tick++) {
                assertTrue(follower.locate(x, y, z, nodes));
                Motion move = follower.next(x, y, z, speed, tolerance);
                assertTrue(move.length() <= speed + 1e-9, "moved further than the speed allows");
                for (double t = .1; t <= 1; t += .1) {
                    worst = Math.max(worst, offRoute(x + move.x() * t, y + move.y() * t, z + move.z() * t, nodes));
                }
                x += move.x();
                y += move.y();
                z += move.z();
                ticks++;
                follower.locate(x, y, z, nodes);
                if (follower.atEnd(x, y, z)) return;
            }
            fail("didn't reach the end of the route, stopped at " + x + " " + y + " " + z);
        }
    }

    @Test void noLineToFollowWithoutTwoNodes() {
        ElytraRouteFollower follower = new ElytraRouteFollower();
        assertFalse(follower.locate(0, 0, 0, List.of()));
        assertFalse(follower.locate(0, 0, 0, route(0, 64, 0)));
        assertEquals(Motion.ZERO, follower.next(0, 0, 0, 5, ElytraRouteFollower.EXACT));
        assertNull(follower.aim(0, 0, 0));
    }

    @Test void aStraightPieceIsFlownAtFullSpeed() {
        List<BlockPos> nodes = route(0, 64, 0, 0, 64, 100);
        Flown flight = new Flown(.5, 64.5, .5);
        flight.fly(nodes, 5, ElytraRouteFollower.EXACT, 1000);
        assertEquals(20, flight.ticks);
        assertEquals(0, flight.worst, 1e-6);
    }

    /** The difference from Baritone's glide: with a clear line to the end, the corner still gets flown. */
    @Test void aCornerIsFlownEvenWithAClearLinePastIt() {
        List<BlockPos> nodes = route(0, 64, 0, 0, 64, 40, 40, 64, 40);
        Flown flight = new Flown(.5, 64.5, .5);
        flight.fly(nodes, 15, ElytraRouteFollower.EXACT, 1000);
        assertEquals(0, flight.worst, 1e-6, "stays exactly on the line");
    }

    @Test void aWindingRouteInEveryDirectionStaysWithinTheTolerance() {
        List<BlockPos> nodes = route(0, 64, 0, 4, 64, 0, 4, 68, 0, 4, 68, 4, 8, 72, 8, 8, 72, 16, 0, 60, 16, 0, 60, 32, -16, 60, 32, -16, 44, 32);
        for (double speed : new double[]{.3, 1.4, 5, 17}) {
            Flown flight = new Flown(.5, 64.5, .5);
            flight.fly(nodes, speed, ElytraRouteFollower.EXACT, 2000);
            assertTrue(flight.worst <= ElytraRouteFollower.EXACT + 1e-6, "strayed " + flight.worst + " at speed " + speed);
        }
    }

    @Test void nodesOnTheSameLineDoNotSlowFlight() {
        List<BlockPos> nodes = new ArrayList<>();
        for (int i = 0; i <= 100; i++) nodes.add(new BlockPos(0, 64, i));
        Flown flight = new Flown(.5, 64.5, .5);
        flight.fly(nodes, 10, ElytraRouteFollower.EXACT, 1000);
        assertEquals(10, flight.ticks, "ten blocks a tick along a hundred blocks");
    }

    /** Stopping at each corner of a small staircase costs speed, which the tolerance can buy back by cutting them. */
    @Test void moreToleranceCutsCornersForSpeed() {
        List<BlockPos> nodes = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            nodes.add(new BlockPos(4 * i, 64, 4 * i));
            nodes.add(new BlockPos(4 * i + 4, 64, 4 * i));
        }
        Flown exact = new Flown(.5, 64.5, .5);
        exact.fly(nodes, 16, ElytraRouteFollower.EXACT, 1000);
        Flown loose = new Flown(.5, 64.5, .5);
        loose.fly(nodes, 16, 4, 1000);
        assertTrue(exact.worst <= ElytraRouteFollower.EXACT + 1e-6);
        assertTrue(loose.worst <= 4 + 1e-6, "strayed " + loose.worst);
        assertTrue(loose.ticks * 2 < exact.ticks, "exact " + exact.ticks + " ticks, loose " + loose.ticks);
    }

    /** A route out and back past a wall: the way back is right beside the body, and through that wall. */
    @Test void aLaterPartOfTheRoutePassingCloseIsNotAShortcut() {
        List<BlockPos> nodes = route(0, 64, 0, 0, 64, 100, 3, 64, 100, 3, 64, 0);
        ElytraRouteFollower follower = new ElytraRouteFollower();
        double x = .5, z = .5;
        for (int tick = 0; tick < 19; tick++) {
            follower.locate(x, 64.5, z, nodes);
            Motion move = follower.next(x, 64.5, z, 5, ElytraRouteFollower.EXACT);
            assertEquals(0, move.x(), 1e-9, "stays on the way out");
            z += move.z();
        }
        assertEquals(95.5, z, 1e-6);
    }

    /** A setback puts the body on a part of the route already flown: it flies that part again, rather than heading straight back. */
    @Test void afterASetbackItComesBackAlongTheRoute() {
        List<BlockPos> nodes = route(0, 64, 0, 0, 64, 20, 20, 64, 20);
        ElytraRouteFollower follower = new ElytraRouteFollower();
        follower.locate(10.5, 64.5, 20.5, nodes);
        assertEquals(0, follower.offRoute(), 1e-9);
        follower.locate(.5, 64.5, 10.5, nodes);
        assertEquals(0, follower.offRoute(), 1e-9, "found again on the first piece");
        Motion move = follower.next(.5, 64.5, 10.5, 2, ElytraRouteFollower.EXACT);
        assertEquals(0, move.x(), 1e-9);
        assertEquals(2, move.z(), 1e-9);
    }

    @Test void offTheRouteItHeadsBackAndThenFollows() {
        List<BlockPos> nodes = route(0, 64, 0, 0, 64, 100);
        ElytraRouteFollower follower = new ElytraRouteFollower();
        double x = 4.5, z = 10.5;
        follower.locate(x, 64.5, z, nodes);
        assertEquals(4, follower.offRoute(), 1e-9);
        for (int tick = 0; tick < 10; tick++) {
            follower.locate(x, 64.5, z, nodes);
            Motion move = follower.next(x, 64.5, z, 1, ElytraRouteFollower.EXACT);
            assertEquals(1, move.length(), 1e-6);
            assertTrue(move.z() > 0, "never back along the route");
            x += move.x();
            z += move.z();
        }
        follower.locate(x, 64.5, z, nodes);
        assertEquals(0, follower.offRoute(), 1e-6, "back on the route");
        assertTrue(z < 25, "without skipping much of it");
    }

    @Test void offTheRouteItDoesNotHeadPastTheNextCorner() {
        List<BlockPos> nodes = route(0, 64, 0, 0, 64, 10, 40, 64, 10);
        ElytraRouteFollower follower = new ElytraRouteFollower();
        follower.locate(-6.5, 64.5, 9.5, nodes);
        Motion move = follower.next(-6.5, 64.5, 9.5, 100, ElytraRouteFollower.EXACT);
        assertEquals(.5, -6.5 + move.x(), 1e-6);
        assertEquals(10.5, 9.5 + move.z(), 1e-6);
    }

    @Test void otherWaysBackRunOut() {
        List<BlockPos> nodes = route(0, 64, 0, 0, 64, 100);
        ElytraRouteFollower follower = new ElytraRouteFollower();
        follower.locate(4.5, 64.5, 50.5, nodes);
        int ways = 0;
        while (follower.rejoin(4.5, 64.5, 50.5, 1, ways) != null) ways++;
        assertTrue(ways >= 2);
    }

    @Test void theNextSegmentCarriesOnFromTheSamePlace() {
        // The route comes back past the start of its first piece, so a search from scratch could pick the wrong pass.
        List<BlockPos> first = route(0, 64, 0, 0, 64, 40, 8, 64, 40, 8, 64, 0);
        ElytraRouteFollower follower = new ElytraRouteFollower();
        follower.locate(8.5, 64.5, 2.5, first);
        Motion before = follower.next(8.5, 64.5, 2.5, 1, ElytraRouteFollower.EXACT);
        List<BlockPos> longer = new ArrayList<>(first);
        longer.add(new BlockPos(30, 64, 0));
        follower.locate(8.5, 64.5, 2.5, longer);
        assertEquals(before, follower.next(8.5, 64.5, 2.5, 1, ElytraRouteFollower.EXACT));
    }

    @Test void aRouteFromSomewhereElseIsPickedUpWhereItPassesTheBody() {
        ElytraRouteFollower follower = new ElytraRouteFollower();
        follower.locate(.5, 64.5, 50.5, route(0, 64, 0, 0, 64, 100));
        // Passes here twice: the earlier pass is the one to fly from, so nothing is skipped.
        List<BlockPos> other = route(100, 64, 50, 0, 64, 50, 0, 64, 60, -100, 64, 60, -100, 64, 50, 0, 64, 50);
        follower.locate(.5, 64.5, 50.5, other);
        Motion move = follower.next(.5, 64.5, 50.5, 2, ElytraRouteFollower.EXACT);
        assertEquals(2, move.z(), 1e-6, "on to the second node, not on past the last");
    }

    @Test void itStopsAtTheEndOfTheRoute() {
        List<BlockPos> nodes = route(0, 64, 0, 0, 64, 3);
        ElytraRouteFollower follower = new ElytraRouteFollower();
        follower.locate(.5, 64.5, 2.5, nodes);
        assertEquals(1, follower.next(.5, 64.5, 2.5, 10, ElytraRouteFollower.EXACT).length(), 1e-6);
        follower.locate(.5, 64.5, 3.5, nodes);
        assertTrue(follower.atEnd(.5, 64.5, 3.5));
        assertEquals(Motion.ZERO, follower.next(.5, 64.5, 3.5, 10, ElytraRouteFollower.EXACT));
    }

    @Test void theHeadingLooksAheadAlongTheRoute() {
        List<BlockPos> nodes = route(0, 64, 0, 0, 64, 100);
        ElytraRouteFollower follower = new ElytraRouteFollower();
        follower.locate(.5, 64.5, 20.5, nodes);
        assertEquals(0, follower.aim(.5, 64.5, 20.5).yaw(), 1e-4);
        assertEquals(0, follower.aim(.5, 64.5, 20.5).pitch(), 1e-4);
        List<BlockPos> corner = route(0, 64, 0, 0, 64, 10, -40, 64, 10);
        follower = new ElytraRouteFollower();
        follower.locate(.5, 64.5, 9.5, corner);
        assertTrue(follower.aim(.5, 64.5, 9.5).yaw() > 45, "already turning for the corner");
    }
}
