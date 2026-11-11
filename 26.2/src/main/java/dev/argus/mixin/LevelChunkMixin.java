package dev.argus.mixin;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.BlockStateChangeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public class LevelChunkMixin {
   @Shadow
   @Final
   Level level;

   @Inject(method = "setBlockState", at = @At("TAIL"))
   private void postBlockStateChange(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> callback) {
      // The return value is the replaced state, or null when nothing changed.
      BlockState previous = callback.getReturnValue();
      if (previous != null && this.level.isClientSide()) {
         ClientAccess.eventBus.post(new BlockStateChangeEvent(pos.immutable(), previous, state));
      }
   }
}
