package dev.argus.mixin;

import dev.argus.client.managers.ModuleRegistry;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Level.class)
public interface LevelModuleBindings {
   @Invoker(value = "argusclient$bindLevelModules", remap = false)
   static void bind(ModuleRegistry registry) {
      throw new AssertionError("Mixin invoker was not applied");
   }
}
