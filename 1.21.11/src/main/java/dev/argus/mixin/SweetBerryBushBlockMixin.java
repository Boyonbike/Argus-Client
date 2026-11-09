package dev.argus.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.movement.NoSlowModule;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SweetBerryBushBlock.class)
public class SweetBerryBushBlockMixin {
   private static NoSlowModule noSlowModule = ClientContext.getModule(NoSlowModule.class);

   @WrapWithCondition(
      method = "entityInside",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/Entity;makeStuckInBlock(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V"
      )
   )
   private boolean allowBerryBushSlowdown(Entity entity, BlockState blockState, Vec3 slowdown) {
      return !noSlowModule.isToggled() || !noSlowModule.berryBushSetting.getValue();
   }

   @Unique
   private static void argusclient$bindSweetBerryBushBlockModules(ModuleRegistry moduleRegistry) {
      noSlowModule = (NoSlowModule)moduleRegistry.getModule(NoSlowModule.class);
   }
}
