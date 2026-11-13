package dev.argus.mixin;

import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The chunk some servers send for a tracked player, for the Base Hunting WaypointCoords module. */
@Mixin(targets = "net.minecraft.world.waypoints.TrackedWaypoint$ChunkWaypoint")
public interface TrackedChunkWaypointAccessor {
   @Accessor("chunkPos")
   ChunkPos argusclient$getChunkPos();
}
