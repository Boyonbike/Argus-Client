package dev.argus.client.utils.combat;

import net.minecraft.world.damagesource.DamageSource;

public interface RemotePlayerDamageAccess {
   default boolean damageSuper(DamageSource source, float amount) {
      return true;
   }
}
