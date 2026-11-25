package dev.argus.client.utils.movement;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Where the body is on Baritone's route and where it goes next, for flight that stays on that line instead of aiming at
 * whichever later node it can see. It only ever searches a short way along the route from where it last was, so a later
 * part of the route that passes close by is never a shortcut. Positions are the centre of the body. Nodes count as the
 * centre of their block, which the pathfinder knows is open, and that block holds a gliding body with room to spare.
 * Ported from Comet's ElytraRouteFollower.
 */
public final class ElytraRouteFollower {
    /** A move in blocks for one tick. */
    public record Motion(double x, double y, double z) {
        public static final Motion ZERO = new Motion(0, 0, 0);

        public double length() {
            return Math.hypot(Math.hypot(x, y), z);
        }

        public Motion scaled(double factor) {
            return factor == 0 ? ZERO : new Motion(x * factor, y * factor, z * factor);
        }
    }

    /** Blocks the body may stray from the route when that saves stopping at a corner: a gliding body still fits inside the node's block. */
    public static final double EXACT = .2;
    /** Blocks ahead on the route to report as the heading, so the server's hidden glide is already turning before each corner. */
    static final double HEADING_AHEAD = 8;
    /** Blocks along the route searched for the body each tick, which is more than it can fly in one. */
    static final double WINDOW = 24;
    /** Further off the route than this and something moved the body, such as a setback, so the search goes wider. */
    static final double LOST = 1;
    /** Blocks along the route searched either way once lost. */
    static final double LOST_WINDOW = 128;
    /** Off the route, ways back to try after the first is blocked, as multiples of the distance off it, ahead along the route. */
    private static final double[] REJOIN_AHEAD = {0, 2, 4, -1};
    private static final double EPSILON = 1e-6;

    private List<? extends BlockPos> route;
    private BlockPos segmentStart, segmentEnd;
    /** The body is nearest to {@code segment}, the piece from that node to the next, {@code along} blocks into it. */
    private int segment;
    private double along, offRoute;

    public void reset() {
        route = null;
        segmentStart = segmentEnd = null;
        segment = 0;
        along = offRoute = 0;
    }

    /**
     * Updates where the body is on this route. A new route carries on from the same place when it still has the same nodes
     * there, because Baritone's next segment only adds to the end.
     *
     * @return false when there's no line to follow: fewer than two nodes
     */
    public boolean locate(double x, double y, double z, List<? extends BlockPos> path) {
        if (path.size() < 2) {
            reset();
            return false;
        }
        if (path != route) adopt(x, y, z, path);
        search(x, y, z);
        if (offRoute > LOST) searchWide(x, y, z);
        segmentStart = route.get(segment);
        segmentEnd = route.get(segment + 1);
        return true;
    }

    private void adopt(double x, double y, double z, List<? extends BlockPos> path) {
        boolean sameNodes = route != null && segment + 1 < path.size()
            && path.get(segment).equals(segmentStart) && path.get(segment + 1).equals(segmentEnd);
        route = path;
        if (sameNodes) return;
        // The earliest pass of about the nearest distance: the route may come back past here later on.
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i + 1 < path.size(); i++) best = Math.min(best, distanceToSegment(x, y, z, i));
        for (int i = 0; i + 1 < path.size(); i++) {
            if (distanceToSegment(x, y, z, i) > best + .5) continue;
            segment = i;
            break;
        }
        along = 0;
    }

    /** The nearest place a little way ahead of the last one. Ties stay on the earlier piece. */
    private void search(double x, double y, double z) {
        int bestSegment = segment;
        double best = Double.POSITIVE_INFINITY;
        double passed = -along;
        for (int i = segment; i + 1 < route.size() && passed <= WINDOW; i++) {
            double distance = distanceToSegment(x, y, z, i);
            if (distance < best - EPSILON) {
                best = distance;
                bestSegment = i;
            }
            passed += length(i);
        }
        place(x, y, z, bestSegment, best);
    }

    private void searchWide(double x, double y, double z) {
        int from = segment;
        for (double back = 0; from > 0 && back < LOST_WINDOW; from--) back += length(from - 1);
        int bestSegment = segment;
        double best = offRoute;
        double passed = 0;
        for (int i = from; i + 1 < route.size() && passed <= 2 * LOST_WINDOW; i++) {
            double distance = distanceToSegment(x, y, z, i);
            // Clearly nearer, so a route running alongside itself doesn't pull the body over to the other side.
            if (distance < best - .5) {
                best = distance;
                bestSegment = i;
            }
            passed += length(i);
        }
        place(x, y, z, bestSegment, best);
    }

    private void place(double x, double y, double z, int index, double distance) {
        segment = index;
        offRoute = distance;
        Vec3 a = node(index), b = node(index + 1);
        double length = a.distanceTo(b);
        along = length < EPSILON ? 0 : clamp(((x - a.x) * (b.x - a.x) + (y - a.y) * (b.y - a.y) + (z - a.z) * (b.z - a.z)) / length, length);
    }

    /** How far the body is from the route, as of {@link #locate}. */
    public double offRoute() {
        return offRoute;
    }

    /**
     * The straight move for this tick, at most {@code budget} long, ending on the route. It's normally up to the next
     * node. It goes past a node only when every node passed stays within {@code tolerance} of the line. Off the route,
     * it heads back to a point as far ahead as the distance off it, and no further than the end of this piece of route.
     * Zero at the end of the route.
     */
    public Motion next(double x, double y, double z, double budget, double tolerance) {
        if (route == null || budget <= 0) return Motion.ZERO;
        if (offRoute > tolerance) return towards(x, y, z, withinPiece(Math.max(offRoute, 1)), budget);
        Vec3 here = new Vec3(x, y, z);
        Vec3 end = null;
        // Measured along the route, from the place nearest the body.
        Vec3 from = pointOnPiece(segment, along);
        double used = 0;
        for (int i = segment + 1; i < route.size(); i++) {
            Vec3 corner = node(i);
            double piece = from.distanceTo(corner);
            if (used + piece >= budget) {
                Vec3 stop = piece < EPSILON ? corner : from.add(corner.subtract(from).scale((budget - used) / piece));
                if (keepsTo(here, stop, i - 1, tolerance)) end = stop;
                break;
            }
            if (!keepsTo(here, corner, i - 1, tolerance)) break;
            end = corner;
            used += piece;
            from = corner;
        }
        return end == null ? Motion.ZERO : limited(end.subtract(here), budget);
    }

    /** Another way back to the route for when {@link #next}'s is blocked: null once there are no more. Only off the route. */
    public Motion rejoin(double x, double y, double z, double budget, int attempt) {
        if (route == null || attempt >= REJOIN_AHEAD.length) return null;
        return towards(x, y, z, pointAhead(REJOIN_AHEAD[attempt] * Math.max(offRoute, 1)), budget);
    }

    /** True at the last node, where only Baritone can say what's next. */
    public boolean atEnd(double x, double y, double z) {
        return route != null && node(route.size() - 1).distanceToSqr(x, y, z) < 1e-4;
    }

    /** The rotation to report for this position: towards a point a little way along the route. Null with nowhere to look. */
    public ElytraPathAim.Aim aim(double x, double y, double z) {
        if (route == null) return null;
        Vec3 target = pointAhead(HEADING_AHEAD);
        double dx = target.x - x, dy = target.y - y, dz = target.z - z;
        if (dx * dx + dy * dy + dz * dz < 1e-4) return null;
        return new ElytraPathAim.Aim((float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }

    /** Whether the line from here to {@code end} stays this close to each node between the body and the piece ending at {@code last}. */
    private boolean keepsTo(Vec3 here, Vec3 end, int last, double tolerance) {
        for (int i = segment + 1; i <= last; i++) {
            if (distanceToLine(node(i), here, end) > tolerance) return false;
        }
        return true;
    }

    private Motion towards(double x, double y, double z, Vec3 target, double budget) {
        return limited(target.subtract(x, y, z), budget);
    }

    private Vec3 withinPiece(double ahead) {
        double length = length(segment);
        // At the end of this piece, the next piece is where the route goes on.
        if (along + EPSILON >= length && segment + 2 < route.size()) return pointOnPiece(segment + 1, Math.min(ahead, length(segment + 1)));
        return pointOnPiece(segment, Math.min(along + ahead, length));
    }

    /** The point this many blocks along the route from the place nearest the body, stopping at either end of the route. */
    private Vec3 pointAhead(double ahead) {
        int index = segment;
        double offset = along + ahead;
        while (offset < 0 && index > 0) offset += length(--index);
        while (offset > length(index) && index + 2 < route.size()) offset -= length(index++);
        return pointOnPiece(index, clamp(offset, length(index)));
    }

    private Vec3 pointOnPiece(int index, double offset) {
        Vec3 a = node(index), b = node(index + 1);
        double length = a.distanceTo(b);
        return length < EPSILON ? a : a.add(b.subtract(a).scale(offset / length));
    }

    private Vec3 node(int index) {
        BlockPos pos = route.get(index);
        return new Vec3(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
    }

    private double length(int index) {
        return node(index).distanceTo(node(index + 1));
    }

    private double distanceToSegment(double x, double y, double z, int index) {
        Vec3 a = node(index), b = node(index + 1);
        Vec3 way = b.subtract(a);
        double lengthSquared = way.lengthSqr();
        double t = lengthSquared < EPSILON ? 0 : Math.max(0, Math.min(1, ((x - a.x) * way.x + (y - a.y) * way.y + (z - a.z) * way.z) / lengthSquared));
        double dx = a.x + way.x * t - x, dy = a.y + way.y * t - y, dz = a.z + way.z * t - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double distanceToLine(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 way = b.subtract(a);
        double lengthSquared = way.lengthSqr();
        double t = lengthSquared < EPSILON ? 0 : Math.max(0, Math.min(1, point.subtract(a).dot(way) / lengthSquared));
        return a.add(way.scale(t)).distanceTo(point);
    }

    private static Motion limited(Vec3 way, double budget) {
        double length = way.length();
        if (length < EPSILON) return Motion.ZERO;
        double scale = length > budget ? budget / length : 1;
        return new Motion(way.x * scale, way.y * scale, way.z * scale);
    }

    private static double clamp(double value, double maximum) {
        return Math.max(0, Math.min(value, maximum));
    }
}
