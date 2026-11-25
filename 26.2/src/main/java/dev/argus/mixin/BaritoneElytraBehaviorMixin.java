package dev.argus.mixin;

import dev.argus.client.integrations.baritone.BaritoneElytraOverrides;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Baritone forces a firework when it thinks it's stuck and on every tick in lava, whatever its settings say, and prints
 * "no fireworks" each tick when it has none. Baritone's release jars keep class names but scramble method names, so its
 * firework method is found by its parameters. Ported from Comet's BaritoneElytraBehaviorMixin.
 */
@Pseudo
@Mixin(targets = "baritone.process.elytra.ElytraBehavior", remap = false)
public abstract class BaritoneElytraBehaviorMixin {
   @Inject(method = "*(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;ZZ)V", at = @At("HEAD"), cancellable = true, require = 0)
   private void argus$noFireworks(CallbackInfo ci) {
      if (BaritoneElytraOverrides.noFireworks()) {
         ci.cancel();
      }
   }
}
