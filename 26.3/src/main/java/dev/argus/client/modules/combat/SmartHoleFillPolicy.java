package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.HoleManager;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.math.Vec3Utils;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.client.utils.world.HoleInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SmartHoleFillPolicy extends PlainHoleFillPolicy {
   public SmartHoleFillPolicy(HoleFillModule holeFillModule) {
      super(holeFillModule);
   }

   @Override
   public boolean shouldFill(HoleInfo hole) {
      ClientLevel levelSnapshot = ClientAccess.argusclientMinecraft.level;

      for (AbstractClientPlayer player : levelSnapshot.players()) {
         Setting<Boolean> selfSafetySettingRef = this.holeFillModule.selfSafetySetting;
         if (!selfSafetySettingRef.getValue() || this.isSelfSafetySatisfied(hole)) {
            Setting<Boolean> ignoreNakedsSettingRef = this.holeFillModule.ignoreNakedsSetting;
            if ((!ignoreNakedsSettingRef.getValue() || PlayerStateUtils.hasArmor(player))
               && !player.isDeadOrDying()
               && ClientAccess.argusclientMinecraft.player != player
               && !ClientContext.socialRegistry.isFriendPlayer(player)) {
               HoleManager holeManagerSnapshot = ClientContext.holeManager;
               Vec3 playerPositionForBlockPos = player.position();
               BlockPos playerBlockPosSnapshot = BlockPos.containing(playerPositionForBlockPos);
               if (!holeManagerSnapshot.intersectsHole(playerBlockPosSnapshot) && super.shouldFill(hole)) {
                  Setting<Boolean> extrapolationSettingRef = this.holeFillModule.extrapolationSetting;
                  if (!extrapolationSettingRef.getValue()) {
                     AABB currentBounds = BoundingBoxUtils.getInterpolationBounds(player);
                     if (!this.shouldIgnorePlayerBounds(hole, player, currentBounds)) {
                        return true;
                     }
                  } else {
                     Setting<Integer> ticksSettingRef = this.holeFillModule.ticksSetting;

                     for (int predictionTicks = BoundingBoxUtils.getPredictionTicks(ticksSettingRef); predictionTicks > 1; predictionTicks--) {
                        AABB predictedBounds = ClientContext.movementPredictionManager.getPredictedBoundingBox(player, predictionTicks);
                        if (!this.shouldIgnorePlayerBounds(hole, player, predictedBounds)) {
                           return true;
                        }
                     }
                  }
               }
            }
         }
      }

      return false;
   }

   private boolean shouldIgnorePlayerBounds(HoleInfo hole, Player player, AABB bounds) {
      boolean playerConditionResult = true;
      if (MovementPhysics.isMoving(player)) {
         BlockPos conditionHolePos = hole.pos();
         Vec3 conditionHoleCenter = Vec3.atCenterOf(conditionHolePos);
         playerConditionResult = MovementPhysics.isMovingToward(player, conditionHoleCenter);
      }

      Vec3 boundsCenter = BoundingBoxUtils.getBottomCenter(bounds);
      BlockPos rangeHolePos = hole.pos();
      Vec3 holeCenterSnapshot = Vec3.atCenterOf(rangeHolePos);
      Vec3 boundsCenterSnapshot = BoundingBoxUtils.getBottomCenter(bounds);
      float smartDistance = Vec3Utils.horizontalDistance(holeCenterSnapshot, boundsCenterSnapshot);
      Setting<Float> smartRangeSettingRef = this.holeFillModule.smartRangeSetting;
      Float smartRange = smartRangeSettingRef.getValue();
      if (!(smartDistance > smartRange)) {
         double centerY = boundsCenter.y();
         BlockPos heightHolePos = hole.pos();
         if (!(centerY <= heightHolePos.getY())) {
            double boundsMinY = bounds.minY;
            BlockPos differenceHolePos = hole.pos();
            double heightDifference = boundsMinY - differenceHolePos.getY();
            if (!(heightDifference > 4.0) && playerConditionResult) {
               return false;
            }
         }
      }

      return true;
   }

   private boolean isSelfSafetySatisfied(HoleInfo hole) {
      HoleManager holeManagerSnapshot = ClientContext.holeManager;
      BlockPos playerBlockPosSnapshot = BlockPos.containing(ClientAccess.argusclientMinecraft.player.position());
      if (!holeManagerSnapshot.intersectsHole(playerBlockPosSnapshot)) {
         Vec3 playerPositionSnapshot = ClientAccess.argusclientMinecraft.player.position();
         BlockPos holePos = hole.pos();
         Vec3 holeCenter = Vec3.atCenterOf(holePos);
         double distance = playerPositionSnapshot.distanceTo(holeCenter);
         if (!(distance >= 2.0)) {
            return false;
         }
      }

      return true;
   }
}
