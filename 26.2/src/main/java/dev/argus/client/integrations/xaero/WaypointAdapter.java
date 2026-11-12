package dev.argus.client.integrations.xaero;

import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;

public interface WaypointAdapter {
   void removeWaypoints(String var1);

   boolean isAvailable();

   void addWaypoint(String var1, BlockPos var2);

   // ---- two-way sync (see XaeroWaypointSync) ----

   /** Xaero's worlds for the overworld, nether and end of the server you are on; empty while Xaero isn't ready. */
   default List<WaypointSyncPlanner.World> syncWorlds() { return List.of(); }

   /** Creates a waypoint in the current set of that dimension's world; returns the set's name, or null if it couldn't. */
   default String createSynced(String dimension, String name, int x, int y, int z, boolean enabled) { return null; }

   default void updateSynced(Object handle, int x, int y, int z, boolean enabled) {}

   default void removeSynced(String dimension, Object handle) {}

   /** Writes the worlds of those dimensions to Xaero's waypoint files. */
   default void saveSynced(Collection<String> dimensions) {}
}
