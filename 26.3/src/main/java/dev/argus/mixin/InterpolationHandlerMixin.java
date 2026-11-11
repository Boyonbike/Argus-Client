package dev.argus.mixin;

import dev.argus.client.ClientContext;
import dev.argus.client.modules.render.NoRenderModule;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InterpolationHandler.class)
public abstract class InterpolationHandlerMixin {
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
   @Shadow
   @Final
   private Entity entity;

   @Shadow
   public abstract void cancel();

   @Inject(method = "interpolateTo", at = @At("HEAD"), cancellable = true)
   private void suppressEntityInterpolation(Vec3 position, float yaw, float pitch, CallbackInfo callback) {
      if (noRenderModule.isToggled() && noRenderModule.interpolationSetting.getValue()) {
         this.entity.snapTo(position, yaw, pitch);
         this.cancel();
         callback.cancel();
      }
   }
}
