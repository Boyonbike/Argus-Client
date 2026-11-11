package dev.argus.mixin;

import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.NoRenderModule;
import net.minecraft.world.entity.AbstractInterpolationHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionPath;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractInterpolationHandler.class)
public abstract class InterpolationHandlerMixin {
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   @Shadow
   @Final
   protected Entity entity;

   @Inject(method = "interpolateTo(Lnet/minecraft/world/entity/PositionPath;FFZ)Z", at = @At("HEAD"), cancellable = true)
   private void suppressEntityInterpolation(
      PositionPath path, float yaw, float pitch, boolean snap, CallbackInfoReturnable<Boolean> callback
   ) {
      if (noRenderModule.isToggled() && noRenderModule.interpolationSetting.getValue()) {
         this.entity.snapTo(path.endPosition(), yaw, pitch);
         callback.setReturnValue(false);
         callback.cancel();
      }
   }
}
