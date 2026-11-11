package dev.argus.client.modules.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

public record CrystalPlacementResult(BlockPos base, LivingEntity target, double damage, CrystalPlacementKind kind) {
   public CrystalPlacementResult selectBetterPlacement(CrystalPlacementResult other) {
      if (other == null) {
         return this;
      } else {
         int priority = this.kind.ordinal();
         if (priority < other.kind.ordinal()) {
            return this;
         } else {
            priority = this.kind.ordinal();
            if (priority > other.kind.ordinal()) {
               return other;
            } else {
               return other.damage > this.damage ? other : this;
            }
         }
      }
   }
}
