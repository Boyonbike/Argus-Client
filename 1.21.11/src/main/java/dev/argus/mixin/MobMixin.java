package dev.argus.mixin;

import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.movement.EntityControlModule;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public class MobMixin {
   private static EntityControlModule entityControlModule = ClientContext.getModule(EntityControlModule.class);

   @Inject(method = "isSaddled", at = @At("HEAD"), cancellable = true)
   private void forceSaddledForEntityControl(CallbackInfoReturnable<Boolean> callback) {
      if (entityControlModule.isToggled()) {
         callback.setReturnValue(true);
      }
   }

   @Unique
   private static void argusclient$bindMobModules(ModuleRegistry moduleRegistry) {
      entityControlModule = (EntityControlModule)moduleRegistry.getModule(EntityControlModule.class);
   }
}
