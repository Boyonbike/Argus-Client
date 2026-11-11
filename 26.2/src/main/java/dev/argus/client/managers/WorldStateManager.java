package dev.argus.client.managers;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.BlockCollisionShapeEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.SelectedSlotChangeEvent;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.PendingPlacement;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.BlockStateSnapshot;
import dev.argus.client.utils.world.WorldUtils;
import dev.argus.mixin.BlockBehaviourAccessor;
import dev.argus.mixin.ClientLevelAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WorldStateManager implements ClientAccess {
   private static final AntiCheatModule antiCheatModule = ClientContext.getModule(AntiCheatModule.class);
   private final List<BlockEntity> blockEntities = Collections.synchronizedList(new ArrayList<>());
   private volatile boolean blockEntitiesStale = true;
   private final Map<BlockPos, PendingPlacement> pendingPlacements = Collections.synchronizedMap(new HashMap<>());
   private final Set<BlockPos> confirmedPlacements = Collections.synchronizedSet(new HashSet<>());
   private final Set<BlockPos> currentTickPlacements = Collections.synchronizedSet(new HashSet<>());
   private final Map<BlockPos, BlockStateSnapshot> rollbackBlocks = Collections.synchronizedMap(new HashMap<>());
   private final Map<BlockPos, WorldStateManager.GlassUpdateWindow> glassUpdateWindows = new ConcurrentHashMap<>();
   @Deprecated
   private BlockPos unconfirmedBreak = null;
   private final ElapsedTimer unconfirmedBreakTimer = new ElapsedTimer();
   private BlockPos extraBreak;
   private final ElapsedTimer extraBreakTimer = new ElapsedTimer();
   private DimensionType dimension = DimensionType.OVERWORLD;
   private ItemStack mainHandSnapshot;
   private ItemStack offHandSnapshot;
   private int chunkRadius;

   public DimensionType getDimension() {
      return this.dimension;
   }

   public int nextPredictionSequence() {
      BlockStatePredictionHandler handler = ((ClientLevelAccessor)ClientAccess.argusclientMinecraft.level).getPendingUpdateManager();
      BlockStatePredictionHandler prediction = handler.startPredicting();
      prediction.close();
      return prediction.currentSequence();
   }

   public int getChunkRadius() {
      return this.chunkRadius;
   }

   public List<BlockEntity> getBlockEntities() {
      // Rebuilt lazily, at most once per tick and only when something asks for it,
      // instead of copying every loaded chunk's block entities on every tick.
      if (this.blockEntitiesStale && ClientAccess.argusclientMinecraft.isSameThread()) {
         this.blockEntitiesStale = false;
         synchronized (this.blockEntities) {
            this.blockEntities.clear();
            if (ClientAccess.argusclientMinecraft.level != null && ClientAccess.argusclientMinecraft.player != null) {
               this.blockEntities.addAll(WorldUtils.getLoadedBlockEntities());
               this.blockEntities.removeIf(Objects::isNull);
            }
         }
      }

      return this.blockEntities;
   }

   public Map<BlockPos, PendingPlacement> getPendingPlacements() {
      return this.pendingPlacements;
   }

   public BlockPos getUnconfirmedBreak() {
      return this.unconfirmedBreak;
   }

   public BlockPos getExtraBreak() {
      return this.extraBreak;
   }

   private void refreshHandSnapshots() {
      this.mainHandSnapshot = ClientAccess.argusclientMinecraft.player.getMainHandItem().copy();
      this.offHandSnapshot = ClientAccess.argusclientMinecraft.player.getOffhandItem().copy();
   }

   public boolean hasActiveGlassUpdate(BlockPos position) {
      WorldStateManager.GlassUpdateWindow window = this.glassUpdateWindows.get(position);
      return window != null && window.isActive();
   }

   public boolean hasPendingPlacement(BlockPos position) {
      return this.pendingPlacements.containsKey(position);
   }

   public WorldStateManager() {
      ClientAccess.eventBus.subscribe(this);
   }

   public boolean isPlacementOccupied(BlockPos position) {
      if (this.hasActiveGlassUpdate(position) && RotationManager.isMovementSyncEnabled()) {
         return false;
      }

      if (antiCheatModule.assumeBlockBreakingSetting.getValue() && this.rollbackBlocks.containsKey(position)) {
         return false;
      }

      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      BlockState state = level.getBlockState(position);
      return !state.canBeReplaced() || this.currentTickPlacements.contains(position);
   }

   public void destroyBlockPredicted(BlockPos position) {
      this.saveBlockForRollback(position);
      MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
      gameMode.destroyBlock(position);
   }

   public void markUnconfirmedBreak(BlockPos position) {
      this.unconfirmedBreak = position;
      this.unconfirmedBreakTimer.reset();
   }

   public void markExtraBreak(BlockPos position) {
      this.extraBreak = position;
      this.extraBreakTimer.reset();
   }

   @EventHandler(priority = -200)
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      this.currentTickPlacements.clear();
      if (this.unconfirmedBreakTimer.hasElapsed(150L)) {
         this.unconfirmedBreak = null;
      }

      if (this.extraBreakTimer.hasElapsed(150L)) {
         this.extraBreak = null;
      }

      this.confirmedPlacements.removeIf(position -> {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         BlockPos playerPosition = player.blockPosition();
         return !position.closerThan(playerPosition, 128.0);
      });
      synchronized (this.pendingPlacements) {
         List<BlockPos> expiredPlacements = new ArrayList<>();

         for (Entry<BlockPos, PendingPlacement> placementEntry : this.pendingPlacements.entrySet()) {
            long now = System.currentTimeMillis();
            long createdAtMillis = placementEntry.getValue().createdAtMillis();
            if (now > createdAtMillis + 250L) {
               expiredPlacements.add(placementEntry.getKey());
            }
         }

         for (BlockPos expiredPosition : expiredPlacements) {
            this.pendingPlacements.remove(expiredPosition);
         }
      }

      this.blockEntitiesStale = true;

      synchronized (this.rollbackBlocks) {
         this.rollbackBlocks.forEach((position, snapshot) -> {
            if (snapshot.hasExpired()) {
               ClientLevel level = ClientAccess.argusclientMinecraft.level;
               BlockPos savedPosition = snapshot.position();
               BlockState savedState = snapshot.state();
               level.setBlockAndUpdate(savedPosition, savedState);
            }
         });
         this.rollbackBlocks.entrySet().removeIf(entry -> {
            BlockStateSnapshot snapshot = entry.getValue();
            return snapshot.hasExpired();
         });
      }

      this.glassUpdateWindows.values().removeIf(WorldStateManager.GlassUpdateWindow::hasExpired);
   }

   @EventHandler
   private void onConnectionSendPacketEvent(PacketEvent.Send event) {
      if (event.getPacket() instanceof ServerboundUseItemOnPacket packet) {
         RotationManager rotationManager = ClientContext.rotationManager;
         if (!rotationManager.isExecutingAction()) {
            InteractionHand hand = packet.getHand();
            ItemStack stack = this.resolveHandStack(hand);
            if (stack.getItem() instanceof BlockItem item) {
               BlockHitResult hit = packet.getHitResult();
               BlockPos placementPosition = hit.getBlockPos();
               Direction face = hit.getDirection();
               BlockPos adjacentPosition = placementPosition.relative(face);
               Block block = item.getBlock();
               boolean replacesClickedBlock = true;
               if (!WorldUtils.isReplaceable(placementPosition)) {
                  placementPosition = adjacentPosition;
                  replacesClickedBlock = false;
               }

               if (!WorldUtils.isReplaceable(placementPosition)) {
                  if (replacesClickedBlock) {
                     return;
                  }

                  if (block != WorldUtils.getBlock(placementPosition)) {
                     return;
                  }
               }

               this.pendingPlacements.put(placementPosition, new PendingPlacement(System.currentTimeMillis(), block));
               if (!replacesClickedBlock) {
                  this.currentTickPlacements.add(placementPosition);
               }
            }
         }
      }
   }

   @EventHandler
   private void onHandSnapshotEvent(SelectedSlotChangeEvent event) {
      this.refreshHandSnapshots();
   }

   @EventHandler
   private void onCollisionShapeEvent(BlockCollisionShapeEvent event) {
      if (event.getPos() != null && ClientAccess.argusclientMinecraft.player != null) {
         Map<BlockPos, PendingPlacement> placements = this.pendingPlacements;
         if (placements.containsKey(event.getPos())) {
            placements = this.pendingPlacements;
            PendingPlacement placement = placements.get(event.getPos());
            if (placement == null) {
               return;
            }

            if (placement.block() == null) {
               return;
            }

            AABB blockBounds = new AABB(event.getPos());
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            if (player.getBoundingBox().intersects(blockBounds)) {
               return;
            }

            if (!((BlockBehaviourAccessor)placement.block()).isCollidable()) {
               return;
            }

            ClientLevel entityLevel = ClientAccess.argusclientMinecraft.level;
            Class<Entity> entityClass = Entity.class;
            Predicate<Entity> collisionEntityFilter = entity -> !(entity instanceof ThrownExperienceBottle)
               && !(entity instanceof ItemEntity)
               && !(entity instanceof ExperienceOrb)
               && !(entity instanceof Arrow)
               && !(entity instanceof ThrownEnderpearl);
            if (!entityLevel.getEntitiesOfClass(entityClass, blockBounds, collisionEntityFilter).isEmpty()) {
               return;
            }

            Block block = placement.block();
            BlockState defaultState = block.defaultBlockState();
            ClientLevel level = ClientAccess.argusclientMinecraft.level;
            BlockPos shapePosition = event.getPos();
            VoxelShape shape = defaultState.getShape(level, shapePosition);
            event.setShape(shape);
         }

         if (RotationManager.isMovementSyncEnabled()
            && ClientContext.serverStateManager.getSetbackTimer().hasElapsed(300L)
            && event.getPos().equals(this.unconfirmedBreak)) {
            VoxelShape emptyShape = Shapes.empty();
            event.setShape(emptyShape);
         }
      }
   }

   @EventHandler
   private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
      this.pendingPlacements.clear();
      this.confirmedPlacements.clear();
      this.blockEntities.clear();
      this.rollbackBlocks.clear();
   }

   @EventHandler(priority = 200)
   private void onLocalPlayerPreTick(LocalPlayerPreTickEvent event) {
      this.currentTickPlacements.clear();
      this.dimension = WorldUtils.getDimension();
      this.refreshHandSnapshots();
   }

   private void trackGlassUpdate(BlockPos position, BlockState state) {
      if (BlockInteractionUtils.isGlass(state)) {
         long now = System.currentTimeMillis();
         int latencyDurationMillis = ClientContext.serverStateManager.getLatencyTicks() * 50 + 50;
         long activeDurationMillis = Math.min(250, latencyDurationMillis);
         Map<BlockPos, WorldStateManager.GlassUpdateWindow> windows = this.glassUpdateWindows;
         BlockPos immutableWindowPosition = position.immutable();
         long activeUntilMillis = now + activeDurationMillis;
         windows.putIfAbsent(immutableWindowPosition, new WorldStateManager.GlassUpdateWindow(activeUntilMillis, now + 10000L));
      }
   }

   private ItemStack resolveHandStack(InteractionHand hand) {
      ItemStack previous = hand == InteractionHand.MAIN_HAND ? this.mainHandSnapshot : this.offHandSnapshot;
      this.refreshHandSnapshots();
      ItemStack updated = hand == InteractionHand.MAIN_HAND ? this.mainHandSnapshot : this.offHandSnapshot;
      return updated.isEmpty() ? previous : updated;
   }

   public boolean hasKnownPlacement(BlockPos position) {
      return this.confirmedPlacements.contains(position) || this.pendingPlacements.containsKey(position);
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundBlockUpdatePacket blockUpdate) {
         this.rollbackBlocks.remove(blockUpdate.getPos());
         BlockState state = blockUpdate.getBlockState();
         if (state.isAir()) {
            this.confirmedPlacements.remove(blockUpdate.getPos());
            Map<BlockPos, PendingPlacement> pending = this.pendingPlacements;
            pending.remove(blockUpdate.getPos());
            Map<BlockPos, WorldStateManager.GlassUpdateWindow> windows = this.glassUpdateWindows;
            windows.remove(blockUpdate.getPos());
         } else {
            Map<BlockPos, PendingPlacement> pending = this.pendingPlacements;
            if (pending.remove(blockUpdate.getPos()) != null) {
               this.confirmedPlacements.add(blockUpdate.getPos());
            }
         }

         BlockPos updatePosition = blockUpdate.getPos();
         BlockState updateState = blockUpdate.getBlockState();
         this.trackGlassUpdate(updatePosition, updateState);
      }

      if (event.getPacket() instanceof ClientboundSectionBlocksUpdatePacket sectionUpdate) {
         synchronized (this.rollbackBlocks) {
            BiConsumer<BlockPos, BlockState> sectionUpdates = (position, state) -> {
               this.rollbackBlocks.remove(position);
               if (state.isAir()) {
                  Map<BlockPos, WorldStateManager.GlassUpdateWindow> windows = this.glassUpdateWindows;
                  windows.remove(position.immutable());
               } else {
                  this.trackGlassUpdate(position, state);
               }
            };
            sectionUpdate.runUpdates(sectionUpdates);
         }
      }

      if (event.getPacket() instanceof ClientboundLoginPacket login) {
         this.chunkRadius = login.chunkRadius();
      }

      if (event.getPacket() instanceof ClientboundSetChunkCacheRadiusPacket radiusUpdate) {
         this.chunkRadius = radiusUpdate.getRadius();
      }
   }

   public void saveBlockForRollback(BlockPos position) {
      if (!this.rollbackBlocks.containsKey(position)) {
         this.rollbackBlocks.put(position, new BlockStateSnapshot(BlockStateSnapshot.captureState(position), position, System.currentTimeMillis()));
      }
   }

   private record GlassUpdateWindow(long activeUntilMillis, long expiresAtMillis) {
      public boolean isActive() {
         return System.currentTimeMillis() < this.activeUntilMillis;
      }

      public boolean hasExpired() {
         return System.currentTimeMillis() >= this.expiresAtMillis;
      }
   }
}
