package dev.argus.client.utils.entity;

import dev.argus.client.settings.options.SocialRole;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public interface PlayerState {
   void argusclient$setRole(SocialRole var1);

   boolean argusclient$isNextToWall();

   SocialRole argusclient$getRole();

   boolean argusclient$wasCrawling();

   default float getItemUseProgress(ItemStack stack, LivingEntity entity) {
      float elapsedMillis = (float)(System.currentTimeMillis() - this.argusclient$getLastEatingTime());
      return elapsedMillis / (stack.getUseDuration(entity) * 50.0F + 50.0F);
   }

   float argusclient$getHealthFactor();

   long argusclient$getLastEatingTime();

   int argusclient$getLastAttackedTicksServer();

   void argusclient$resetLastAttackedServer();
}
