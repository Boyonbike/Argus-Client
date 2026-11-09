package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.utils.entity.EntityUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

public enum CrystalLethality implements ClientAccess {
   NONE(false),
   INTERSECTION(false),
   POP(true),
   KILL(true);

   final boolean lethalDamage;

   CrystalLethality(boolean lethalDamage) {
      this.lethalDamage = lethalDamage;
   }

   public static CrystalLethality classifyLethality(LivingEntity target, double damage) {
      boolean lethalDamage = damage >= EntityUtils.getHealthWithAbsorption(target);
      boolean holdingTotem = target.getOffhandItem().is(Items.TOTEM_OF_UNDYING) || target.getMainHandItem().is(Items.TOTEM_OF_UNDYING);
      return lethalDamage ? (holdingTotem ? POP : KILL) : NONE;
   }

   public boolean isKill() {
      return this == KILL;
   }

   public boolean isIntersection() {
      return this == INTERSECTION;
   }
}
