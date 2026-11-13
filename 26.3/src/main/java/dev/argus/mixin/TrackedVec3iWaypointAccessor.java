package dev.argus.mixin;

import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The exact position some servers send for a tracked player, for the Base Hunting WaypointCoords module. */
@Mixin(targets = "net.minecraft.world.waypoints.TrackedWaypoint$Vec3iWaypoint")
public interface TrackedVec3iWaypointAccessor {
   @Accessor("vector")
   Vec3i argusclient$getVector();
}
