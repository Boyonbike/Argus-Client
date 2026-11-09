package dev.argus.mixin;

import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.NoRenderModule;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SkyLightEngine.class)
public class SkyLightEngineMixin {
   private static NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);

   @Inject(at = @At("HEAD"), method = "getLowestSourceY", cancellable = true)
   private void suppressSkyLightSource(int chunkX, int chunkZ, int blockY, CallbackInfoReturnable<Integer> callback) {
      if (noRenderModule.isToggled() && noRenderModule.skyLightSetting.getValue()) {
         callback.setReturnValue(15);
         callback.cancel();
      }
   }
}
