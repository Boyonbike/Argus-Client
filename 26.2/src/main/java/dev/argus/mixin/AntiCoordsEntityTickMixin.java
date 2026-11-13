package dev.argus.mixin;

import dev.argus.client.modules.misc.AntiCoordsModule;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Your movement collides during the entity tick, so it keeps the real offsets there and stays in sync with the server. */
@Mixin(ClientLevel.class)
public abstract class AntiCoordsEntityTickMixin {
   @Inject(method = "tickEntities", at = @At("HEAD"))
   private void argus$antiCoordsRealOffsets(CallbackInfo ci) {
      AntiCoordsModule.entityTick(true);
   }

   @Inject(method = "tickEntities", at = @At("TAIL"))
   private void argus$antiCoordsSpoofedOffsets(CallbackInfo ci) {
      AntiCoordsModule.entityTick(false);
   }
}
