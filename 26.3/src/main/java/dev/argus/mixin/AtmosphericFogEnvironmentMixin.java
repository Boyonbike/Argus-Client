package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.SkyboxModule;
import java.awt.Color;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AtmosphericFogEnvironment.class)
public class AtmosphericFogEnvironmentMixin {
   private static final SkyboxModule skyboxModule = ClientContext.getModule(SkyboxModule.class);

   @ModifyReturnValue(method = "getBaseColor", at = @At("RETURN"))
   private org.joml.Vector3fc blendConfiguredFogColor(org.joml.Vector3fc originalColor) {
      if (skyboxModule.isToggled() && skyboxModule.isCurrentDimensionEnabled()) {
         Color configuredColor = skyboxModule.backgroundSetting.getValue();
         float blend = configuredColor.getAlpha() / 255.0F;
         return new org.joml.Vector3f(
            org.joml.Math.lerp(originalColor.x(), configuredColor.getRed() / 255.0F, blend),
            org.joml.Math.lerp(originalColor.y(), configuredColor.getGreen() / 255.0F, blend),
            org.joml.Math.lerp(originalColor.z(), configuredColor.getBlue() / 255.0F, blend)
         );
      } else {
         return originalColor;
      }
   }
}
