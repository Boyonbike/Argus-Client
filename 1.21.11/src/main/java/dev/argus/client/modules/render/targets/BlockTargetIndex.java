package dev.argus.client.modules.render.targets;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.BlockStateChangeEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.MinecraftTickEvent;
import dev.argus.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.utils.world.ChunkScanner;
import dev.argus.client.utils.world.WorldUtils;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * The block targets of both ESP and Tracers, found once for the union of their block lists. Loaded chunks are scanned on
 * a worker thread (skipping sections that can't hold a wanted block) and unloads follow the same queue; single block
 * changes are applied straight away on the client thread, after the world has them, into 8x8x8 cells. A chunk scan can
 * read a block just before it changes, so its result is checked against the live chunk when applied and the changes
 * made since the scan began are replayed on top. Block entities are re-checked five times a second
 * for lid and shulker poses and for containers the player opened, so an idle stash never changes a cell.
 * <p>
 * Each module filters the shared entries by its own card (height, ominous vaults, natural chests, opened containers).
 */
public final class BlockTargetIndex implements ClientAccess {
   public static final int DUNGEON = 1;
   public static final int FORTRESS = 2;
   public static final int BASTION = 4;
   public static final int ANCIENT_CITY = 8;
   public static final int TRIAL_CHAMBERS = 16;
   private static final int MAX_ENTRIES = 300_000;
   private static final long DRAIN_BUDGET_NANOS = 3_000_000L;
   /** Containers the player opened in this world and dimension; the oldest are forgotten first beyond this. */
   private static final int MAX_OPENED = 65_536;
   private static BlockTargetIndex instance;

   /** A found block. {@code structures} marks natural chests (bits above); {@code pose} is a lid/shulker step. */
   public record Entry(BlockState state, int structures, int pose, boolean opened) {
      public boolean hasBlockEntity() {
         return this.state.hasBlockEntity();
      }
   }

   private record Source(Module module, TargetSettings targets) {
      void addWanted(Set<Block> wanted) {
         if (!this.module.isToggled()) {
            return;
         }

         if (this.targets.blocks.active()) {
            wanted.addAll(this.targets.blocks.list.getValue());
         }

         wanted.addAll(this.targets.specific.blocks());
      }
   }

   /**
    * A whole chunk from the worker, replacing what was stored. {@code startSeq} is the block-change count when the scan
    * began; changes after it are replayed when the result is applied ({@link Long#MAX_VALUE} for an unload).
    */
   private record Result(int generation, int chunkX, int chunkZ, long startSeq, long[] positions, Entry[] entries) {
   }

   /** How long block changes are remembered for replaying over chunk scans that began before them. */
   private static final int CHANGE_MEMORY_TICKS = 200;

   private final List<Source> sources = new ArrayList<>();
   private final CellIndex<Entry> index = new CellIndex<>();
   private final LongOpenHashSet blockEntities = new LongOpenHashSet();
   private final LongLinkedOpenHashSet opened = new LongLinkedOpenHashSet();
   private final LongArrayList added = new LongArrayList();
   private final ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>();
   private final ChunkScanner scanner = new ChunkScanner(1, this::scanChunk, this::unloadChunk, null);
   private volatile Set<Block> scanning = Set.of();
   private volatile int generation;
   private ClientLevel level;
   private boolean subscribed;
   private boolean running;
   private int ticks;
   /** Ticks since start; unlike {@link #ticks} it is never reset, so change ages stay right. */
   private long clock;
   private long version;
   private long addedEnd;
   /** Entries stored per chunk column, so the farthest chunks can make way when the index is full. */
   private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap chunkCounts = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
   /** Loaded chunks left out (or cut short) because the index was full; rescanned when they become nearer. */
   private final LongOpenHashSet skipped = new LongOpenHashSet();
   /** Counts block changes; written on the client thread, read by the worker when a scan begins. */
   private volatile long changeSeq;
   /** Recent block changes per chunk column (packed position -> change count), for replaying over older scans. */
   private final Long2ObjectOpenHashMap<Long2LongOpenHashMap> recentChanges = new Long2ObjectOpenHashMap<>();
   /** The tick each chunk in {@link #recentChanges} last changed, so old ones are forgotten. */
   private final Long2LongOpenHashMap recentChangeTicks = new Long2LongOpenHashMap();

   private BlockTargetIndex() {
   }

   public static BlockTargetIndex get() {
      if (instance == null) {
         instance = new BlockTargetIndex();
      }

      return instance;
   }

   /**
    * Registers a module's Blocks card and specific block targets. The index runs while an enabled module has its card
    * on or a specific block target.
    */
   public void register(Module module, TargetSettings targets) {
      this.sources.add(new Source(module, targets));
      if (!this.subscribed) {
         this.subscribed = true;
         ClientAccess.eventBus.subscribe(this);
      }
   }

   public CellIndex<Entry> index() {
      return this.index;
   }

   /** Whether some loaded chunks are left out because there are too many matches; the nearest ones are kept. */
   public boolean capped() {
      return !this.skipped.isEmpty();
   }

   /** Bumps whenever any entry changes, so caches can tell nothing happened. */
   public long version() {
      return this.version;
   }

   /**
    * Visits positions found since {@code since} (a value this method returned earlier, or 0) and returns the new mark.
    * Each reader keeps its own mark, so alerts don't depend on which tick handler runs first. Only the most recent
    * 4096 finds are kept.
    */
   public long forEachAddedSince(long since, java.util.function.LongConsumer visitor) {
      long first = this.addedEnd - this.added.size();
      for (long seq = Math.max(since, first); seq < this.addedEnd; seq++) {
         visitor.accept(this.added.getLong((int)(seq - first)));
      }

      return this.addedEnd;
   }

   public boolean isOpened(long packed) {
      return this.opened.contains(packed);
   }

   public void clearOpened() {
      this.opened.clear();
      this.ticks = -1;
   }

   @EventHandler
   private void onTick(MinecraftTickEvent event) {
      ClientLevel current = argusclientMinecraft.level;
      if (current != this.level) {
         this.stop();
         this.opened.clear();
         this.level = current;
      }

      Set<Block> wanted = new HashSet<>();
      for (Source source : this.sources) {
         source.addWanted(wanted);
      }

      if (current == null || wanted.isEmpty()) {
         this.stop();
         return;
      }

      if (!this.running || !wanted.equals(this.scanning)) {
         this.restart(wanted);
      }

      this.clock++;
      this.drain();
      if (this.ticks % 20 == 0) {
         this.rescanNearerSkipped();
         this.forgetOldChanges();
      }

      if (++this.ticks % 4 == 0) {
         this.reconcileBlockEntities();
      }
   }

   @EventHandler
   private void onDisconnect(MinecraftDisconnectEvent event) {
      this.stop();
      this.opened.clear();
   }

   /** A block changed in the client world (server update or local prediction), already applied to the chunk. */
   @EventHandler
   private void onBlockStateChange(BlockStateChangeEvent event) {
      ClientLevel current = argusclientMinecraft.level;
      if (!this.running || current == null || current != this.level) {
         return;
      }

      BlockPos position = event.getPos();
      long packed = CellIndex.pack(position.getX(), position.getY(), position.getZ());
      long chunkKey = ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4);
      long seq = this.changeSeq + 1;
      this.changeSeq = seq;
      this.recentChanges.computeIfAbsent(chunkKey, ignored -> new Long2LongOpenHashMap()).put(packed, seq);
      this.recentChangeTicks.put(chunkKey, this.clock);
      this.applyLive(current, position, packed, chunkKey, event.getNewState());
   }

   @EventHandler(priority = -200)
   private void onUseItemOn(MultiPlayerGameModeUseItemOnEvent event) {
      ClientLevel current = argusclientMinecraft.level;
      if (event.isCancelled() || current == null || argusclientMinecraft.player == null || argusclientMinecraft.player.isShiftKeyDown()) {
         return;
      }

      BlockPos position = event.getHitResult().getBlockPos();
      BlockState state = current.getBlockState(position);
      if (current.getBlockEntity(position) == null) {
         return;
      }

      this.markOpened(position.asLong());
      if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
         this.markOpened(ChestBlock.getConnectedBlockPos(position, state).asLong());
      }

      this.ticks = -1;
   }

   private void markOpened(long blockPos) {
      BlockPos position = BlockPos.of(blockPos);
      long packed = CellIndex.pack(position.getX(), position.getY(), position.getZ());
      this.opened.addAndMoveToLast(packed);
      while (this.opened.size() > MAX_OPENED) {
         this.opened.removeFirstLong();
      }
   }

   private void restart(Set<Block> wanted) {
      this.scanner.stop();
      this.generation++;
      this.results.clear();
      this.index.clear();
      this.blockEntities.clear();
      this.version++;
      this.scanning = Set.copyOf(wanted);
      this.chunkCounts.clear();
      this.skipped.clear();
      this.recentChanges.clear();
      this.recentChangeTicks.clear();
      this.scanner.start();
      this.running = true;
      this.scanNearestFirst();
   }

   /**
    * Scans every loaded chunk, nearest first, so when there are more matches than the index holds (netherrack in the
    * Nether) it fills with the ones around the player.
    */
   private void scanNearestFirst() {
      if (argusclientMinecraft.player == null || argusclientMinecraft.level == null) {
         this.scanner.scanLoadedChunks();
         return;
      }

      List<LevelChunk> chunks = new ArrayList<>(WorldUtils.getLoadedChunks());
      ChunkPos center = argusclientMinecraft.player.chunkPosition();
      chunks.sort(java.util.Comparator.comparingInt(chunk -> chunkDistance(chunk.getPos().x, chunk.getPos().z, center)));
      java.util.concurrent.ExecutorService executor = this.scanner.getExecutor();
      for (LevelChunk chunk : chunks) {
         executor.submit(() -> this.scanChunk(chunk.getPos(), chunk));
      }
   }

   private static int chunkDistance(int chunkX, int chunkZ, ChunkPos center) {
      int dx = chunkX - center.x;
      int dz = chunkZ - center.z;
      return dx * dx + dz * dz;
   }

   private ChunkPos playerChunk() {
      return argusclientMinecraft.player == null ? new ChunkPos(0, 0) : argusclientMinecraft.player.chunkPosition();
   }

   /** Frees room for a chunk by dropping stored chunks farther away than it. Returns whether there's room now. */
   private boolean makeRoom(int chunkX, int chunkZ, int needed) {
      if (this.index.size() + needed <= MAX_ENTRIES) {
         return true;
      }

      ChunkPos center = this.playerChunk();
      int distance = chunkDistance(chunkX, chunkZ, center);
      long[] stored = this.chunkCounts.keySet().toLongArray();
      Long[] order = new Long[stored.length];
      for (int i = 0; i < stored.length; i++) {
         order[i] = stored[i];
      }

      java.util.Arrays.sort(order, java.util.Comparator.comparingInt(
         (Long key) -> chunkDistance(ChunkPos.getX(key), ChunkPos.getZ(key), center)).reversed());
      for (Long key : order) {
         if (this.index.size() + needed <= MAX_ENTRIES) {
            break;
         }

         int x = ChunkPos.getX(key);
         int z = ChunkPos.getZ(key);
         if (chunkDistance(x, z, center) <= distance) {
            break;
         }

         this.removeChunk(x, z);
         this.skipped.add(ChunkPos.asLong(x, z));
      }

      return this.index.size() + needed <= MAX_ENTRIES;
   }

   /** Every second: rescans left-out chunks that are now nearer than the farthest stored one (or fit outright). */
   private void rescanNearerSkipped() {
      if (this.skipped.isEmpty() || argusclientMinecraft.level == null || !this.running) {
         return;
      }

      ChunkPos center = this.playerChunk();
      int farthest = 0;
      for (long key : this.chunkCounts.keySet()) {
         farthest = Math.max(farthest, chunkDistance(ChunkPos.getX(key), ChunkPos.getZ(key), center));
      }

      long[] candidates = this.skipped.toLongArray();
      Long[] order = new Long[candidates.length];
      for (int i = 0; i < candidates.length; i++) {
         order[i] = candidates[i];
      }

      java.util.Arrays.sort(order, java.util.Comparator.comparingInt((Long key) -> chunkDistance(ChunkPos.getX(key), ChunkPos.getZ(key), center)));
      boolean room = this.index.size() < MAX_ENTRIES;
      int sent = 0;
      for (Long key : order) {
         if (sent >= 8) {
            break;
         }

         int x = ChunkPos.getX(key);
         int z = ChunkPos.getZ(key);
         if (!room && chunkDistance(x, z, center) >= farthest) {
            break;
         }

         LevelChunk chunk = argusclientMinecraft.level.getChunkSource().getChunkNow(x, z);
         this.skipped.remove(key.longValue());
         if (chunk != null) {
            this.scanner.getExecutor().submit(() -> this.scanChunk(chunk.getPos(), chunk));
            sent++;
         }
      }
   }

   private void stop() {
      if (this.running) {
         this.scanner.stop();
         this.running = false;
         this.generation++;
         this.results.clear();
         this.index.clear();
         this.blockEntities.clear();
         this.chunkCounts.clear();
         this.skipped.clear();
         this.recentChanges.clear();
         this.recentChangeTicks.clear();
         this.scanning = Set.of();
         this.version++;
      }
   }

   /** Applies worker results on the client thread. */
   private void drain() {
      int current = this.generation;
      // Bounded per tick so joining a world at high render distance spreads the work out instead of
      // applying every scanned chunk in one frame; anything left over waits for the next tick.
      long deadline = System.nanoTime() + DRAIN_BUDGET_NANOS;
      Result result;
      while (System.nanoTime() < deadline && (result = this.results.poll()) != null) {
         if (result.generation() != current) {
            continue;
         }

         long chunkKey = ChunkPos.asLong(result.chunkX(), result.chunkZ());
         this.removeChunk(result.chunkX(), result.chunkZ());
         this.skipped.remove(chunkKey);
         ClientLevel world = this.level;
         LevelChunk chunk = world == null ? null : world.getChunkSource().getChunkNow(result.chunkX(), result.chunkZ());
         if (chunk == null) {
            // Unloaded meanwhile (or an unload result): nothing of this chunk stays.
            continue;
         }

         if (result.positions().length > 0 && !this.makeRoom(result.chunkX(), result.chunkZ(), result.positions().length)) {
            // Still too full: keep what fits (nearest chunks were stored first) and try again when it's nearer.
            this.skipped.add(chunkKey);
         }

         Set<Block> wanted = this.scanning;
         MutableBlockPos position = new MutableBlockPos();
         for (int i = 0; i < result.positions().length; i++) {
            long packed = result.positions()[i];
            position.set(CellIndex.unpackX(packed), CellIndex.unpackY(packed), CellIndex.unpackZ(packed));
            // The scan ran on another thread and may have read a block just before it was broken or replaced.
            BlockState live = chunk.getBlockState(position);
            if (!wanted.contains(live.getBlock())) {
               continue;
            }

            Entry entry = result.entries()[i];
            if (live != entry.state()) {
               entry = new Entry(live, structures(chunk, position, live), 0, false);
            }

            this.apply(packed, chunkKey, entry);
         }

         // Blocks changed after the scan began (placed ones it may have missed) are re-read from the live chunk.
         Long2LongOpenHashMap changes = this.recentChanges.get(chunkKey);
         if (changes != null && result.startSeq() != Long.MAX_VALUE) {
            for (Long2LongOpenHashMap.Entry change : changes.long2LongEntrySet()) {
               if (change.getLongValue() > result.startSeq()) {
                  long packed = change.getLongKey();
                  position.set(CellIndex.unpackX(packed), CellIndex.unpackY(packed), CellIndex.unpackZ(packed));
                  this.applyLive(world, position.immutable(), packed, chunkKey, chunk.getBlockState(position));
               }
            }
         }
      }
   }

   /** Stores or removes one position from the block now in the world. */
   private void applyLive(ClientLevel current, BlockPos position, long packed, long chunkKey, BlockState state) {
      if (this.scanning.contains(state.getBlock())) {
         int structures = current.hasChunkAt(position) ? structures(current.getChunkAt(position), position, state) : 0;
         this.apply(packed, chunkKey, new Entry(state, structures, 0, false));
      } else {
         this.apply(packed, chunkKey, null);
      }
   }

   /** Stores an entry (keeping a pose and opened mark while the block is unchanged), or removes it when null. */
   private void apply(long packed, long chunkKey, Entry entry) {
      if (entry == null) {
         if (this.index.remove(packed)) {
            this.blockEntities.remove(packed);
            this.chunkCounts.addTo(chunkKey, -1);
            this.touchNeighbours(packed);
            this.version++;
         }

         return;
      }

      Entry previous = this.index.get(packed);
      if (previous == null && this.index.size() >= MAX_ENTRIES) {
         return;
      }

      if (previous != null && previous.state() == entry.state()) {
         entry = new Entry(entry.state(), entry.structures(), previous.pose(), previous.opened());
      } else {
         entry = new Entry(entry.state(), entry.structures(), 0, this.opened.contains(packed));
      }

      if (this.index.put(packed, entry)) {
         this.chunkCounts.addTo(chunkKey, 1);
         this.added.add(packed);
         this.addedEnd++;
         if (this.added.size() > 4096) {
            this.added.removeElements(0, this.added.size() - 4096);
         }
      }

      if (entry.hasBlockEntity()) {
         this.blockEntities.add(packed);
      } else {
         this.blockEntities.remove(packed);
      }

      this.version++;
   }

   /** Drops remembered block changes no in-flight scan can still be older than. */
   private void forgetOldChanges() {
      var iterator = this.recentChangeTicks.long2LongEntrySet().iterator();
      while (iterator.hasNext()) {
         Long2LongOpenHashMap.Entry entry = iterator.next();
         if (this.clock - entry.getLongValue() > CHANGE_MEMORY_TICKS) {
            this.recentChanges.remove(entry.getLongKey());
            iterator.remove();
         }
      }
   }

   private void removeChunk(int chunkX, int chunkZ) {
      // Block-entity marks for this chunk are pruned by reconcileBlockEntities() once their index entry is gone,
      // so there's no need to walk every tracked block entity on each chunk unload.
      this.index.removeChunk(chunkX, chunkZ);
      this.chunkCounts.remove(ChunkPos.asLong(chunkX, chunkZ));
      this.version++;
   }

   /** Lid and shulker poses and opened marks, five times a second; unchanged entries stay untouched. */
   private void reconcileBlockEntities() {
      ClientLevel current = this.level;
      if (current == null) {
         return;
      }

      MutableBlockPos position = new MutableBlockPos();
      LongIterator iterator = this.blockEntities.iterator();
      while (iterator.hasNext()) {
         long packed = iterator.nextLong();
         Entry entry = this.index.get(packed);
         if (entry == null) {
            iterator.remove();
            continue;
         }

         position.set(CellIndex.unpackX(packed), CellIndex.unpackY(packed), CellIndex.unpackZ(packed));
         BlockEntity blockEntity = current.getBlockEntity(position);
         int pose = pose(blockEntity);
         boolean isOpened = this.opened.contains(packed);
         if (pose != entry.pose() || isOpened != entry.opened()) {
            this.index.put(packed, new Entry(entry.state(), entry.structures(), pose, isOpened));
            this.version++;
         }
      }
   }

   private static int pose(BlockEntity blockEntity) {
      if (blockEntity instanceof LidBlockEntity lid) {
         return Math.round(lid.getOpenNess(1.0F) * 10.0F);
      }

      return blockEntity instanceof ShulkerBoxBlockEntity box ? Math.round(box.getProgress(1.0F) * 10.0F) : 0;
   }

   // ---- worker thread -----------------------------------------------------------------------------------------------

   private void scanChunk(ChunkPos chunkPosition, LevelChunk chunk) {
      int scanGeneration = this.generation;
      long startSeq = this.changeSeq;
      Set<Block> wanted = this.scanning;
      if (wanted.isEmpty() || Thread.currentThread().isInterrupted()) {
         return;
      }

      LongArrayList positions = new LongArrayList();
      List<Entry> entries = new ArrayList<>();
      MutableBlockPos position = new MutableBlockPos();
      LevelChunkSection[] sections = chunk.getSections();
      int minY = chunk.getMinY();
      int baseX = chunkPosition.getMinBlockX();
      int baseZ = chunkPosition.getMinBlockZ();
      for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
         LevelChunkSection section = sections[sectionIndex];
         if (section == null || section.hasOnlyAir() || !section.maybeHas(state -> wanted.contains(state.getBlock()))) {
            continue;
         }

         if (Thread.currentThread().isInterrupted()) {
            return;
         }

         int sectionY = minY + sectionIndex * 16;
         for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
               for (int x = 0; x < 16; x++) {
                  BlockState state = section.getBlockState(x, y, z);
                  if (!wanted.contains(state.getBlock())) {
                     continue;
                  }

                  int worldX = baseX + x;
                  int worldY = sectionY + y;
                  int worldZ = baseZ + z;
                  position.set(worldX, worldY, worldZ);
                  positions.add(CellIndex.pack(worldX, worldY, worldZ));
                  entries.add(new Entry(state, structures(chunk, position, state), 0, false));
               }
            }
         }
      }

      this.results.add(new Result(scanGeneration, chunkPosition.x, chunkPosition.z, startSeq, positions.toLongArray(), entries.toArray(Entry[]::new)));
   }

   /**
    * A removed block may have hidden a face of its neighbours (meshes skip faces between drawn blocks), so neighbouring
    * cells across a cell border rebuild too.
    */
   private void touchNeighbours(long packed) {
      int x = CellIndex.unpackX(packed);
      int y = CellIndex.unpackY(packed);
      int z = CellIndex.unpackZ(packed);
      if ((x & 7) == 0) this.index.touch(CellIndex.pack(x - 1, y, z));
      if ((x & 7) == 7) this.index.touch(CellIndex.pack(x + 1, y, z));
      if ((y & 7) == 0) this.index.touch(CellIndex.pack(x, y - 1, z));
      if ((y & 7) == 7) this.index.touch(CellIndex.pack(x, y + 1, z));
      if ((z & 7) == 0) this.index.touch(CellIndex.pack(x, y, z - 1));
      if ((z & 7) == 7) this.index.touch(CellIndex.pack(x, y, z + 1));
   }

   private void unloadChunk(ChunkPos chunkPosition, LevelChunk chunk) {
      this.results.add(new Result(this.generation, chunkPosition.x, chunkPosition.z, Long.MAX_VALUE, new long[0], new Entry[0]));
   }

   private static int structures(LevelChunk chunk, BlockPos position, BlockState state) {
      if (!(state.getBlock() instanceof ChestBlock)) {
         return 0;
      }

      try {
         int mask = 0;
         if (WorldUtils.isStructureChest(chunk, position, false, false, false, true, false)) mask |= DUNGEON;
         if (WorldUtils.isStructureChest(chunk, position, true, false, false, false, false)) mask |= FORTRESS;
         if (WorldUtils.isStructureChest(chunk, position, false, true, false, false, false)) mask |= BASTION;
         if (WorldUtils.isStructureChest(chunk, position, false, false, true, false, false)) mask |= ANCIENT_CITY;
         if (WorldUtils.isStructureChest(chunk, position, false, false, false, false, true)) mask |= TRIAL_CHAMBERS;
         return mask;
      } catch (RuntimeException ignored) {
         return 0;
      }
   }
}
