package dev.argus.mixin;

import dev.argus.client.managers.ModuleRegistry;
import net.minecraft.world.item.component.BundleContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BundleContents.class)
public interface BundleContentsModuleBindings {
   @Invoker(value = "argusclient$bindBundleContentsModules", remap = false)
   static void bind(ModuleRegistry registry) {
      throw new AssertionError("Mixin invoker was not applied");
   }
}
