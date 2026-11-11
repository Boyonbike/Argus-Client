package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.render.AmbienceModule;
import java.util.function.Consumer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelMixin {
   private static AmbienceModule ambience = ClientContext.getModule(AmbienceModule.class);

   @ModifyReturnValue(method = "getRainLevel", at = @At("RETURN"))
   private float overrideRainLevel(float original) {
      if (ambience.isToggled() && ambience.worldWeatherSetting.getValue() && !ambience.isWeatherOverrideSuppressed()) {
         return ambience.weatherSetting.getValue() == AmbienceModule.WeatherChoice.CLEAR ? 0.0F : 1.0F;
      }

      return original;
   }

   @WrapOperation(method = "isRaining", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getRainLevel(F)F"))
   private float readRainLevelForRainCheck(Level level, float partialTick, Operation original) {
      return ambience.worldWeatherSetting.getValue() && ambience.isToggled() && ambience.weatherSetting.getValue() == AmbienceModule.WeatherChoice.CLEAR
         ? (Float)ambience.withWeatherOverrideSuppressed(() -> (Float)original.call(new Object[]{level, partialTick}))
         : (Float)original.call(new Object[]{level, partialTick});
   }

   @ModifyExpressionValue(
      method = "<init>",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;create()Lnet/minecraft/util/RandomSource;"),
      require = 0
   )
   private RandomSource useThreadSafeRandomSource(RandomSource originalRandom) {
      return RandomSource.createThreadSafe();
   }

   @Inject(method = "guardEntityTick", at = @At("HEAD"), cancellable = true)
   private void cancelNullEntityTick(Consumer ticker, Entity entity, CallbackInfo callback) {
      if (entity == null) {
         callback.cancel();
      }
   }

   @Unique
   private static void argusclient$bindLevelModules(ModuleRegistry moduleRegistry) {
      ambience = (AmbienceModule)moduleRegistry.getModule(AmbienceModule.class);
   }
}
