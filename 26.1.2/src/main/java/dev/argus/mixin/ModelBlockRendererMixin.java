package dev.argus.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.NoRenderModule;
import dev.argus.client.modules.render.XrayModule;
import java.util.List;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {
   private static final XrayModule xrayModule = ClientContext.getModule(XrayModule.class);
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);

   @Inject(method = {"tesselateWithAO", "tesselateWithoutAO"}, at = @At("HEAD"), cancellable = true)
   private void suppressHiddenBlockEntityMesh(
      BlockAndTintGetter level,
      List parts,
      BlockState blockState,
      BlockPos blockPos,
      PoseStack poses,
      VertexConsumer vertices,
      boolean checkSides,
      int packedOverlay,
      CallbackInfo callback
   ) {
      XrayModule xray = XrayModule.getInstance();
      if (xray.isToggled() && xray.shouldHideUnselectedBlocks() && !xray.shouldRenderBlock(blockPos, blockState.getBlock())) {
         callback.cancel();
      } else if (noRenderModule.shouldHideBlockEntity(blockState)) {
         callback.cancel();
      }
   }

   // Runs for every quad of every block model; @ModifyArg on the single alpha argument avoids the
   // per-call Args array (and boxing) that @ModifyArgs allocated.
   @ModifyArg(
      method = "putQuadData",
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[II)V"
      ),
      index = 6
   )
   private float applyXrayQuadAlpha(float alpha, @Local(argsOnly = true) BlockPos blockPos, @Local(argsOnly = true) BlockState blockState) {
      if (xrayModule.isToggled()
         && !xrayModule.alphaSetting.isAtMinimum()
         && !xrayModule.alphaSetting.isAtMaximum()
         && !xrayModule.shouldRenderBlock(blockPos, blockState.getBlock())) {
         return xrayModule.alphaSetting.getValue().intValue() / 100.0F;
      }

      return alpha;
   }
}
