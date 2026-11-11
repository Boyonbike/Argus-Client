package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.NoRenderModule;
import dev.argus.client.modules.render.XrayModule;
import dev.argus.client.render.MeshBlockContext;
import dev.argus.client.render.RenderUtils;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BakedQuad.MaterialInfo.class)
public class ItemBlockRenderTypesMixin {
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   private static final XrayModule xrayModule = ClientContext.getModule(XrayModule.class);

   @ModifyReturnValue(method = "itemRenderType", at = @At("RETURN"))
   private static RenderType applyTranslucentHandRenderType(RenderType original) {
      return noRenderModule.getHandOpacity() != 1.0F && RenderUtils.getRenderColor()[3] != 255 ? Sheets.translucentItemSheet() : original;
   }

   @ModifyReturnValue(method = "layer", at = @At("RETURN"))
   private static ChunkSectionLayer applyXrayTranslucentChunkLayer(ChunkSectionLayer original) {
      BlockState blockState = MeshBlockContext.get();
      if (blockState == null) return original;
      return xrayModule.isToggled()
            && !xrayModule.alphaSetting.isAtMinimum()
            && !xrayModule.alphaSetting.isAtMaximum()
            && !xrayModule.shouldRenderBlock(BlockPos.ZERO, blockState.getBlock())
         ? ChunkSectionLayer.TRANSLUCENT
         : original;
   }
}
