package dev.argus.mixin;

import dev.argus.client.managers.ModuleRegistry;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderType.class)
public interface RenderTypeColorModuleBindings {
   @Invoker(value = "argusclient$bindRenderTypeColorModules", remap = false)
   static void bind(ModuleRegistry registry) {
      throw new AssertionError("Mixin invoker was not applied");
   }
}
