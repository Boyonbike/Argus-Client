package dev.argus.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes the block being meshed visible to the MaterialInfo layer override used by Xray. */
@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {
   @Unique
   private static final ThreadLocal<BlockState> CURRENT_BLOCK = new ThreadLocal<>();

   public static BlockState currentBlock() {
      return CURRENT_BLOCK.get();
   }

   @Inject(
      method = "compile",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V",
         shift = At.Shift.BEFORE
      )
   )
   private void argusclient$setCurrentBlock(@Local(type = BlockState.class) BlockState blockState, CallbackInfoReturnable<SectionCompiler.Results> cir) {
      CURRENT_BLOCK.set(blockState);
   }

   @Inject(
      method = "compile",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V",
         shift = At.Shift.AFTER
      )
   )
   private void argusclient$clearCurrentBlock(CallbackInfoReturnable<SectionCompiler.Results> cir) {
      CURRENT_BLOCK.remove();
   }
}
