package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.argus.client.modules.render.XrayModule;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockStateBase.class)
public class BlockBehaviourBlockStateBaseMixin {
   /**
    * Runs for every vertex during chunk meshing. A cancellable inject allocated a callback object per call;
    * a return-value modifier allocates nothing.
    */
   @ModifyReturnValue(method = "getShadeBrightness", at = @At("RETURN"))
   private float overrideXrayShadeBrightness(float original) {
      XrayModule xray = XrayModule.getInstance();
      return xray.isToggled() && xray.shouldHideUnselectedBlocks() ? 1.0F : original;
   }
}
