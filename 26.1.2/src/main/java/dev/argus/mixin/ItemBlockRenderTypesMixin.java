package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
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

   // Both lookups run per item model / per meshed block, so they use return-value modifiers (no callback allocation).
   @ModifyReturnValue(method = "getRenderType", at = @At("RETURN"))
   private static RenderType applyTranslucentHandRenderType(RenderType original, BlockState blockState) {
      return noRenderModule.getHandOpacity() != 1.0F && RenderUtils.getRenderColor()[3] != 255 ? Sheets.translucentItemSheet() : original;
   }

   @ModifyReturnValue(method = "getChunkRenderType", at = @At("RETURN"))
   private static ChunkSectionLayer applyXrayTranslucentChunkLayer(ChunkSectionLayer original, BlockState blockState) {
      return xrayModule.isToggled()
            && !xrayModule.alphaSetting.isAtMinimum()
            && !xrayModule.alphaSetting.isAtMaximum()
            && !xrayModule.shouldRenderBlock(BlockPos.ZERO, blockState.getBlock())
         ? ChunkSectionLayer.TRANSLUCENT
         : original;
   }
}
