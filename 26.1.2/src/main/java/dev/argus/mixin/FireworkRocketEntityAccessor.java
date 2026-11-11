package dev.argus.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FireworkRocketEntity.class)
public interface FireworkRocketEntityAccessor {
   @Accessor("life")
   void setLife(int var1);

   @Accessor("life")
   int getLife();

   @Accessor("lifetime")
   int getLifeTime();

   @Accessor("attachedToEntity")
   LivingEntity argusclient$getShooter();
}
