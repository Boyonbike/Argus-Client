package dev.argus.client.events;

import net.minecraft.client.particle.Particle;

public final class ParticleEngineAddEvent extends CancellableEvent {
   private Particle particle;

   public ParticleEngineAddEvent(Particle particle) {
      this.particle = particle;
   }

   public Particle getParticle() {
      return this.particle;
   }

   public void setParticle(Particle particle) {
      this.particle = particle;
   }
}
