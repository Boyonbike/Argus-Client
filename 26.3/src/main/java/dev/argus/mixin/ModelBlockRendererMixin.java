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

   @Inject(method = "tesselateBlock", at = @At("HEAD"), cancellable = true)
   private void suppressHiddenBlockEntityMesh(
      net.minecraft.client.renderer.block.BlockQuadOutput output,
      float x,
      float y,
      float z,
      BlockAndTintGetter level,
      BlockPos blockPos,
      BlockState blockState,
      net.minecraft.client.renderer.block.dispatch.BlockStateModel model,
      long seed,
      CallbackInfo callback
   ) {
      XrayModule xray = XrayModule.getInstance();
      if (xray.isToggled() && xray.shouldHideUnselectedBlocks() && !xray.shouldRenderBlock(blockPos, blockState.getBlock())) {
         callback.cancel();
      } else if (noRenderModule.shouldHideBlockEntity(blockState)) {
         callback.cancel();
      }
   }

}
