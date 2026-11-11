package dev.argus.mixin;

import dev.argus.client.managers.ModuleRegistry;
import net.minecraft.client.resources.model.Material;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Material.class)
public interface MaterialModuleBindings {
   @Invoker(value = "argusclient$bindMaterialModules", remap = false)
   static void bind(ModuleRegistry registry) {
      throw new AssertionError("Mixin invoker was not applied");
   }
}
