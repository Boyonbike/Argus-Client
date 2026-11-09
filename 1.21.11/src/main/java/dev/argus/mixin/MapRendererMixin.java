package dev.argus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.NoRenderModule;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.MapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MapRenderer.class)
public class MapRendererMixin {
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);

   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   private void suppressMapRendering(
      MapRenderState mapState, PoseStack poses, SubmitNodeCollector collector, boolean ignoredRenderFlag, int packedLight, CallbackInfo callback
   ) {
      if (noRenderModule.shouldHideMaps()) {
         callback.cancel();
      }
   }
}
