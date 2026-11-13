package dev.argus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The bearing the locator bar shows for a far-away player, for the Base Hunting WaypointCoords module. */
@Mixin(targets = "net.minecraft.world.waypoints.TrackedWaypoint$AzimuthWaypoint")
public interface TrackedAzimuthWaypointAccessor {
   @Accessor("angle")
   float argusclient$getAngle();
}
