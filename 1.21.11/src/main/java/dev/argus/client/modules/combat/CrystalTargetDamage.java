package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record CrystalTargetDamage(LivingEntity target, double damage, CrystalPlacementKind kind, CrystalLethality lethality) {
   static CrystalTargetDamage calculateForBase(AutoCrystalModule module, BlockPos base, boolean hasCrystalBase, List<LivingEntity> targets) {
      CrystalTargetDamage bestTargetDamage = null;

      for (LivingEntity candidate : targets) {
         if (!module.isInvalidTarget(candidate)) {
            double baseY = base.getY();
            double targetY = candidate.getY();
            if (!(baseY > targetY + 1.5) || hasCrystalBase) {
               AABB targetBounds = module.getTargetBounds(candidate);
               double minimumDamage = CrystalDamagePolicy.PLACE.getMinimumDamage(candidate);
               double damage = module.calculateExplosionDamage(base, candidate, targetBounds, true);
               CrystalLethality lethality = CrystalLethality.classifyLethality(candidate, damage);
               Item crystalItem = Items.END_CRYSTAL;
               boolean holdingCrystal = candidate.isHolding(crystalItem);
               if (!module.ignoreNakedsSetting.getValue() || PlayerStateUtils.hasArmor(candidate) || holdingCrystal) {
                  Vec3 boundsCenterSnapshot = BoundingBoxUtils.getBottomCenter(targetBounds);
                  Vec3 baseCenter = Vec3.atBottomCenterOf(base);
                  double distance = boundsCenterSnapshot.distanceTo(baseCenter);
                  Float crystalRange = module.crystalRangeSetting.getValue();
                  if (!(distance > crystalRange.floatValue()) || lethality.lethalDamage || module.obsidianDropNearTarget || module.quiverThreatActive) {
                     if (ClientContext.socialRegistry.isFriendEntity(candidate) && candidate != ClientAccess.argusclientMinecraft.player) {
                        if (module.antiFriendKillSetting.getValue() && damage > EntityUtils.getHealthWithAbsorption(candidate)) {
                           return null;
                        }
                     } else if ((!(minimumDamage > damage) && !(damage < 0.5) || lethality.lethalDamage)
                        && (bestTargetDamage == null || damage > bestTargetDamage.damage())) {
                        bestTargetDamage = new CrystalTargetDamage(candidate, damage, CrystalPlacementKind.NONE, lethality);
                     }
                  }
               }
            }
         }
      }

      return bestTargetDamage;
   }
}
