package dev.argus.mixin;

import dev.argus.client.render.MeshBlockContext;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Makes the block being meshed visible to the MaterialInfo layer override used by Xray. */
@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {
   private static final String TESSELATE =
      "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V";

   @ModifyArgs(method = "compile", at = @At(value = "INVOKE", target = TESSELATE))
   private void argusclient$setCurrentBlock(Args args) {
      MeshBlockContext.set(args.get(6));
   }

   @Inject(method = "compile", at = @At(value = "INVOKE", target = TESSELATE, shift = At.Shift.AFTER))
   private void argusclient$clearCurrentBlock(CallbackInfoReturnable<SectionCompiler.Results> cir) {
      MeshBlockContext.clear();
   }
}
