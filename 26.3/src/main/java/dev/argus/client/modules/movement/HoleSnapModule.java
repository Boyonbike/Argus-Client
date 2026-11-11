package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.managers.HoleManager;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.settings.options.HoleSnapPriorityChoice;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.BlockRaycastClassifier;
import dev.argus.client.utils.combat.BlockRaycastContext;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.combat.RaycastUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.math.Vec3Utils;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.client.utils.movement.WallsVelocityHandler;
import dev.argus.client.utils.world.HoleInfo;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class HoleSnapModule extends Module {
   protected final Setting<HoleSnapPriorityChoice> prioritySetting = this.add(new EnumSetting<>("Priority", HoleSnapPriorityChoice.DISTANCE));
   private final Setting<Float> heightSetting = this.add(new NumberSetting<>("Height", 2.0F, 1.0F, 6.0F).withUnit("m"));
   private final Setting<Float> timeoutSetting = this.add(new NumberSetting<>("Timeout", 0.5F, 0.0F, 2.0F).withUnit("s"));
   private final Setting<Float> rangeSetting = this.add(new NumberSetting<>("Range", 1.0F, 0.1F, 3.0F).withUnit("m"));
   private final Setting<Float> speedSetting;
   private final Setting<Float> pitchSetting;
   private final Setting<Boolean> autoDisableSetting;
   private final Setting<Boolean> pauseStepSetting;
   private final Setting<Boolean> pauseInBlocksSetting;
   protected final Setting<Boolean> shiftSetting;
   protected final Setting<Integer> factorSetting;
   protected final Setting<Integer> chargeSetting;
   private final Setting<Boolean> directionalSetting;
   private final Setting<Boolean> softSetting;
   protected final Setting<Boolean> alignSetting;
   protected final Setting<Boolean> onlyWideSetting;
   private final Setting<Float> alignPitchSetting;
   private final HoleSnapChargeController chargeController;
   private final ElapsedTimer stepPauseTimer;
   public final ElapsedTimer targetTimeoutTimer;
   private HoleSnapModule.SnapTarget alignmentTarget;
   private boolean wasInHole;
   private boolean forwardOverrideActive;

   @Override
   public void onToggle() {
      this.targetTimeoutTimer.clear();
   }

   @EventHandler(priority = -100)
   private void onLocalPlayerPreTickEvent(LocalPlayerPreTickEvent event) {
      if (this.forwardOverrideActive && !this.shouldPauseSnapping() && !this.isWaitingForTarget() && RotationManager.isMovementSyncEnabled()) {
         ClientContext.inputOverrides.requestForward(true);
         ClientContext.inputOverrides.requestForwardMovement(1.0F);
      }

      AABB playerBounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
      double playerY = ClientAccess.argusclientMinecraft.player.getY();
      AABB feetBounds = playerBounds.setMaxY(playerY).inflate(0.05);
      if (ClientAccess.argusclientMinecraft.player.isInWater()) {
         HoleManager holeManager = ClientContext.holeManager;
         BlockPos belowPlayer = ClientAccess.argusclientMinecraft.player.blockPosition().below();
         if (holeManager.intersectsHole(belowPlayer) && BlockPos.betweenClosedStream(feetBounds).count() == 1L) {
            ClientContext.inputOverrides.requestSneak(true);
         }
      }
   }

   @EventHandler
   private void onLocalPlayerMoveEvent(LocalPlayerMoveEvent event) {
      if (!this.shouldPauseSnapping()) {
         this.alignmentTarget = this.findAlignmentTarget();
         HoleManager holeManagerSnapshot = ClientContext.holeManager;
         BlockPos playerBlock = PlayerStateUtils.getLocalRoundedBlockPosition();
         if (holeManagerSnapshot.intersectsHole(playerBlock)) {
            if (this.autoDisableSetting.getValue() && (this.alignmentTarget == null || this.alignmentTarget.distance < 0.01)) {
               this.disable();
               this.forwardOverrideActive = false;
               return;
            }

            this.targetTimeoutTimer.reset();
         }

         if (!this.isWaitingForTarget() && !RotationManager.isMovementSyncEnabled()) {
            HoleSnapModule.SnapTarget target = this.getSnapTarget();
            if (target == null) {
               this.forwardOverrideActive = false;
            } else {
               if (this.softSetting.getValue() && this.directionalSetting.getValue()) {
                  double directionalDistance = target.distance();
                  if (directionalDistance < 0.01) {
                     return;
                  }
               }

               Vec3 targetPosition = target.position();
               Vec3 playerPosition = ClientAccess.argusclientMinecraft.player.position();
               Vec3 direction = targetPosition.subtract(playerPosition).with(Axis.Y, 0.0).normalize();
               double movementSpeed = MovementPhysics.getBaseMoveSpeed(true);
               Float speedMultiplier = this.speedSetting.getValue();
               double scaledSpeed = movementSpeed * speedMultiplier.floatValue();
               double targetDistance = target.distance();
               double stepSpeed = Math.min(scaledSpeed, targetDistance);
               Vec3 movement = direction.scale(stepSpeed);
               double deltaZ = movement.z;
               double deltaX = movement.x;
               event.setHorizontalVelocity(deltaX, deltaZ);
            }
         }
      }
   }

   protected boolean hasBlockingRaycast(Vec3 start, Vec3 end) {
      BlockRaycastContext context = new BlockRaycastContext(
         start, end, Block.COLLIDER, Fluid.NONE, ClientAccess.argusclientMinecraft.player, BlockRaycastClassifier.NO_OVERRIDE
      );
      BlockHitResult hit = RaycastUtils.raycast(context);
      return Type.MISS != hit.getType();
   }

   @EventHandler
   private void onRotationUpdate(RotationRequestEvent event) {
      ClientContext.playerTickController.releaseMultiplier(this);
      this.alignmentTarget = this.findAlignmentTarget();
      boolean inHole = ClientContext.holeManager.isInHole();
      if (ClientAccess.argusclientMinecraft.player.onGround() && inHole != this.wasInHole) {
         this.wasInHole = inHole;
         this.stepPauseTimer.reset();
      }

      if (!this.shouldPauseSnapping() && !this.isWaitingForTarget()) {
         HoleSnapModule.SnapTarget target = this.getSnapTarget();
         if (target == null) {
            this.forwardOverrideActive = false;
         } else {
            if (RotationManager.isMovementSyncEnabled()) {
               ClientContext.rotationManager.forceRotationOverride();
               RotationManager targetRotationManagerSnapshot = ClientContext.rotationManager;
               Vec3 targetPosition = target.position();
               float[] angles = RotationManager.getRotationsToPosition(targetPosition);
               targetRotationManagerSnapshot.requestRotation(angles, 99999, true);
               this.forwardOverrideActive = true;
            }

            if (this.shiftSetting.getValue()) {
               this.chargeController.requestShift();
            }
         }
      }
   }

   private boolean shouldPauseSnapping() {
      float playerPitch = ClientAccess.argusclientMinecraft.player.getXRot();
      Float requiredPitch = this.pitchSetting.getValue();
      return playerPitch < requiredPitch && this.pitchSetting.getValue() != 0.0F
         ? true
         : !ClientContext.serverStateManager.getSetbackTimer().hasElapsed(250L)
            || ClientAccess.argusclientMinecraft.player.isSpectator()
            || ClientAccess.argusclientMinecraft.player.isFallFlying();
   }

   private HoleSnapModule.SnapTarget findAlignmentTarget() {
      if (!this.alignSetting.getValue()) {
         return null;
      }

      if (!ClientContext.holeManager.isInHole()) {
         return null;
      }

      if (ClientAccess.argusclientMinecraft.player.onGround() && !MovementPhysics.isLocalPlayerMoving()) {
         float pitch = ClientAccess.argusclientMinecraft.player.getXRot();
         Float minimumPitch = this.alignPitchSetting.getValue();
         if (pitch < minimumPitch && !this.alignPitchSetting.isAtMinimum()) {
            return null;
         }

         HoleManager alignedHoleManager = ClientContext.holeManager;
         BlockPos playerBlock = PlayerStateUtils.getLocalRoundedBlockPosition();
         HoleInfo hole = alignedHoleManager.findIntersectingHole(playerBlock);
         if (hole == null) {
            return null;
         }

         double holeSize = hole.bounds().getSize();
         if (holeSize == 1.0 && this.onlyWideSetting.getValue()) {
            return null;
         }

         AABB holeBounds = hole.bounds();
         Vec3 holeCenter = BoundingBoxUtils.getBottomCenter(holeBounds);
         double distance = ClientAccess.argusclientMinecraft.player.position().distanceTo(holeCenter);
         double minimumDistance = RotationManager.isMovementSyncEnabled() ? 0.1 : 0.0;
         return distance < minimumDistance ? null : new HoleSnapModule.SnapTarget(holeCenter, distance);
      } else {
         return null;
      }
   }

   private boolean isWaitingForTarget() {
      if (this.alignmentTarget != null) {
         return false;
      }

      ElapsedTimer timeoutTimerSnapshot = this.targetTimeoutTimer;
      double timeoutSeconds = this.timeoutSetting.getValue().floatValue();
      return !timeoutTimerSnapshot.hasElapsed(timeoutSeconds, TimeUnit.SECONDS);
   }

   public HoleSnapModule.SnapTarget getSnapTarget() {
      if (this.pauseInBlocksSetting.getValue() && WallsVelocityHandler.hasRaisedPlayerCollision()) {
         return null;
      }

      if (this.alignmentTarget != null) {
         return this.alignmentTarget;
      }

      double bestDistance = 9999.0;
      HoleInfo bestHole = null;
      Vec3 bestPosition = null;
      Vec3 footPosition = ClientAccess.argusclientMinecraft.player.position().add(0.0, 0.01, 0.0);
      double heightOffsetY = ClientAccess.argusclientMinecraft.player.getBbHeight();
      Vec3 headPosition = footPosition.add(0.0, heightOffsetY, 0.0);

      for (Object holeEntry : ClientContext.holeManager.getHoles()) {
         HoleInfo hole = (HoleInfo)holeEntry;
         Vec3 candidatePosition = hole.bounds().getCenter();
         double candidateDistance = footPosition.distanceTo(candidatePosition);
         if (this.softSetting.getValue() && this.directionalSetting.getValue()) {
            Axis yAxis = Axis.Y;
            double playerY = footPosition.y();
            double candidateY = candidatePosition.y;
            double candidateTop = candidatePosition.y;
            Setting<Float> heightSettingSnapshot = this.heightSetting;
            Float heightValue = heightSettingSnapshot.getValue();
            candidateTop += heightValue.floatValue();
            double maximumCandidateY = candidateTop - 0.5;
            double clampedY = Mth.clamp(playerY, candidateY, maximumCandidateY);
            candidatePosition = candidatePosition.with(yAxis, clampedY);
         } else {
            candidatePosition = candidatePosition.add(0.0, 0.5, 0.0);
            Vec3 capturedPlayerPosition = ClientAccess.argusclientMinecraft.player.position();
            candidateDistance = Vec3Utils.horizontalDistance(candidatePosition, capturedPlayerPosition);
            double verticalDifference = ClientAccess.argusclientMinecraft.player.getY() - candidatePosition.y;
            double heightDifference = Math.abs(verticalDifference);
            Setting<Float> heightSettingSnapshot = this.heightSetting;
            Float maximumHeight = heightSettingSnapshot.getValue();
            if (heightDifference > maximumHeight.floatValue()) {
               continue;
            }
         }

         double holeY = hole.pos().getY();
         if (!(holeY >= footPosition.y())
            && !hole.trapped()
            && (!this.directionalSetting.getValue() || MovementPhysics.isMovingToward(candidatePosition) || !(candidateDistance > 0.05))) {
            Float range = this.rangeSetting.getValue();
            if (!(candidateDistance > range.floatValue())) {
               ClientLevel level = ClientAccess.argusclientMinecraft.level;
               AABB collisionBounds = hole.bounds();
               double playerMaxY = ClientAccess.argusclientMinecraft.player.getBoundingBox().maxY;
               AABB boundsForCollision = collisionBounds.setMaxY(playerMaxY);
               if (level.noCollision(boundsForCollision)) {
                  Vec3 firstRayEnd = candidatePosition.with(Axis.Y, footPosition.y());
                  if (!this.hasBlockingRaycast(footPosition, firstRayEnd)) {
                     Vec3 secondRayEnd = candidatePosition.with(Axis.Y, headPosition.y());
                     if (!this.hasBlockingRaycast(headPosition, secondRayEnd)) {
                        Vec3 thirdRayEnd = candidatePosition.with(Axis.Y, headPosition.y());
                        if (!this.hasBlockingRaycast(candidatePosition, thirdRayEnd)
                           && (bestHole == null || this.prioritySetting.getValue().getComparator().compare(hole, bestHole) < 0)) {
                           bestHole = hole;
                           bestDistance = candidateDistance;
                           bestPosition = candidatePosition;
                        }
                     }
                  }
               }
            }
         }
      }

      if (bestHole == null) {
         return null;
      }

      Float finalRange = this.rangeSetting.getValue();
      return bestDistance > finalRange.floatValue() ? null : new HoleSnapModule.SnapTarget(bestPosition, bestDistance);
   }

   public boolean shouldPauseStep() {
      return !this.stepPauseTimer.hasElapsed(400L) && ClientContext.holeManager.isInHole() && this.pauseStepSetting.getValue();
   }

   private static String[] createAliases() {
      return new String[]{"holetp", "anchor"};
   }

   public HoleSnapModule() {
      super("HoleSnap", "Pushes you into holes as you go past them.", ModuleCategory.MOVEMENT, createAliases());
      Predicate<Object> speedVisibility = ignored -> !RotationManager.isMovementSyncEnabled();
      this.speedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 0.5F, 3.0F, speedVisibility));
      this.pitchSetting = this.add(new NumberSetting<>("Pitch", 0.0F, 0.0F, 90.0F).withUnit("°"));
      this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", true));
      this.pauseStepSetting = this.add(new BooleanSetting("PauseStep", false));
      this.pauseInBlocksSetting = this.add(new BooleanSetting("PauseInBlocks", false));
      this.shiftSetting = this.add(new BooleanSetting("Shift", false).markVisibilityDependency());
      this.factorSetting = this.add(new NumberSetting<>("Factor", 2, 1, 6).dependsOn(this.shiftSetting));
      this.chargeSetting = this.add(new NumberSetting<>("Charge", 3, 1, 16).dependsOn(this.shiftSetting));
      this.directionalSetting = this.add(new BooleanSetting("Directional", false).markVisibilityDependency());
      this.softSetting = this.add(new BooleanSetting("Soft", false).dependsOn(this.directionalSetting));
      this.alignSetting = this.add(new BooleanSetting("Align", false).markVisibilityDependency());
      this.onlyWideSetting = this.add(new BooleanSetting("OnlyWide", false).dependsOn(this.alignSetting));
      this.alignPitchSetting = this.add(new NumberSetting<>("AlignPitch", 0.0F, 0.0F, 90.0F).withUnit("°").dependsOn(this.alignSetting));
      this.chargeController = new HoleSnapChargeController(this);
      this.stepPauseTimer = new ElapsedTimer();
      this.targetTimeoutTimer = new ElapsedTimer();
      this.pitchSetting.setBoundaryLabel("None", SettingLabelCondition.MIN);
      this.alignPitchSetting.setBoundaryLabel("Any", SettingLabelCondition.MIN);
   }

   public record SnapTarget(Vec3 position, double distance) {
   }
}
