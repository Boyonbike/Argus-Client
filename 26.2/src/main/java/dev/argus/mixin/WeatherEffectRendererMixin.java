package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.AmbienceModule;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.biome.Biome.Precipitation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererMixin implements ClientAccess {
   private static final AmbienceModule ambienceModule = ClientContext.getModule(AmbienceModule.class);

   @ModifyExpressionValue(method = "renderInstances", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;white(F)I"))
   private int argusclient$applyWeatherOpacity(int originalColor) {
      if (ambienceModule.isToggled() && ambienceModule.worldWeatherSetting.getValue()) {
         float opacityMultiplier = ambienceModule.opacitySetting.getValue().intValue() / 100.0F;
         int adjustedAlpha = (int)(ARGB.alpha(originalColor) * opacityMultiplier) & 0xFF;
         return adjustedAlpha << 24 | originalColor & 16777215;
      } else {
         return originalColor;
      }
   }

   @ModifyExpressionValue(
      method = "extractRenderState",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I")
   )
   private int argusclient$forceWeatherBelowSurface(int originalHeight) {
      return ambienceModule.isToggled()
            && ambienceModule.worldWeatherSetting.getValue()
            && ambienceModule.weatherAlwaysSetting.getValue()
            && ambienceModule.weatherSetting.getValue().supportsPrecipitation()
         ? Integer.MIN_VALUE
         : originalHeight;
   }

   @ModifyExpressionValue(
      method = "extractRenderState",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/multiplayer/ClientLevel;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"
      )
   )
   private Precipitation argusclient$overridePrecipitation(Precipitation originalPrecipitation) {
      Precipitation configuredPrecipitation = ambienceModule.weatherSetting.getValue().getPrecipitation();
      return ambienceModule.isToggled() && ambienceModule.worldWeatherSetting.getValue() && configuredPrecipitation != null
         ? configuredPrecipitation
         : originalPrecipitation;
   }
}
