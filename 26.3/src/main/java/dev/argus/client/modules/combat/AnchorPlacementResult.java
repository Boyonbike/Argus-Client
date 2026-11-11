package dev.argus.client.modules.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record AnchorPlacementResult(BlockPos base, Direction direction, AnchorTargetDamage targetDamage) {
   public AnchorPlacementResult selectHigherDamage(AnchorPlacementResult other) {
      if (other == null) {
         return this;
      }

      double otherDamage = other.targetDamage.damage();
      double currentDamage = this.targetDamage.damage();
      return otherDamage > currentDamage ? other : this;
   }
}
