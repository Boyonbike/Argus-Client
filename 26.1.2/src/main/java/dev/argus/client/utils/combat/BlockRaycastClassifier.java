package dev.argus.client.utils.combat;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public interface BlockRaycastClassifier {
   BlockRaycastClassifier NO_OVERRIDE = (ignoredPos, state) -> BlockRaycastOverride.NONE;
   BlockRaycastClassifier IGNORE_LOW_RESISTANCE = (ignoredPos, state) -> {
      Block block = state.getBlock();
      return block.getExplosionResistance() < 600.0F ? BlockRaycastOverride.IGNORE : BlockRaycastOverride.NONE;
   };

   BlockRaycastOverride test(BlockPos var1, BlockState var2);

   static BlockRaycastClassifier ignorePosition(BlockPos ignoredPos) {
      return (blockPos, ignoredState) -> blockPos.equals(ignoredPos) ? BlockRaycastOverride.IGNORE : BlockRaycastOverride.NONE;
   }

   static BlockRaycastClassifier ignorePositionSet(Set<?> ignoredPositions) {
      return (blockPos, ignoredState) -> ignoredPositions.contains(blockPos) ? BlockRaycastOverride.IGNORE : BlockRaycastOverride.NONE;
   }

   static BlockRaycastClassifier ignorePositions(Collection<?> ignoredPositions) {
      if (ignoredPositions.isEmpty()) {
         return NO_OVERRIDE;
      } else {
         return ignoredPositions instanceof Set<?> positions ? ignorePositionSet(positions) : ignorePositions((Iterable<?>)ignoredPositions);
      }
   }

   static BlockRaycastClassifier ignorePositions(Iterable<?> ignoredPositions) {
      return (blockPos, ignoredState) -> containsPosition(ignoredPositions, blockPos) ? BlockRaycastOverride.IGNORE : BlockRaycastOverride.NONE;
   }

   static BlockRaycastClassifier solidPosition(BlockPos solidPos) {
      return solidPos == null ? NO_OVERRIDE : (blockPos, ignoredState) -> blockPos.equals(solidPos) ? BlockRaycastOverride.SOLID : BlockRaycastOverride.NONE;
   }

   static BlockRaycastClassifier solidPositions(Iterable<?> solidPositions) {
      return (blockPos, ignoredState) -> containsPosition(solidPositions, blockPos) ? BlockRaycastOverride.SOLID : BlockRaycastOverride.NONE;
   }

   private static boolean containsPosition(Iterable<?> positions, BlockPos blockPos) {
      for (Object value : positions) {
         if (blockPos.equals((BlockPos)value)) {
            return true;
         }
      }

      return false;
   }

   static BlockRaycastClassifier firstNonNone(BlockRaycastClassifier... classifiers) {
      BlockRaycastClassifier[] activeClassifiers = Arrays.stream(classifiers)
         .filter(classifier -> classifier != NO_OVERRIDE)
         .toArray(BlockRaycastClassifier[]::new);
      if (activeClassifiers.length == 0) {
         return NO_OVERRIDE;
      } else {
         return activeClassifiers.length == 1 ? activeClassifiers[0] : (blockPos, state) -> {
            for (BlockRaycastClassifier classifier : activeClassifiers) {
               BlockRaycastOverride override = classifier.test(blockPos, state);
               if (override != BlockRaycastOverride.NONE) {
                  return override;
               }
            }

            return BlockRaycastOverride.NONE;
         };
      }
   }
}
