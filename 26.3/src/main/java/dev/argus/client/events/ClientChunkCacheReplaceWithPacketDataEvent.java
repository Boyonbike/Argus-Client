package dev.argus.client.events;

import net.minecraft.world.level.chunk.LevelChunk;

public final class ClientChunkCacheReplaceWithPacketDataEvent extends CancellableEvent {
   private final LevelChunk chunk;

   public ClientChunkCacheReplaceWithPacketDataEvent(LevelChunk chunk) {
      this.chunk = chunk;
   }

   public LevelChunk getChunk() {
      return this.chunk;
   }
}
