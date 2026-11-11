package dev.argus.mixin;

import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.movement.FastWebModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WebBlock.class)
public class WebBlockMixin {
   private static FastWebModule fastWebModule = ClientContext.getModule(FastWebModule.class);

   @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
   private void cancelGrimWebEffects(
      BlockState state, Level level, BlockPos position, Entity entity, InsideBlockEffectApplier effects, boolean bl, CallbackInfo callback
   ) {
      if (fastWebModule.isGrimEnabled()) {
         callback.cancel();
      }
   }

   @Unique
   private static void argusclient$bindWebBlockModules(ModuleRegistry moduleRegistry) {
      fastWebModule = (FastWebModule)moduleRegistry.getModule(FastWebModule.class);
   }
}
