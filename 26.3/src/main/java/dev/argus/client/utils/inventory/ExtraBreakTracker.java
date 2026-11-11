package dev.argus.client.utils.inventory;

import dev.argus.client.ClientAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class ExtraBreakTracker implements ClientAccess {
   private final List<BlockPos> expiredPositions = new ArrayList<>();
   private final Map<BlockPos, Long> trackedBreaks = new HashMap<>();
   private Block lastBlock;

   public synchronized void pruneExpired() {
      this.trackedBreaks.forEach(this::collectExpired);
      List<BlockPos> expiredSnapshot = this.expiredPositions;
      Map<BlockPos, Long> removalTarget = this.trackedBreaks;
      expiredSnapshot.forEach(removalTarget::remove);
      this.expiredPositions.clear();
   }

   public Block getLastBlock() {
      return this.lastBlock;
   }

   public synchronized boolean isTracked(BlockPos position) {
      return this.trackedBreaks.containsKey(position);
   }

   private void collectExpired(BlockPos position, long trackedAt) {
      long elapsedMillis = System.currentTimeMillis() - trackedAt;
      if (elapsedMillis >= 200L) {
         this.expiredPositions.add(position);
      }
   }

   public synchronized void trackBreak(BlockPos position) {
      Map<BlockPos, Long> breaksSnapshot = this.trackedBreaks;
      long trackedAt = System.currentTimeMillis();
      breaksSnapshot.put(position, trackedAt);
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      BlockState blockState = level.getBlockState(position);
      if (!blockState.isAir()) {
         this.lastBlock = blockState.getBlock();
      }
   }
}
