package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.render.FreeLookModule;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.utils.combat.RaycastUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.math.MathUtils;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

public class RotationLockModule extends Module {
   private final Setting<RotationLockModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", RotationLockModule.ModeChoice.PLAIN));
   private final Setting<Boolean> silentSetting = this.add(new BooleanSetting("Silent", false));
   public final Setting<Boolean> yawSetting = this.add(new BooleanSetting("Yaw", true).markVisibilityDependency());
   public final Setting<Boolean> customYawSetting = this.add(new BooleanSetting("Custom", false).dependsOn(this.yawSetting));
   public final Setting<Float> yawValueSetting = this.add(
      new NumberSetting<>("Value", 0.0F, -180.0F, 180.0F).withUnit("°").dependsOn(this.yawSetting, this.customYawSetting)
   );
   public final Setting<Boolean> pitchSetting = this.add(new BooleanSetting("Pitch", false).markVisibilityDependency());
   public final Setting<Float> pitchValueSetting = this.add(new NumberSetting<>("Value", 0.0F, -90.0F, 90.0F).withUnit("°").dependsOn(this.pitchSetting));
   private final Setting<Float> rangeSetting = this.add(new NumberSetting<>("Range", 8.0F, 4.0F, 64.0F).withUnit("m"));
   private final Setting<Float> disableRangeSetting = this.add(new NumberSetting<>("DisableRange", 0.0F, 0.0F, 32.0F).withUnit("m"));
   private final RotationLockModule.PlainRotationProvider plainRotationProvider = new RotationLockModule.PlainRotationProvider();
   private final RotationLockModule.TargetRotationProvider targetRotationProvider = new RotationLockModule.TargetRotationProvider();
   private String targetName;

   @Override
   public void onEnable() {
      if (this.modeSetting.getValue() != RotationLockModule.ModeChoice.PLAIN) {
         this.selectTarget();
      }
   }

   public float calculateYaw(float currentYaw) {
      Boolean useCustomYaw = this.customYawSetting.getValue();
      if (!useCustomYaw) {
         float quotient = (currentYaw + 1.0F) / 45.0F;
         return Math.round(quotient) * 45;
      } else {
         return this.yawValueSetting.getValue();
      }
   }

   private void selectTarget() {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      Stream<AbstractClientPlayer> candidates = level.players().stream().filter(candidate -> {
         if (ClientAccess.argusclientMinecraft.player != candidate) {
            SocialRegistry friends = ClientContext.socialRegistry;
            return !friends.isFriendPlayer(candidate);
         } else {
            return false;
         }
      }).filter(candidate -> !candidate.isDeadOrDying() ? candidate.isAlive() : false).filter(candidate -> {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         Vec3 eyePosition = player.getEyePosition();
         Vec3 candidatePosition = candidate.position();
         double distance = eyePosition.distanceTo(candidatePosition);
         Float maximumDistance = this.rangeSetting.getValue();
         return distance <= maximumDistance.floatValue();
      });
      AbstractClientPlayer target = candidates.min(Comparator.comparingDouble(this::getTargetYawDistance)).orElse(null);
      String selectedName;
      if (target == null) {
         selectedName = null;
      } else {
         selectedName = target.getGameProfile().name();
      }

      this.targetName = selectedName;
   }

   private Vec3 getInterpolatedEyePosition(Entity entity) {
      float partialTick = RenderUtils.getPartialTick();
      Vec3 position = entity.getPosition(partialTick);
      Pose pose = entity.getPose();
      double eyeHeight = entity.getEyeHeight(pose);
      return position.add(0.0, eyeHeight, 0.0);
   }

   @EventHandler
   private void onSilentRotation(RotationRequestEvent event) {
      if (this.silentSetting.getValue()) {
         RotationLockModule.LockedRotation rotation = this.getLockedRotation();
         if (rotation.hasLockedAxis()) {
            ClientContext.rotationManager.forceRotationOverride();
            RotationManager applyManager = ClientContext.rotationManager;
            float[] angles = new float[]{rotation.getResolvedYaw(), rotation.getResolvedPitch()};
            applyManager.requestRotation(angles, 1200, true);
         }
      }
   }

   @EventHandler
   private void onPlayerRotation(WorldRenderEvent.Main event) {
      if (this.modeSetting.getValue() == RotationLockModule.ModeChoice.TARGET && !this.disableRangeSetting.isAtMinimum()) {
         Vec3 targetPosition = this.findTargetEyePosition();
         if (targetPosition != null) {
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            Vec3 playerEyePosition = player.getEyePosition();
            double distance = targetPosition.distanceTo(playerEyePosition);
            Float threshold = this.disableRangeSetting.getValue();
            if (distance <= threshold.floatValue()) {
               this.disable();
               return;
            }
         }
      }

      Boolean useSilentRotation = this.silentSetting.getValue();
      if (!useSilentRotation) {
         RotationLockModule.LockedRotation rotation = this.getLockedRotation();
         if (rotation.yawLocked()) {
            LocalPlayer yawDestination = ClientAccess.argusclientMinecraft.player;
            float yawInterpolation = event.getPartialTick();
            LocalPlayer yawSource = ClientAccess.argusclientMinecraft.player;
            float yawPartialTick = RenderUtils.getPartialTick();
            float currentYaw = yawSource.getYRot(yawPartialTick);
            float lockedYaw = rotation.getResolvedYaw();
            float interpolatedYaw = Mth.lerp(yawInterpolation, currentYaw, lockedYaw);
            yawDestination.setYRot(interpolatedYaw);
         }

         if (rotation.pitchLocked()) {
            LocalPlayer pitchDestination = ClientAccess.argusclientMinecraft.player;
            float pitchInterpolation = event.getPartialTick();
            LocalPlayer pitchSource = ClientAccess.argusclientMinecraft.player;
            float pitchPartialTick = RenderUtils.getPartialTick();
            float currentPitch = pitchSource.getXRot(pitchPartialTick);
            float lockedPitch = rotation.getResolvedPitch();
            float interpolatedPitch = Mth.lerp(pitchInterpolation, currentPitch, lockedPitch);
            pitchDestination.setXRot(interpolatedPitch);
         }
      }
   }

   private float getTargetYawDistance(Entity target) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      float playerYaw = player.getYRot();
      float targetYaw = RotationManager.getRotationsToEntity(target)[0];
      return Mth.degreesDifferenceAbs(playerYaw, targetYaw);
   }

   private RotationLockModule.LockedRotation getLockedRotation() {
      RotationLockModule.ModeChoice choice = this.modeSetting.getValue();

      return switch (choice) {
         case PLAIN -> this.plainRotationProvider.getRotation();
         case TARGET -> this.targetRotationProvider.getRotation();
      };
   }

   private Vec3 findTargetEyePosition() {
      if (this.targetName == null) {
         return null;
      }

      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      Optional<Vec3> targetPosition = level.players()
         .stream()
         .filter(candidate -> candidate.getGameProfile().name().equals(this.targetName))
         .findFirst()
         .map(this::getInterpolatedEyePosition);
      return targetPosition.orElse(null);
   }

   public RotationLockModule() {
      super("RotationLock", "Locks your rotation.", ModuleCategory.PLAYER);
      this.modeSetting.setChangeCallback(this::selectTarget);
      Predicate<Object> plainMode = ignored -> {
         Setting<RotationLockModule.ModeChoice> selectedMode = this.modeSetting;
         return selectedMode.getValue() == RotationLockModule.ModeChoice.PLAIN;
      };
      this.yawSetting.addVisibilityPredicate(plainMode);
      this.customYawSetting.addVisibilityPredicate(plainMode);
      this.yawValueSetting.addVisibilityPredicate(plainMode);
      this.pitchSetting.addVisibilityPredicate(plainMode);
      this.pitchValueSetting.addVisibilityPredicate(plainMode);
      this.rangeSetting.addVisibilityPredicate(ignored -> {
         Setting<RotationLockModule.ModeChoice> selectedMode = this.modeSetting;
         return selectedMode.getValue() == RotationLockModule.ModeChoice.TARGET;
      });
      Setting<Float> minimumLabelSetting = this.disableRangeSetting;
      SettingLabelCondition minimumEndpoint = SettingLabelCondition.MIN;
      minimumLabelSetting.setBoundaryLabel("None", minimumEndpoint);
      this.yawValueSetting.setConfigName("YawValue");
      this.pitchValueSetting.setConfigName("PitchValue");
   }

   static {
      FreeLookModule var0 = ClientContext.getModule(FreeLookModule.class);
   }

   record LockedRotation(float yaw, float pitch, boolean yawLocked, boolean pitchLocked) {
      public LockedRotation(float yaw, float pitch) {
         this(yaw, pitch, true, true);
      }

      public LockedRotation() {
         this(0.0F, 0.0F, false, false);
      }

      public float getResolvedYaw() {
         if (!this.yawLocked) {
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            return player.getYRot();
         } else {
            return this.yaw;
         }
      }

      public float getResolvedPitch() {
         if (!this.pitchLocked) {
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            return player.getXRot();
         } else {
            return this.pitch;
         }
      }

      public boolean hasLockedAxis() {
         return this.yawLocked || this.pitchLocked;
      }
   }

   public enum ModeChoice implements NamedEntry {
      PLAIN("Plain"),
      TARGET("Target");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName) {
         this.displayName = displayName;
      }
   }

   class PlainRotationProvider {
      public RotationLockModule.LockedRotation getRotation() {
         return new RotationLockModule.LockedRotation(
            this.getYawForLock(),
            RotationLockModule.this.pitchValueSetting.getValue(),
            RotationLockModule.this.yawSetting.getValue(),
            RotationLockModule.this.pitchSetting.getValue()
         );
      }

      private float getYawForLock() {
         RotationLockModule yawOwner = RotationLockModule.this;
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         float currentYaw = player.getYRot();
         return yawOwner.calculateYaw(currentYaw);
      }
   }

   class TargetRotationProvider {
      public RotationLockModule.LockedRotation getRotation() {
         Vec3 targetPosition = RotationLockModule.this.findTargetEyePosition();
         if (targetPosition == null) {
            return new RotationLockModule.LockedRotation();
         }

         RotationLockModule eyePositionOwner = RotationLockModule.this;
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         Vec3 playerPosition = eyePositionOwner.getInterpolatedEyePosition(player);
         float[] angles = RotationManager.calculateRotations(playerPosition, targetPosition);
         return PlayerStateUtils.isFallFlying() ? this.avoidObstacles(angles) : new RotationLockModule.LockedRotation(angles[0], angles[1]);
      }

      private RotationLockModule.LockedRotation avoidObstacles(float[] angles) {
         float movementYaw = this.getMovementYaw();
         Optional<Float> clearPitch = this.findClearPitch(angles[0], angles[1]);
         return clearPitch.isPresent()
            ? new RotationLockModule.LockedRotation(angles[0], clearPitch.get())
            : new RotationLockModule.LockedRotation(this.findClearYaw(movementYaw, angles[0], angles[1]), angles[1]);
      }

      private float getMovementYaw() {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         Vec3 motion = MathUtils.getPositionDelta(player);
         return motion.horizontalDistance() < 0.05 ? player.getYRot() : (float)Math.toDegrees(Math.atan2(-motion.x, motion.z));
      }

      private Optional<Float> findClearPitch(float yaw, float pitch) {
         if (this.isDirectionClear(yaw, pitch)) {
            return Optional.of(pitch);
         }

         for (float offset = 15.0F; offset <= 75.0F; offset += 15.0F) {
            float candidate = Math.min(pitch + offset, 90.0F);
            if (this.isDirectionClear(yaw, candidate)) {
               return Optional.of(candidate);
            }
         }

         return Optional.empty();
      }

      private float findClearYaw(float movementYaw, float yaw, float pitch) {
         float difference = Mth.clamp(Mth.wrapDegrees(yaw - movementYaw), -50.0F, 50.0F);
         float candidate = Mth.wrapDegrees(movementYaw + difference);
         if (this.isDirectionClear(candidate, pitch)) {
            return candidate;
         }

         for (float offset = 5.0F; offset <= 50.0F; offset += 5.0F) {
            if (difference + offset <= 50.0F) {
               candidate = Mth.wrapDegrees(movementYaw + difference + offset);
               if (this.isDirectionClear(candidate, pitch)) {
                  return candidate;
               }
            }

            if (difference - offset >= -50.0F) {
               candidate = Mth.wrapDegrees(movementYaw - difference - offset);
               if (this.isDirectionClear(candidate, pitch)) {
                  return candidate;
               }
            }
         }

         return movementYaw;
      }

      private boolean isDirectionClear(float yaw, float pitch) {
         Vec3 eyes = ClientAccess.argusclientMinecraft.player.getEyePosition();
         Vec3 destination = eyes.add(Vec3.directionFromRotation(pitch, yaw).scale(6.0));
         return RaycastUtils.isLineVisible(eyes, destination);
      }
   }
}
