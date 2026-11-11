package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import java.util.Comparator;
import java.util.stream.Stream;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public final class AutoMineTargetSelector implements ClientAccess {
   private final AutoMineModule module;

   public AABB getTargetFootprintBounds(Player target) {
      AABB boundsSnapshot = target.getBoundingBox();
      double shrink = -BoundingBoxUtils.blockCornerInset;
      boundsSnapshot = boundsSnapshot.inflate(shrink, 0.0, shrink);
      double maximumY = target.getY();
      return boundsSnapshot.setMaxY(maximumY);
   }

   AutoMineTargetSelector(AutoMineModule module) {
      this.module = module;
   }

   public Player selectTarget() {
      Stream<AbstractClientPlayer> filteredCandidates = ClientAccess.argusclientMinecraft
         .level
         .players()
         .stream()
         .filter(
            candidate -> candidate.isAlive()
               && candidate != ClientAccess.argusclientMinecraft.player
               && ClientAccess.argusclientMinecraft.player.distanceTo(candidate) <= this.module.getRange() + 2.0F
               && !ClientContext.socialRegistry.isFriendPlayer(candidate)
         )
         .filter(candidate -> !this.module.ignoreNakedSetting.getValue() || PlayerStateUtils.hasArmor(candidate));
      Comparator<AbstractClientPlayer> yawOrder = Comparator.comparing(candidate -> {
         float yawSnapshot = ClientAccess.argusclientMinecraft.player.getYRot();
         float candidateYaw = RotationManager.getRotationsToEntity(candidate)[0];
         float yawDifference = Mth.degreesDifferenceAbs(yawSnapshot, candidateYaw);
         return yawDifference;
      });
      return filteredCandidates.min(yawOrder).orElse(null);
   }
}
