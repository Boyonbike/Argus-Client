package dev.argus.client.utils.world;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.ClientChunkCacheReplaceWithPacketDataEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.PacketEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

public class ChunkScanner implements ClientAccess {
   private final int workerCount;
   private final BiConsumer<ChunkPos, LevelChunk> chunkLoadCallback;
   private final BiConsumer<ChunkPos, LevelChunk> chunkUnloadCallback;
   private final BiConsumer<BlockPos, BlockState> blockUpdateCallback;
   private volatile ExecutorService executor;

   public synchronized void start() {
      if (this.executor != null) {
         return;
      }
      int requestedWorkers = this.workerCount;
      this.executor = createExecutor(requestedWorkers);
      ClientAccess.eventBus.subscribe(this);
   }

   public void scanLoadedChunks() {
      if (this.chunkLoadCallback != null) {
         for (LevelChunk chunk : WorldUtils.getLoadedChunks()) {
            this.dispatch(() -> this.chunkLoadCallback.accept(chunk.getPos(), chunk));
         }
      }
   }

   public synchronized void restartExecutor() {
      if (this.executor != null) {
         this.executor.shutdownNow();
         this.executor = createExecutor(this.workerCount);
      }
   }

   public ChunkScanner(int workerCount, BiConsumer<ChunkPos, LevelChunk> chunkLoadCallback, BiConsumer<ChunkPos, LevelChunk> chunkUnloadCallback, BiConsumer<BlockPos, BlockState> blockUpdateCallback) {
      this.workerCount = workerCount;
      this.chunkLoadCallback = chunkLoadCallback;
      this.chunkUnloadCallback = chunkUnloadCallback;
      this.blockUpdateCallback = blockUpdateCallback;
   }

   public ChunkScanner(int workerCount, ChunkScanner.Listener listener) {
      this(workerCount, listener::onChunkLoaded, listener::onChunkUnloaded, listener::onBlockUpdated);
   }

   public ExecutorService getExecutor() {
      return this.executor;
   }

   @EventHandler
   private void onClientChunkCacheReplaceWithPacketDataEvent(ClientChunkCacheReplaceWithPacketDataEvent event) {
      if (this.chunkLoadCallback != null) {
         this.dispatch(() -> {
            BiConsumer<ChunkPos, LevelChunk> loadCallback = this.chunkLoadCallback;
            LevelChunk chunkForPosition = event.getChunk();
            ChunkPos chunkPosition = chunkForPosition.getPos();
            loadCallback.accept(chunkPosition, event.getChunk());
         });
      }
   }

   @EventHandler
   public void onPacketReceive(PacketEvent.Receive event) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      if (level != null) {
         if (this.blockUpdateCallback != null && event.getPacket() instanceof ClientboundBlockUpdatePacket blockPacket) {
            this.dispatch(level, () -> {
               BiConsumer<BlockPos, BlockState> updateCallback = this.blockUpdateCallback;
               BlockPos blockPosition = blockPacket.getPos();
               updateCallback.accept(blockPosition, blockPacket.getBlockState());
            });
            return;
         }

         if (this.blockUpdateCallback != null && event.getPacket() instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) {
            this.dispatch(level, () -> sectionPacket.runUpdates((position, state) -> {
               BiConsumer<BlockPos, BlockState> updateCallback = this.blockUpdateCallback;
               updateCallback.accept(position.immutable(), state);
            }));
            return;
         }

         if (this.chunkUnloadCallback != null && event.getPacket() instanceof ClientboundForgetLevelChunkPacket forgetPacket) {
            ChunkPos extractedChunkPosition;
            try {
               extractedChunkPosition = forgetPacket.pos();
            } catch (Throwable extractionFailure) {
               throw new MatchException(extractionFailure.toString(), extractionFailure);
            }

            ChunkPos copiedChunkPosition = new ChunkPos(extractedChunkPosition.x, extractedChunkPosition.z);
            int chunkZ = copiedChunkPosition.z;
            int chunkX = copiedChunkPosition.x;
            LevelChunk chunk = level.getChunk(chunkX, chunkZ);
            this.dispatch(level, () -> this.chunkUnloadCallback.accept(copiedChunkPosition, chunk));
         }
      }
   }

   private void dispatch(ClientLevel level, Runnable action) {
      this.dispatch(() -> {
         if (ClientAccess.argusclientMinecraft.level == level) {
            action.run();
         }
      });
   }

   private void dispatch(Runnable action) {
      if (this.workerCount == 0) {
         if (this.executor != null) {
            action.run();
         }
      } else {
         synchronized (this) {
            if (this.executor != null) {
               this.executor.execute(action);
            }
         }
      }
   }

   public synchronized void stop() {
      if (this.executor != null) {
         ClientAccess.eventBus.unsubscribe(this);
         this.executor.shutdownNow();
         this.executor = null;
      }
   }

   private static ExecutorService createExecutor(int requestedWorkers) {
      int workers = Math.max(requestedWorkers, 1);
      return Executors.newFixedThreadPool(workers);
   }

   public interface Listener {
      void onChunkLoaded(ChunkPos var1, LevelChunk var2);

      void onChunkUnloaded(ChunkPos var1, LevelChunk var2);

      void onBlockUpdated(BlockPos var1, BlockState var2);
   }
}
