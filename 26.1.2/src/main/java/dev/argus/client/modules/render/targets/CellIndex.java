package dev.argus.client.modules.render.targets;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Block targets grouped into 8x8x8 cells, so a renderer can keep one cached mesh per cell and rebuild only the cells
 * that changed. Putting an equal value at a position changes nothing: an idle stash never marks a cell dirty.
 * Positions are packed by the caller (see {@link #pack}); all access happens on the client thread.
 */
public final class CellIndex<T> {
   public record CellKey(int x, int y, int z) {
      public static CellKey ofBlock(int blockX, int blockY, int blockZ) {
         return new CellKey(blockX >> 3, blockY >> 3, blockZ >> 3);
      }

      public int minX() { return this.x << 3; }
      public int minY() { return this.y << 3; }
      public int minZ() { return this.z << 3; }

      /** The chunk column this cell belongs to. */
      public long chunk() { return chunkKey(this.x >> 1, this.z >> 1); }
   }

   public static final class Cell<T> {
      private final CellKey key;
      private final Long2ObjectOpenHashMap<T> entries = new Long2ObjectOpenHashMap<>();
      private final Collection<T> values = Collections.unmodifiableCollection(this.entries.values());
      private long revision;

      private Cell(CellKey key) { this.key = key; }

      public CellKey key() { return this.key; }
      public Collection<T> values() { return this.values; }
      public long revision() { return this.revision; }
      public int size() { return this.entries.size(); }

      /** Visits each stored position with its value. */
      public void forEach(EntryVisitor<T> visitor) {
         for (Long2ObjectMap.Entry<T> entry : this.entries.long2ObjectEntrySet()) {
            visitor.visit(entry.getLongKey(), entry.getValue());
         }
      }
   }

   @FunctionalInterface
   public interface EntryVisitor<T> {
      void visit(long packed, T value);
   }

   private final Map<CellKey, Cell<T>> cells = new HashMap<>();
   private final Collection<Cell<T>> cellsView = Collections.unmodifiableCollection(this.cells.values());
   private final Long2ObjectOpenHashMap<Set<CellKey>> chunks = new Long2ObjectOpenHashMap<>();
   private final ArrayDeque<CellKey> dirtyQueue = new ArrayDeque<>();
   private final Set<CellKey> queued = new HashSet<>();
   private int size;
   /**
    * One counter for every cell, so a cell dropped and recreated at the same key (a chunk rescan) never gets a revision
    * a cached mesh already holds for the old cell.
    */
   private long revisions;

   /** Packs a block position the same way for every caller (26 bits X/Z, 12 bits Y, like Minecraft's BlockPos). */
   public static long pack(int x, int y, int z) {
      return ((long)x & 0x3FFFFFFL) << 38 | ((long)z & 0x3FFFFFFL) << 12 | (long)y & 0xFFFL;
   }

   public static int unpackX(long packed) { return (int)(packed >> 38); }
   public static int unpackY(long packed) { return (int)(packed << 52 >> 52); }
   public static int unpackZ(long packed) { return (int)(packed << 26 >> 38); }

   public static long chunkKey(int chunkX, int chunkZ) {
      return (long)chunkX << 32 | chunkZ & 0xFFFFFFFFL;
   }

   private static CellKey keyOf(long packed) {
      return CellKey.ofBlock(unpackX(packed), unpackY(packed), unpackZ(packed));
   }

   /** Stores a non-null value; returns true when this added a position that wasn't stored before. */
   public boolean put(long packed, T value) {
      Objects.requireNonNull(value, "value");
      CellKey key = keyOf(packed);
      Cell<T> cell = this.cells.get(key);
      if (cell == null) {
         cell = new Cell<>(key);
         this.cells.put(key, cell);
         this.chunks.computeIfAbsent(key.chunk(), ignored -> new HashSet<>()).add(key);
      }

      T previous = cell.entries.get(packed);
      if (Objects.equals(previous, value)) {
         return false;
      }

      cell.entries.put(packed, value);
      cell.revision = ++this.revisions;
      this.enqueue(key);
      if (previous == null) {
         this.size++;
         return true;
      }

      return false;
   }

   /** Removes a position. An emptied cell stays until its next rebuild so the old mesh can be released. */
   public boolean remove(long packed) {
      CellKey key = keyOf(packed);
      Cell<T> cell = this.cells.get(key);
      if (cell == null || cell.entries.remove(packed) == null) {
         return false;
      }

      this.size--;
      cell.revision = ++this.revisions;
      this.enqueue(key);
      return true;
   }

   /** Drops every cell in this chunk column and queues their meshes for release. */
   public void removeChunk(int chunkX, int chunkZ) {
      Set<CellKey> keys = this.chunks.remove(chunkKey(chunkX, chunkZ));
      if (keys == null) {
         return;
      }

      for (CellKey key : keys) {
         Cell<T> cell = this.cells.remove(key);
         if (cell != null) {
            this.size -= cell.entries.size();
            cell.entries.clear();
            cell.revision = ++this.revisions;
         }

         this.enqueue(key);
      }
   }

   /** Marks the cell holding a position for a rebuild, if it exists (e.g. a neighbour across its border changed). */
   public void touch(long packed) {
      CellKey key = keyOf(packed);
      Cell<T> cell = this.cells.get(key);
      if (cell != null) {
         cell.revision = ++this.revisions;
         this.enqueue(key);
      }
   }

   public T get(long packed) {
      Cell<T> cell = this.cells.get(keyOf(packed));
      return cell == null ? null : cell.entries.get(packed);
   }

   public Cell<T> cell(CellKey key) { return this.cells.get(key); }

   /** A live, read-only view of every cell. */
   public Collection<Cell<T>> cells() { return this.cellsView; }

   public int size() { return this.size; }

   /** The next cell whose contents changed since it was last polled, or null. */
   public CellKey pollDirty() {
      CellKey key = this.dirtyQueue.pollFirst();
      if (key != null) {
         this.queued.remove(key);
      }

      return key;
   }

   /** Marks every cell for a rebuild, for example after a colour change. */
   public void dirtyAll() {
      for (Cell<T> cell : this.cells.values()) {
         cell.revision = ++this.revisions;
         this.enqueue(cell.key);
      }
   }

   public void clear() {
      this.cells.clear();
      this.chunks.clear();
      this.dirtyQueue.clear();
      this.queued.clear();
      this.size = 0;
   }

   private void enqueue(CellKey key) {
      if (this.queued.add(key)) {
         this.dirtyQueue.addLast(key);
      }
   }
}
