package dev.argus.client.modules.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;

record PistonCrystalPlan(
   BlockPos crystalPosition,
   BlockHitResult crystalHit,
   BlockPos pistonPosition,
   Direction pistonFacing,
   BlockHitResult pistonHit,
   BlockPos powerPosition,
   BlockHitResult powerHit
) {
}
