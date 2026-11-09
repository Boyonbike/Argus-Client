package dev.argus.mixin;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.ParticleEngineAddEvent;
import dev.argus.client.modules.render.NoRenderModule;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
   private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);

   @Shadow
   @Nullable
   protected abstract Particle makeParticle(ParticleOptions var1, double var2, double var4, double var6, double var8, double var10, double var12);

   @Inject(method = "add", at = @At("HEAD"), cancellable = true)
   private void postParticleAddEvent(Particle particle, CallbackInfo callback) {
      ParticleEngineAddEvent event = new ParticleEngineAddEvent(particle);
      ClientAccess.eventBus.post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
   private void suppressSelectedParticle(
      ParticleOptions options, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> callback
   ) {
      if (noRenderModule.shouldHideParticle(options)) {
         callback.setReturnValue(this.makeParticle(options, x, y, z, velocityX, velocityY, velocityZ));
         callback.cancel();
      }
   }
}
