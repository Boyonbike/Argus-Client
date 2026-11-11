package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.InventoryStateManager;
import dev.argus.client.managers.SwingInteractionManager;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.entity.SwingAction;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.inventory.ItemUtils;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

public final class FeetPlaceFireworkSupport implements ClientAccess {
   private final Map pendingFireworks = new HashMap();
   private final Map<BlockPos, Deque<Long>> itemSpawnTimes = new HashMap();

   void onEnable() {
      this.pendingFireworks.clear();
   }

   private boolean hasHorizontalNeighbor(BlockPos position, Iterable<BlockPos> positions) {
      for (BlockPos neighbor : positions) {
         if (neighbor.distManhattan(position) == 1) {
            int positionY = position.getY();
            if (positionY == neighbor.getY()) {
               return true;
            }
         }
      }

      return false;
   }

   private void pruneSpawnTimes(Deque<Long> times, long now) {
      Long oldestTime;
      while ((oldestTime = (Long)times.peekFirst()) != null) {
         Long oldestTimeSnapshot = oldestTime;
         long elapsedMillis = now - oldestTimeSnapshot;
         if (elapsedMillis > 500L) {
            times.pollFirst();
            continue;
         }
         break;
      }
   }

   void processFireworks(boolean airPlace, boolean raytrace) {
      if (!ClientAccess.argusclientMinecraft.player.isSpectator()
         && ClientAccess.argusclientMinecraft.player.isAlive()
         && !ClientAccess.argusclientMinecraft.player.isHandsBusy()
         && !ClientAccess.argusclientMinecraft.player.isSleeping()) {
         long now = System.currentTimeMillis();
         long historyPruneTime = now;
         this.pruneSpawnHistory(historyPruneTime);
         BlockPos position = this.selectFireworkPosition();
         if (position != null && !BlockInteractionUtils.isOutsidePlacementBounds(position)) {
            boolean strictDirection = BlockInteractionUtils.isStrictDirectionEnabled();
            Direction face = BlockInteractionUtils.findSupportDirection(position, strictDirection, raytrace);
            if (face != null || airPlace) {
               int previousSlot = InventoryUtils.getSelectedSlot();
               Item rocketItem = Items.FIREWORK_ROCKET;
               InventoryUtils.SwapSlot rocketSlot = InventoryUtils.findSwapSlot(rocketItem, true);
               if (!InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 2) || rocketSlot.hotbar()) {
                  InventoryUtils.runWithSwap(previousSlot, rocketSlot, () -> {
                     InteractionHand hand = rocketSlot.hand();
                     BlockInteractionUtils.sendPlacement(position, null, face, true, hand);
                     SwingInteractionManager swingManager = ClientContext.swingManager;
                     InteractionHand swingHand = InteractionHand.MAIN_HAND;
                     SwingAction interactionType = SwingAction.INTERACT;
                     swingManager.swing(swingHand, interactionType);
                     this.pendingFireworks.put(position, new FeetPlaceFireworkSupport.PendingFirework(now, this.getFireworkDuration(rocketSlot), position));
                  });
               }
            }
         }
      }
   }

   private int getFireworkDuration(InventoryUtils.SwapSlot rocketSlot) {
      ItemStack rocketStack = rocketSlot.getStack();
      return ItemUtils.getFireworkDuration(rocketStack);
   }

   private BlockPos selectFireworkPosition() {
      Stream<BlockPos> openPositionStream = PlayerStateUtils.getOccupiedBlockPositions(ClientAccess.argusclientMinecraft.player).stream().filter(WorldUtils::isAir);
      Set<BlockPos> openPositions = openPositionStream.collect(Collectors.toSet());
      Set<BlockPos> occupiedPositions = PlayerStateUtils.getSurroundPositions(ClientAccess.argusclientMinecraft.player);
      Set<BlockPos> fireworkPositions = EntityUtils.getFireworkPositions();
      long now = System.currentTimeMillis();
      boolean hasThreatenedPosition = false;

      for (BlockPos position : occupiedPositions) {
         if (this.hasCoveredAirBlock(position) && this.isThreatenedPosition(position)) {
            hasThreatenedPosition = true;
            break;
         }
      }

      for (BlockPos candidate : occupiedPositions) {
         if (this.hasCoveredAirBlock(candidate) && this.hasHorizontalNeighbor(candidate, openPositions)) {
            BlockPos belowPosition = candidate.below();
            if (WorldUtils.isBedrockOrObsidian(belowPosition)) {
               boolean threatened = this.isThreatenedPosition(candidate);
               if (!hasThreatenedPosition || threatened) {
                  FeetPlaceFireworkSupport.PendingFirework pending = (FeetPlaceFireworkSupport.PendingFirework)this.pendingFireworks.get(candidate);
                  if (pending == null) {
                     if (threatened && !fireworkPositions.contains(candidate)) {
                        return candidate;
                     }
                  } else {
                     int latencyMillis = ClientContext.serverStateManager.getLatencyMillis();
                     long retryDelay = latencyMillis + 50;
                     long sentAtMillis = pending.sentAtMillis;
                     long durationTicks = pending.durationTicks;
                     long expiryTime = sentAtMillis + durationTicks * 50L;
                     boolean ready = now >= expiryTime || !fireworkPositions.contains(candidate);
                     if (ready && now > pending.sentAtMillis + retryDelay) {
                        return candidate;
                     }
                  }
               }
            }
         }
      }

      return null;
   }

   boolean isTrackedFireworkPosition(Set<BlockPos> positions, BlockPos position) {
      int latencyMillis = ClientContext.serverStateManager.getLatencyMillis();
      long retryDelay = latencyMillis + 50;
      FeetPlaceFireworkSupport.PendingFirework pending = (FeetPlaceFireworkSupport.PendingFirework)this.pendingFireworks.get(position);
      if (pending != null) {
         if (positions.contains(position)) {
            return true;
         }

         long now = System.currentTimeMillis();
         if (now < pending.sentAtMillis() + retryDelay) {
            return true;
         }
      }

      return positions.contains(position);
   }

   void observeSpawnPacket(PacketEvent.Receive event) {
      long receivedAt = System.currentTimeMillis();
      if (event.getPacket() instanceof ClientboundAddEntityPacket itemPacket && itemPacket.getType() == EntityTypes.ITEM) {
         double itemX = itemPacket.getX();
         double itemY = itemPacket.getY();
         double itemZ = itemPacket.getZ();
         double capturedItemY = itemY;
         BlockPos position = BlockPos.containing(itemX, capturedItemY, itemZ);
         ClientAccess.argusclientMinecraft.execute(() -> {
            Deque<Long> spawnTimes = this.itemSpawnTimes.computeIfAbsent(position, unused -> new ArrayDeque());
            spawnTimes.addLast(receivedAt);
            this.pruneSpawnTimes(spawnTimes, receivedAt);
         });
      }
   }

   private void pruneSpawnHistory(long now) {
      this.itemSpawnTimes.entrySet().removeIf(entry -> {
         this.pruneSpawnTimes((Deque)entry.getValue(), now);
         return ((Deque)entry.getValue()).isEmpty();
      });
   }

   private boolean hasCoveredAirBlock(BlockPos position) {
      BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(position);
      if (state.canBeReplaced()) {
         ClientLevel aboveLevel = ClientAccess.argusclientMinecraft.level;
         BlockPos abovePosition = position.above();
         BlockState aboveState = aboveLevel.getBlockState(abovePosition);
         if (!aboveState.canBeReplaced()) {
            return true;
         }
      }

      return false;
   }

   private boolean isThreatenedPosition(BlockPos position) {
      long now = System.currentTimeMillis();
      Deque<Long> spawnTimes = (Deque)this.itemSpawnTimes.get(position);
      if (spawnTimes != null) {
         this.pruneSpawnTimes(spawnTimes, now);
         int recentItemCount = spawnTimes.size();
         if (recentItemCount >= 2) {
            return true;
         }
      }

      double damage = ClientContext.blockBreakTracker.getBreakProgress(position);
      return damage > 0.9;
   }

   record PendingFirework(long sentAtMillis, int durationTicks, BlockPos position) {
   }
}
