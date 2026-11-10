package dev.argus.client.modules.render.targets;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.MinecraftTickEvent;
import dev.argus.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.utils.world.ChunkScanner;
import dev.argus.client.utils.world.WorldUtils;
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
 * a worker thread (skipping sections that can't hold a wanted block); block updates and unloads follow the same queue,
 * and every change is applied on the client thread into 8x8x8 cells. Block entities are re-checked five times a second
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
   /** Containers the player opened in this world and dimension; the oldest are forgotten first beyond this. */
   private static final int MAX_OPENED = 65_536;
   private static BlockTargetIndex instance;

   /** A found block. {@code structures} marks natural chests (bits above); {@code pose} is a lid/shulker step. */
   public record Entry(BlockState state, int structures, int pose, boolean opened) {
      public boolean hasBlockEntity() {
         return this.state.hasBlockEntity();
      }
   }

   private record Source(Module module, TargetSettings.Blocks card) {
      boolean wants() {
         return this.module.isToggled() && this.card.active();
      }
   }

   /** A batch from the worker: a whole chunk (replacing what was stored) or single positions (null entry = removed). */
   private record Result(int generation, int chunkX, int chunkZ, boolean wholeChunk, long[] positions, Entry[] entries) {
   }

   private final List<Source> sources = new ArrayList<>();
   private final CellIndex<Entry> index = new CellIndex<>();
   private final LongOpenHashSet blockEntities = new LongOpenHashSet();
   private final LongLinkedOpenHashSet opened = new LongLinkedOpenHashSet();
   private final LongArrayList added = new LongArrayList();
   private final ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>();
   private final ChunkScanner scanner = new ChunkScanner(1, this::scanChunk, this::unloadChunk, this::updateBlock);
   private volatile Set<Block> scanning = Set.of();
   private volatile int generation;
   private ClientLevel level;
   private boolean subscribed;
   private boolean running;
   private int ticks;
   private long version;
   private long addedEnd;

   private BlockTargetIndex() {
   }

   public static BlockTargetIndex get() {
      if (instance == null) {
         instance = new BlockTargetIndex();
      }

      return instance;
   }

   /** Registers a module's Blocks card. The index runs while any registered card is on in an enabled module. */
   public void register(Module module, TargetSettings.Blocks card) {
      this.sources.add(new Source(module, card));
      if (!this.subscribed) {
         this.subscribed = true;
         ClientAccess.eventBus.subscribe(this);
      }
   }

   public CellIndex<Entry> index() {
      return this.index;
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
         if (source.wants()) {
            wanted.addAll(source.card().list.getValue());
         }
      }

      if (current == null || wanted.isEmpty()) {
         this.stop();
         return;
      }

      if (!this.running || !wanted.equals(this.scanning)) {
         this.restart(wanted);
      }

      this.drain();
      if (++this.ticks % 4 == 0) {
         this.reconcileBlockEntities();
      }
   }

   @EventHandler
   private void onDisconnect(MinecraftDisconnectEvent event) {
      this.stop();
      this.opened.clear();
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
      this.scanner.start();
      this.running = true;
      this.scanner.scanLoadedChunks();
   }

   private void stop() {
      if (this.running) {
         this.scanner.stop();
         this.running = false;
         this.generation++;
         this.results.clear();
         this.index.clear();
         this.blockEntities.clear();
         this.scanning = Set.of();
         this.version++;
      }
   }

   /** Applies worker results on the client thread. */
   private void drain() {
      int current = this.generation;
      Result result;
      while ((result = this.results.poll()) != null) {
         if (result.generation() != current) {
            continue;
         }

         if (result.wholeChunk()) {
            this.removeChunk(result.chunkX(), result.chunkZ());
         }

         for (int i = 0; i < result.positions().length; i++) {
            long packed = result.positions()[i];
            Entry entry = result.entries()[i];
            if (entry == null) {
               if (this.index.remove(packed)) {
                  this.blockEntities.remove(packed);
                  this.version++;
               }
            } else if (this.index.size() < MAX_ENTRIES || this.index.get(packed) != null) {
               Entry previous = this.index.get(packed);
               if (previous != null && previous.state() == entry.state()) {
                  entry = new Entry(entry.state(), entry.structures(), previous.pose(), previous.opened());
               } else {
                  entry = new Entry(entry.state(), entry.structures(), 0, this.opened.contains(packed));
               }

               if (this.index.put(packed, entry)) {
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
         }
      }
   }

   private void removeChunk(int chunkX, int chunkZ) {
      LongIterator iterator = this.blockEntities.iterator();
      while (iterator.hasNext()) {
         long packed = iterator.nextLong();
         if (CellIndex.unpackX(packed) >> 4 == chunkX && CellIndex.unpackZ(packed) >> 4 == chunkZ) {
            iterator.remove();
         }
      }

      this.index.removeChunk(chunkX, chunkZ);
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

      this.results.add(new Result(scanGeneration, chunkPosition.x, chunkPosition.z, true, positions.toLongArray(), entries.toArray(Entry[]::new)));
   }

   private void unloadChunk(ChunkPos chunkPosition, LevelChunk chunk) {
      this.results.add(new Result(this.generation, chunkPosition.x, chunkPosition.z, true, new long[0], new Entry[0]));
   }

   private void updateBlock(BlockPos position, BlockState state) {
      Set<Block> wanted = this.scanning;
      long packed = CellIndex.pack(position.getX(), position.getY(), position.getZ());
      Entry entry = null;
      if (wanted.contains(state.getBlock())) {
         ClientLevel current = argusclientMinecraft.level;
         int structures = 0;
         if (current != null && current.hasChunkAt(position)) {
            structures = structures(current.getChunkAt(position), position, state);
         }

         entry = new Entry(state, structures, 0, false);
      }

      this.results.add(new Result(this.generation, position.getX() >> 4, position.getZ() >> 4, false, new long[]{packed}, new Entry[]{entry}));
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
