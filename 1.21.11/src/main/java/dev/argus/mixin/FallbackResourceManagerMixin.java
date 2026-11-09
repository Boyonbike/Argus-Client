package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.argus.client.render.BuiltinResourcePack;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FallbackResourceManager.class)
public class FallbackResourceManagerMixin {
   @ModifyReturnValue(method = "getResource", at = @At("RETURN"))
   private Optional<Resource> resolveClientResource(Optional<Resource> originalResource, @Local(argsOnly = true) Identifier resourceId) {
      return BuiltinResourcePack.resolveResource(resourceId, originalResource);
   }
}
