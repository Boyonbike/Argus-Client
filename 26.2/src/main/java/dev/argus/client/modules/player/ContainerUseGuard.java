package dev.argus.client.modules.player;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;

/** Remembers containers placed during one hold of the Use key. */
public final class ContainerUseGuard {
   private final Set<BlockPos> placed = new HashSet<>();

   public void placed(BlockPos pos, boolean useHeld) {
      update(useHeld);
      if (useHeld) {
         placed.add(pos.immutable());
      }
   }

   public void update(boolean useHeld) {
      if (!useHeld) {
         placed.clear();
      }
   }

   public boolean shouldBlock(BlockPos pos, boolean container, boolean secondaryUse, boolean hasHeldItem) {
      return container && placed.contains(pos) && !(secondaryUse && hasHeldItem);
   }

   public void clear() {
      placed.clear();
   }
}
