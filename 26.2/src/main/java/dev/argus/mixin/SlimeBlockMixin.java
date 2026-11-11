package dev.argus.mixin;

import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.movement.NoSlowModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SlimeBlock.class)
public class SlimeBlockMixin {
   private static NoSlowModule noslow = ClientContext.getModule(NoSlowModule.class);

   @Inject(method = "stepOn", at = @At("HEAD"), cancellable = true)
   private void suppressSlimeStepEffect(Level level, BlockPos blockPos, BlockState blockState, Entity entity, CallbackInfo callback) {
      if (noslow.isToggled() && noslow.slimeSetting.getValue()) {
         callback.cancel();
      }
   }

   @Inject(method = "bounceUp", at = @At("HEAD"), cancellable = true)
   private void suppressSlimeBounce(Entity entity, CallbackInfo callback) {
      if (noslow.isToggled() && noslow.slimeSetting.getValue() && noslow.noSlimeBounceSetting.getValue()) {
         callback.cancel();
      }
   }

   @Unique
   private static void argusclient$bindSlimeBlockModules(ModuleRegistry moduleRegistry) {
      noslow = (NoSlowModule)moduleRegistry.getModule(NoSlowModule.class);
   }
}
