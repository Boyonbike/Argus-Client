package dev.argus.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
   @Accessor("useItemRemaining")
   void setItemUseTimeLeft(int var1);

   @Accessor("swimAmount")
   void argusclient$setLeaningPitch(float var1);

   @Accessor("swimAmount")
   float argusclient$getLeaningPitch();

   @Accessor("swimAmountO")
   float argusclient$getLastLeaningPitch();

   @Accessor("swimAmountO")
   void argusclient$setLastLeaningPitch(float var1);

   @Accessor("fallFlyTicks")
   void argusclient$setGlidingTicks(int var1);

   @Accessor("DATA_EFFECT_PARTICLES")
   static EntityDataAccessor argusclient$potion_swirls() {
      return null;
   }
}
