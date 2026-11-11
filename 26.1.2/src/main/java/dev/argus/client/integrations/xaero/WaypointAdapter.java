package dev.argus.client.integrations.xaero;

import net.minecraft.core.BlockPos;

public interface WaypointAdapter {
   void removeWaypoints(String var1);

   boolean isAvailable();

   void addWaypoint(String var1, BlockPos var2);
}
