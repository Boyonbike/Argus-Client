package dev.argus.client.modules.misc;

import dev.argus.client.ClientAccess;
import dev.argus.client.settings.NamedEntry;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

public enum KillEffectsParticlesChoice implements ClientAccess, NamedEntry {
   OFF("Off"),
   ASHES("Ashes"),
   CUSTOM("Custom");

   private final String displayName;

   @Override
   public String getName() {
      return this.displayName;
   }

   KillEffectsParticlesChoice(String displayName) {
      this.displayName = displayName;
   }

   public ParticleOptions chooseParticle(KillEffectsModule module) {
      return switch (this) {
         case ASHES -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            yield random.nextBoolean() ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH;
         }
         case CUSTOM -> this.selectCustomParticle(module);
         default -> null;
      };
   }

   public void emitParticles(ParticleOptions type, Vec3 position) {
      ThreadLocalRandom random = ThreadLocalRandom.current();

      for (int index = 0; index < random.nextInt(35) + 25; index++) {
         ParticleEngine particleEngine = ClientAccess.argusclientMinecraft.particleEngine;
         double x = position.x();
         x += random.nextGaussian() * 0.2;
         double y = position.y() + 2.0;
         y -= random.nextGaussian() * 0.5;
         double z = position.z();
         z += random.nextGaussian() * 0.2;
         double velocityY = -0.1 - random.nextGaussian() * 0.5;
         Particle particle = particleEngine.createParticle(type, x, y, z, 0.0, velocityY, 0.0);
         if (particle != null) {
            particle.lifetime += 15;
         }
      }
   }

   private ParticleOptions selectCustomParticle(KillEffectsModule module) {
      ArrayList particleTypes = new ArrayList<>(module.particleTypesSetting.getValue());
      Predicate<ParticleType> invalidParticleType = type -> !(type instanceof ParticleOptions);
      particleTypes.removeIf(invalidParticleType);
      if (particleTypes.isEmpty()) {
         return null;
      }

      ThreadLocalRandom randomSnapshot = ThreadLocalRandom.current();
      int typeCount = particleTypes.size();
      int index = randomSnapshot.nextInt(typeCount);
      return (ParticleOptions)particleTypes.get(index);
   }
}
