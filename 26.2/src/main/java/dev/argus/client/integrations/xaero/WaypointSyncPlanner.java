package dev.argus.client.integrations.xaero;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Works out how to bring Argus's waypoints and Xaero's Minimap waypoints for one server back in step.
 *
 * <p>Both sides are compared with the {@link Link links} recorded at the last sync, which is what tells an edit or a
 * deletion apart from a waypoint that was never synced:
 * <ul>
 *   <li>linked on both sides: whichever side changed since the last sync is copied to the other (Xaero wins if both did);</li>
 *   <li>linked but gone from one side: it was deleted there, so it is deleted from the other side too;</li>
 *   <li>not linked: it is new, so it is copied to the other side (a same-named waypoint in the same dimension on the
 *       other side is paired with it instead of duplicated, keeping Argus's position).</li>
 * </ul>
 * Coordinates are compared as block positions: Argus keeps fractions, Xaero keeps whole blocks.
 *
 * <p>A Xaero world is skipped when it isn't the one a link was made in (another of Xaero's sub-worlds is in use),
 * and when none of the waypoints linked to it can be found, which means Xaero hasn't loaded it yet rather than that
 * every one was deleted.
 */
public final class WaypointSyncPlanner {
    /** An Argus waypoint, at block precision. */
    public record ArgusPoint(String name, String dimension, int x, int y, int z, boolean enabled) {}

    /** A Xaero waypoint; {@code handle} is Xaero's own object, passed back when it is changed. */
    public record ExternalPoint(String name, String set, int x, int y, int z, boolean enabled, Object handle) {}

    /** The Xaero world used for one dimension; {@code id} tells Xaero's sub-worlds apart. */
    public record World(String dimension, String id, List<ExternalPoint> points) {}

    /** A waypoint that is synced: what each side calls it and where it was at the last sync. */
    public record Link(String server, String dimension, String world, String argusName, String xaeroName, String set,
                       int x, int y, int z, boolean enabled) {
        boolean sameAs(int x, int y, int z, boolean enabled) { return this.x == x && this.y == y && this.z == z && this.enabled == enabled; }
        Link at(int x, int y, int z, boolean enabled) { return new Link(server, dimension, world, argusName, xaeroName, set, x, y, z, enabled); }
    }

    public sealed interface Op permits ArgusAdd, ArgusUpdate, ArgusRemove, XaeroAdd, XaeroUpdate, XaeroRemove {}
    public record ArgusAdd(String name, String dimension, int x, int y, int z, boolean enabled, String xaeroName, String set, String world) implements Op {}
    public record ArgusUpdate(String name, int x, int y, int z, boolean enabled) implements Op {}
    public record ArgusRemove(String name) implements Op {}
    /** Creates the waypoint in Xaero; whoever applies it records the link, since only Xaero knows which set it lands in. */
    public record XaeroAdd(String dimension, String world, String name, int x, int y, int z, boolean enabled) implements Op {}
    public record XaeroUpdate(String dimension, Object handle, int x, int y, int z, boolean enabled) implements Op {}
    public record XaeroRemove(String dimension, Object handle) implements Op {}

    /** What to do, and the links for this server afterwards (not counting the ones {@link XaeroAdd} makes). */
    public record Plan(List<Op> ops, List<Link> links) {
        public boolean isEmpty() { return ops.isEmpty(); }
    }

    private WaypointSyncPlanner() {}

    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }

    public static Plan plan(String server, Collection<ArgusPoint> argus, Collection<World> worlds, Collection<Link> links) {
        List<Op> ops = new ArrayList<>();
        List<Link> next = new ArrayList<>();
        Map<String, World> byDimension = new HashMap<>();
        for (World world : worlds) byDimension.put(world.dimension(), world);

        Map<String, ArgusPoint> argusByName = new HashMap<>();
        for (ArgusPoint point : argus) argusByName.put(key(point.name()), point);
        Set<String> takenNames = new HashSet<>(argusByName.keySet());
        Set<String> claimedArgus = new HashSet<>();
        Set<Object> claimedXaero = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

        // Worlds whose linked waypoints are all missing haven't been loaded by Xaero yet: leave them alone.
        Set<String> unready = new HashSet<>();
        for (World world : worlds) {
            int linked = 0, found = 0;
            for (Link link : links) {
                if (!link.dimension().equals(world.dimension()) || !link.world().equals(world.id())) continue;
                linked++;
                if (find(world, link, java.util.Collections.newSetFromMap(new IdentityHashMap<>())) != null) found++;
            }
            if (linked >= 2 && found == 0) unready.add(world.dimension());
        }

        for (Link link : links) {
            World world = byDimension.get(link.dimension());
            ArgusPoint a = argusByName.get(key(link.argusName()));
            if (a != null && !a.dimension().equals(link.dimension())) a = null;
            if (world == null || !world.id().equals(link.world()) || unready.contains(link.dimension())) {
                // Can't see its Xaero side right now: keep the link, and don't copy the waypoint anywhere else.
                next.add(link);
                if (a != null) claimedArgus.add(key(a.name()));
                continue;
            }
            ExternalPoint x = find(world, link, claimedXaero);
            if (a != null) claimedArgus.add(key(a.name()));
            if (x != null) claimedXaero.add(x.handle());

            if (a != null && x != null) {
                boolean xaeroChanged = !link.sameAs(x.x(), x.y(), x.z(), x.enabled());
                boolean argusChanged = !link.sameAs(a.x(), a.y(), a.z(), a.enabled());
                boolean differ = a.x() != x.x() || a.y() != x.y() || a.z() != x.z() || a.enabled() != x.enabled();
                if (xaeroChanged) {
                    if (differ) ops.add(new ArgusUpdate(a.name(), x.x(), x.y(), x.z(), x.enabled()));
                    next.add(link.at(x.x(), x.y(), x.z(), x.enabled()));
                } else if (argusChanged) {
                    if (differ) ops.add(new XaeroUpdate(link.dimension(), x.handle(), a.x(), a.y(), a.z(), a.enabled()));
                    next.add(link.at(a.x(), a.y(), a.z(), a.enabled()));
                } else next.add(link);
            } else if (a != null) {
                ops.add(new ArgusRemove(a.name()));
                takenNames.remove(key(a.name()));
            } else if (x != null) {
                ops.add(new XaeroRemove(link.dimension(), x.handle()));
            }
            // Gone from both sides: the link is simply dropped.
        }

        // New in Xaero: pair with a same-named Argus waypoint, or copy it to Argus.
        for (World world : worlds) {
            if (unready.contains(world.dimension())) continue;
            for (ExternalPoint x : world.points()) {
                if (claimedXaero.contains(x.handle())) continue;
                claimedXaero.add(x.handle());
                ArgusPoint twin = argusByName.get(key(x.name()));
                if (twin != null && twin.dimension().equals(world.dimension()) && !claimedArgus.contains(key(twin.name()))) {
                    claimedArgus.add(key(twin.name()));
                    if (twin.x() != x.x() || twin.y() != x.y() || twin.z() != x.z() || twin.enabled() != x.enabled())
                        ops.add(new XaeroUpdate(world.dimension(), x.handle(), twin.x(), twin.y(), twin.z(), twin.enabled()));
                    next.add(new Link(server, world.dimension(), world.id(), twin.name(), x.name(), x.set(), twin.x(), twin.y(), twin.z(), twin.enabled()));
                    continue;
                }
                String name = uniqueName(x.name(), world.dimension(), takenNames);
                takenNames.add(key(name));
                ops.add(new ArgusAdd(name, world.dimension(), x.x(), x.y(), x.z(), x.enabled(), x.name(), x.set(), world.id()));
                next.add(new Link(server, world.dimension(), world.id(), name, x.name(), x.set(), x.x(), x.y(), x.z(), x.enabled()));
            }
        }

        // New in Argus: copy it to Xaero, if Xaero has a world for its dimension.
        for (ArgusPoint a : argus) {
            if (claimedArgus.contains(key(a.name()))) continue;
            World world = byDimension.get(a.dimension());
            if (world == null || unready.contains(a.dimension())) continue;
            ops.add(new XaeroAdd(a.dimension(), world.id(), a.name(), a.x(), a.y(), a.z(), a.enabled()));
        }
        return new Plan(ops, next);
    }

    /** The Xaero waypoint a link points at: same set and name, preferring the one still at the linked position. */
    private static ExternalPoint find(World world, Link link, Set<Object> claimed) {
        ExternalPoint fallback = null;
        for (ExternalPoint x : world.points()) {
            if (claimed.contains(x.handle()) || !x.name().equals(link.xaeroName()) || !x.set().equals(link.set())) continue;
            if (x.x() == link.x() && x.y() == link.y() && x.z() == link.z()) return x;
            if (fallback == null) fallback = x;
        }
        return fallback;
    }

    /** Argus names are unique per server, Xaero's are not: a clash gets the dimension, then a number, added. */
    static String uniqueName(String name, String dimension, Set<String> taken) {
        if (!taken.contains(key(name))) return name;
        String withDimension = name + " (" + Character.toUpperCase(dimension.charAt(0)) + dimension.substring(1) + ")";
        if (!taken.contains(key(withDimension))) return withDimension;
        for (int i = 2; ; i++) if (!taken.contains(key(withDimension + " " + i))) return withDimension + " " + i;
    }
}
