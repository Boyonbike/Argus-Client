package dev.argus.mixin;

import dev.argus.client.managers.ModuleRegistry;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TextureTransform.class)
public interface TextureTransformModuleBindings {
   @Invoker(value = "argusclient$bindTextureTransformModules", remap = false)
   static void bind(ModuleRegistry registry) {
      throw new AssertionError("Mixin invoker was not applied");
   }
}
