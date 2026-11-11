package dev.argus.client.modules.movement;

import baritone.api.event.events.BlockChangeEvent;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Pair;
import dev.babbaj.pathfinder.NetherPathfinder;
import dev.babbaj.pathfinder.Octree;
import dev.babbaj.pathfinder.PathSegment;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class EntityControlPathContext {
   final ClientLevel level;
   final int height;
   private final int minY;
   private final long pointer;
   private long lastCullMillis = System.currentTimeMillis();
   private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> new Thread(task, "Argus Client entity pathfinder"));

   static boolean isSupported() {
      try {
         return NetherPathfinder.isThisSystemSupported();
      } catch (Throwable failure) {
         return false;
      }
   }

   EntityControlPathContext(ClientLevel level, int height) {
      this.level = level;
      this.height = height;
      this.minY = level.getMinY();

      long created;
      try {
         created = NetherPathfinder.newContext(0L);
      } catch (Throwable failure) {
         created = 0L;
      }

      this.pointer = created;
   }

   boolean isValid() {
      return this.pointer != 0L;
   }

   void loadChunks(ChunkPos center) {
      for (int x = center.x - 40; x <= center.x + 40; x++) {
         for (int z = center.z - 40; z <= center.z + 40; z++) {
            this.loadChunk(x, z);
         }
      }
   }

   void loadChunk(int x, int z) {
      LevelChunk chunk = this.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
      if (chunk != null) {
         SoftReference<LevelChunk> reference = new SoftReference<>(chunk);
         this.executor.execute(() -> {
            LevelChunk loaded = reference.get();
            if (loaded != null) {
               this.packChunk(loaded);
            }
         });
      }
   }

   void onBlockChange(BlockChangeEvent event) {
      List<Pair<BlockPos, BlockState>> changes = event.getBlocks();
      this.executor.execute(() -> {
         for (Pair<BlockPos, BlockState> change : changes) {
            BlockPos position = (BlockPos)change.first();
            BlockState state = (BlockState)change.second();
            this.setBlock(position, !state.isAir());
            if (isHazard(state)) {
               for (int y = 1; y <= 2; y++) {
                  this.setBlock(position.above(y), true);
               }
            }
         }
      });
   }

   void markBlocked(BlockPos center, int radius) {
      this.executor.execute(() -> {
         for (BlockPos position : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            this.setBlock(position, true);
         }
      });
   }

   void cullChunks(ChunkPos center) {
      if (System.currentTimeMillis() - this.lastCullMillis >= 180000L) {
         this.lastCullMillis = System.currentTimeMillis();
         this.executor.execute(() -> {
            try {
               NetherPathfinder.cullFarChunks(this.pointer, center.x, center.z, 5000);
            } catch (Throwable var3) {
            }
         });
      }
   }

   CompletableFuture<EntityControlPathContext.Route> findPath(BlockPos from, BlockPos to) {
      int fromY = Mth.clamp(from.getY() - this.minY, 0, this.height - 1);
      int toY = Mth.clamp(to.getY() - this.minY, 0, this.height - 1);
      return CompletableFuture.<EntityControlPathContext.Route>supplyAsync(
            () -> {
               PathSegment path;
               try {
                  path = NetherPathfinder.pathFind(this.pointer, from.getX(), fromY, from.getZ(), to.getX(), toY, to.getZ(), true, false, 10000, true);
               } catch (Throwable failure) {
                  return null;
               }

               if (path == null) {
                  return null;
               }

               List<EntityControlPathContext.Node> nodes = new ArrayList<>(path.packed.length);
               HashMap<BlockPos, Integer> visited = new HashMap<>();

               for (long packed : path.packed) {
                  BetterBlockPos position = BetterBlockPos.deserializeFromLong(packed);
                  int size = Mth.clamp(2 * Integer.lowestOneBit(position.getX() | position.getY() | position.getZ()), 2, 16);
                  EntityControlPathContext.Node node = new EntityControlPathContext.Node(
                     new BlockPos(position.getX(), position.getY() + this.minY, position.getZ()), size
                  );
                  Integer previous = visited.get(node.position());
                  if (previous != null) {
                     while (nodes.size() > previous + 1) {
                        visited.remove(nodes.removeLast().position());
                     }
                  } else {
                     visited.put(node.position(), nodes.size());
                     nodes.add(node);
                  }
               }

               return new EntityControlPathContext.Route(nodes, path.finished);
            },
            this.executor
         )
         .exceptionally(failure -> null);
   }

   void cancel() {
      if (this.isValid()) {
         try {
            NetherPathfinder.cancel(this.pointer);
         } catch (Throwable var2) {
         }
      }
   }

   void close() {
      this.cancel();
      this.executor.execute(() -> {
         if (this.isValid()) {
            try {
               NetherPathfinder.freeContext(this.pointer);
            } catch (Throwable var2) {
            }
         }
      });
      this.executor.shutdown();
   }

   private void setBlock(BlockPos position, boolean solid) {
      int y = position.getY() - this.minY;
      if (y >= 0 && y < 128) {
         try {
            long chunk = NetherPathfinder.getChunkPointer(this.pointer, position.getX() >> 4, position.getZ() >> 4);
            if (chunk != 0L) {
               Octree.setBlock(chunk, position.getX() & 15, y, position.getZ() & 15, solid);
            }
         } catch (Throwable var6) {
         }
      }
   }

   private void packChunk(LevelChunk chunk) {
      try {
         long nativeChunk = NetherPathfinder.getOrCreateChunk(this.pointer, chunk.getPos().x, chunk.getPos().z);
         if (nativeChunk == 0L) {
            return;
         }

         LevelChunkSection[] sections = chunk.getSections();

         for (int section = 0; section < Math.min(sections.length, 8); section++) {
            LevelChunkSection blocks = sections[section];
            if (blocks != null && !blocks.hasOnlyAir()) {
               int sectionY = section << 4;

               for (int y = 0; y < 16; y++) {
                  for (int z = 0; z < 16; z++) {
                     for (int x = 0; x < 16; x++) {
                        BlockState state = blocks.getBlockState(x, y, z);
                        if (!state.isAir()) {
                           Octree.setBlock(nativeChunk, x, sectionY + y, z, true);
                           if (isHazard(state)) {
                              for (int above = 1; above <= 2 && sectionY + y + above < 128; above++) {
                                 Octree.setBlock(nativeChunk, x, sectionY + y + above, z, true);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      } catch (Throwable var13) {
      }
   }

   static boolean isHazard(BlockState state) {
      return !state.getFluidState().isEmpty()
         || state.is(BlockTags.FIRE)
         || state.is(BlockTags.CAMPFIRES)
         || state.is(Blocks.MAGMA_BLOCK)
         || state.is(Blocks.COBWEB)
         || state.is(Blocks.POWDER_SNOW)
         || state.is(Blocks.SWEET_BERRY_BUSH);
   }

   record Node(BlockPos position, int size) {
      AABB bounds() {
         return AABB.ofSize(Vec3.atLowerCornerOf(this.position), this.size, this.size, this.size);
      }
   }

   record Route(List<EntityControlPathContext.Node> nodes, boolean finished) {
   }
}
