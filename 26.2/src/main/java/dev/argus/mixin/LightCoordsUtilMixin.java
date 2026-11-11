package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.AmbienceModule;
import net.minecraft.util.LightCoordsUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightCoordsUtil.class)
public class LightCoordsUtilMixin {
   private static final AmbienceModule ambienceModule = ClientContext.getModule(AmbienceModule.class);

   @ModifyReturnValue(method = "pack", at = @At("RETURN"))
   private static int overridePackedLight(int originalLight) {
      if (ambienceModule.isToggled() && ambienceModule.brightnessSetting.getValue() == AmbienceModule.BrightnessChoice.SCREEN) {
         return 0;
      } else {
         return ambienceModule.isToggled()
               && ambienceModule.occlusionSetting.isVisible()
               && ambienceModule.occlusionSetting.getValue()
               && originalLight == 15728880
            ? 15728864
            : originalLight;
      }
   }
}
