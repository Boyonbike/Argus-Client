package dev.argus.client.modules.combat;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

final class PistonCrystalStageTracker {
   private final EnumMap<PistonCrystalStageTracker.Kind, Map<BlockPos, Long>> pending = new EnumMap<>(
      Map.of(
         PistonCrystalStageTracker.Kind.PISTON,
         new HashMap<>(),
         PistonCrystalStageTracker.Kind.CRYSTAL,
         new HashMap<>(),
         PistonCrystalStageTracker.Kind.POWER,
         new HashMap<>()
      )
   );

   void update() {
      this.pending.values().forEach(positions -> positions.values().removeIf(expiry -> System.currentTimeMillis() > expiry));
   }

   void reset() {
      this.pending.values().forEach(Map::clear);
   }

   void markPiston(BlockPos position, Direction facing) {
      this.mark(PistonCrystalStageTracker.Kind.PISTON, position.immutable());
      this.mark(PistonCrystalStageTracker.Kind.PISTON, position.immutable().relative(facing));
   }

   void markCrystal(BlockPos position) {
      this.mark(PistonCrystalStageTracker.Kind.CRYSTAL, position.immutable());
   }

   void markPower(BlockPos position) {
      this.mark(PistonCrystalStageTracker.Kind.POWER, position.immutable());
   }

   boolean isPending(PistonCrystalStageTracker.Kind kind, BlockPos position) {
      return this.pending.get(kind).getOrDefault(position, 0L) > System.currentTimeMillis();
   }

   private void mark(PistonCrystalStageTracker.Kind kind, BlockPos position) {
      this.pending.get(kind).put(position, System.currentTimeMillis() + 300L);
   }

   enum Kind {
      PISTON,
      CRYSTAL,
      POWER;
   }
}
