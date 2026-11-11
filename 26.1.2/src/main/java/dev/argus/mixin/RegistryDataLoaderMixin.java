package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.resources.RegistryDataLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RegistryDataLoader.class)
public class RegistryDataLoaderMixin {
   @ModifyExpressionValue(
      method = "load(Lnet/minecraft/resources/RegistryDataLoader$LoaderFactory;Ljava/util/List;Ljava/util/List;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;",
      at = @At(value = "INVOKE", target = "Ljava/util/Map;isEmpty()Z")
   )
   private static boolean reportRegistryErrorsAsEmpty(boolean originalEmpty) {
      return true;
   }
}
