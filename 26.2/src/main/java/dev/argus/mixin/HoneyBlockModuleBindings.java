package dev.argus.mixin;

import dev.argus.client.managers.ModuleRegistry;
import net.minecraft.world.level.block.HoneyBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HoneyBlock.class)
public interface HoneyBlockModuleBindings {
   @Invoker(value = "argusclient$bindHoneyBlockModules", remap = false)
   static void bind(ModuleRegistry registry) {
      throw new AssertionError("Mixin invoker was not applied");
   }
}
