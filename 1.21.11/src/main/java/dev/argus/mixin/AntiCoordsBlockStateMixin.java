/*
 * Adapted from NoTextureRotations by rfresh2 (https://github.com/rfresh2/NoTextureRotations), via Comet.
 * Modified for Argus by Boyonabike.
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.argus.client.modules.misc.AntiCoordsHooks;
import dev.argus.client.modules.misc.AntiCoordsModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every renderer asks the block state for its seed, so hooking here also covers doors, beds and tall plants,
 * whose blocks override the seed and would slip past a hook on Block.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class AntiCoordsBlockStateMixin {
   @ModifyVariable(method = "getSeed", at = @At("HEAD"), argsOnly = true)
   private BlockPos argus$antiCoordsSeedPos(BlockPos pos) {
      return AntiCoordsHooks.ready ? AntiCoordsModule.seedPos(pos) : pos;
   }

   @ModifyReturnValue(method = "getSeed", at = @At("RETURN"))
   private long argus$antiCoordsSeed(long seed) {
      return AntiCoordsHooks.ready ? AntiCoordsModule.seed(seed) : seed;
   }

   @Inject(method = "getOffset", at = @At("HEAD"), cancellable = true)
   private void argus$antiCoordsOffset(BlockPos pos, CallbackInfoReturnable<Vec3> cir, @Local(argsOnly = true) LocalRef<BlockPos> posRef) {
      if (!AntiCoordsHooks.ready || !((BlockBehaviour.BlockStateBase) (Object) this).hasOffsetFunction()) {
         return;
      }

      AntiCoordsModule.Mode mode = AntiCoordsModule.offsetMode();
      if (mode == AntiCoordsModule.Mode.FIXED) {
         cir.setReturnValue(Vec3.ZERO);
      } else if (mode == AntiCoordsModule.Mode.KEYED) {
         posRef.set(AntiCoordsModule.offsetPos(pos));
      }
   }
}
