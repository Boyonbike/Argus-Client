package dev.argus.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.ClientChunkCacheReplaceWithPacketDataEvent;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData.BlockEntityTagOutput;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkCache.class)
public class ClientChunkCacheMixin {
   @Inject(
      method = "replaceWithPacketData",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/multiplayer/ClientLevel;onChunkLoaded(Lnet/minecraft/world/level/ChunkPos;)V",
         shift = Shift.BEFORE
      )
   )
   private void postChunkPacketDataEvent(
      int chunkX,
      int chunkZ,
      FriendlyByteBuf packetData,
      Map<Types, long[]> heightmaps,
      Consumer<BlockEntityTagOutput> blockEntityConsumer,
      CallbackInfoReturnable<LevelChunk> callback,
      @Local LevelChunk chunk
   ) {
      ClientAccess.eventBus.post(new ClientChunkCacheReplaceWithPacketDataEvent(chunk));
   }
}
