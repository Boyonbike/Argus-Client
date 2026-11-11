package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
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

   // Hot path of the sky light engine; a return-value modifier avoids a callback allocation per lookup.
   @ModifyReturnValue(method = "getLowestSourceY", at = @At("RETURN"))
   private int suppressSkyLightSource(int original) {
      return noRenderModule.isToggled() && noRenderModule.skyLightSetting.getValue() ? 15 : original;
   }
}
