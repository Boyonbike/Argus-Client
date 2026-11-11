package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.utils.combat.DamageUtils;
import dev.argus.client.utils.network.ServerSideDeathState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record AnchorTargetDamage(Player target, double damage) implements ClientAccess {
   static AnchorTargetDamage calculateForBase(AnchorAuraModule module, BlockPos position) {
      boolean ignoreMiningBlock = module.shouldIgnoreMiningBlock();
      AnchorTargetDamage bestDamage = null;

      for (Player candidate : ClientAccess.argusclientMinecraft.level.players()) {
         double validationRange = module.targetRangeSetting.getValue().floatValue();
         if (!isInvalidTarget(candidate, validationRange)) {
            Vec3 explosionPositionSnapshot = position.getCenter();
            AABB boundsSnapshot = candidate.getBoundingBox();
            BlockPos ignoredBaseSnapshot = !module.isAnchorBlock(position) && !ignoreMiningBlock ? null : position;
            double damage = DamageUtils.getExplosionDamage(explosionPositionSnapshot, candidate, boundsSnapshot, 5.0, true, ignoredBaseSnapshot, (BlockPos)null);
            if (!(module.minDamageSetting.getValue().floatValue() > damage) && !(damage < 0.5) && (bestDamage == null || damage > bestDamage.damage())) {
               bestDamage = new AnchorTargetDamage(candidate, damage);
            }
         }
      }

      return bestDamage;
   }

   public static boolean isInvalidTarget(Player target, double range) {
      if (target == null || target == ClientAccess.argusclientMinecraft.player || !target.isAlive()) {
         return true;
      } else if (ClientContext.socialRegistry.isFriendPlayer(target) || ((ServerSideDeathState)target).isServerSideDead()) {
         return true;
      } else {
         return !target.isSpectator() && !target.getAbilities().instabuild ? ClientAccess.argusclientMinecraft.player.distanceTo(target) > range : true;
      }
   }
}
