package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.resources.RegistryDataLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RegistryDataLoader.class)
public class RegistryDataLoaderMixin {
   @ModifyExpressionValue(
      // 26.1.2 checks the error map inside the async freeze lambda.
      method = "lambda$load$2(Ljava/util/List;Ljava/util/Map;Ljava/lang/Void;)Lnet/minecraft/core/RegistryAccess$Frozen;",
      at = @At(value = "INVOKE", target = "Ljava/util/Map;isEmpty()Z")
   )
   private static boolean reportRegistryErrorsAsEmpty(boolean originalEmpty) {
      return true;
   }
}
