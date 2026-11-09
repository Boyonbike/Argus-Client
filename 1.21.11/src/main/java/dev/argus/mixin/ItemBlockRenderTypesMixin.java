package dev.argus.mixin;

import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.NoRenderModule;
import dev.argus.client.modules.render.XrayModule;
import dev.argus.client.render.RenderUtils;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemBlockRenderTypes.class)
public class ItemBlockRenderTypesMixin {
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   private static final XrayModule xrayModule = ClientContext.getModule(XrayModule.class);

   @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
   private static void applyTranslucentHandRenderType(BlockState blockState, CallbackInfoReturnable<RenderType> callback) {
      if (noRenderModule.getHandOpacity() != 1.0F && RenderUtils.getRenderColor()[3] != 255) {
         callback.setReturnValue(Sheets.translucentItemSheet());
      }
   }

   @Inject(method = "getChunkRenderType", at = @At("HEAD"), cancellable = true)
   private static void applyXrayTranslucentChunkLayer(BlockState blockState, CallbackInfoReturnable<ChunkSectionLayer> callback) {
      if (xrayModule.isToggled()
         && !xrayModule.alphaSetting.isAtMinimum()
         && !xrayModule.alphaSetting.isAtMaximum()
         && !xrayModule.shouldRenderBlock(BlockPos.ZERO, blockState.getBlock())) {
         callback.setReturnValue(ChunkSectionLayer.TRANSLUCENT);
      }
   }
}
