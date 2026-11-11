package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.combat.AutoArmorModule;
import dev.argus.client.modules.player.RotationLockModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.math.Vec3Utils;
import dev.argus.client.utils.world.WorldUtils;
import dev.argus.mixin.BlockBehaviourAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ObstaclePasserModule extends Module {
   private static RotationLockModule rotationLock;
   private static AutoArmorModule autoArmor;
   private static ElytraFlyModule elytraFly;
   private final ObstacleCorrectionWindow correctionWindow = new ObstacleCorrectionWindow(3);
   public final Setting<Boolean> alignSetting = this.add(new BooleanSetting("Align", true));
   private final Setting<Boolean> onlyLoadedSetting = this.add(new BooleanSetting("OnlyLoaded", true));
   public final Setting<Integer> ySetting = this.add(new NumberSetting<>("Y", -1, -1, 128));
   public final Setting<Integer> maxTravelSetting = this.add(new NumberSetting<>("MaxTravel", 12, 6, 15));
   private final ElapsedTimer loadedChunksTimer = new ElapsedTimer();
   private float travelYaw;
   private BlockPos anchorPosition;
   private boolean pathWasActive;
   private boolean elytraPassActive;
   private int passCooldownTicks;
   private ObstacleAlignmentDirection alignmentDirection;

   private boolean hasNearbyCollision() {
      AABB bounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
      double horizontalInflation = BoundingBoxUtils.blockCornerInset;
      bounds = bounds.inflate(horizontalInflation, 0.0, horizontalInflation);
      return !ClientAccess.argusclientMinecraft.level.noBlockCollision(ClientAccess.argusclientMinecraft.player, bounds);
   }

   private boolean shouldAdjustBounceYaw() {
      int integerYaw = (int)this.travelYaw;
      if (integerYaw % 90 != 0) {
         return false;
      } else {
         return elytraFly.isToggled() && elytraFly.modeSetting.getValue() == ElytraFlyModule.ModeChoice.BOUNCE ? elytraFly.packetSetting.getValue() : false;
      }
   }

   private float findYawAdjustment() {
      int closestOffset = 1000;
      float yawAdjustment = 0.0F;
      float yawSteps = this.travelYaw / 90.0F;
      int roundedYaw = Math.round(yawSteps) * 90;
      float scanYaw = roundedYaw + 90;
      Vec3 direction = Vec3.directionFromRotation(0.0F, scanYaw);
      BlockPos playerPosition = ClientAccess.argusclientMinecraft.player.blockPosition();
      double directionX = direction.x;
      double offsetX = Math.ceil(directionX);
      double directionZ = direction.z;
      double offsetZ = Math.ceil(directionZ);
      BlockPos blockDirection = BlockPos.containing(offsetX, 0.0, offsetZ);

      for (int scanOffset = -6; scanOffset <= 6; scanOffset++) {
         int absoluteOffset = Math.abs(scanOffset);
         BlockPos scaledDirection = blockDirection.multiply(scanOffset);
         BlockPos positionToCheck = playerPosition.offset(scaledDirection);
         if (WorldUtils.isPlacementOccupied(positionToCheck) && absoluteOffset < closestOffset) {
            closestOffset = absoluteOffset;
            float adjustment;
            if (scanOffset > 0) {
               adjustment = 3.0F;
            } else {
               adjustment = -3.0F;
            }

            yawAdjustment = adjustment;
         }
      }

      return yawAdjustment;
   }

   private void captureTravelYaw() {
      float playerYaw = ClientAccess.argusclientMinecraft.player.getYRot();
      float yawSteps = (playerYaw + 1.0F) / 45.0F;
      this.travelYaw = Math.round(yawSteps) * 45;
   }

   private boolean canUseLoadedChunks() {
      ClientChunkCache chunkSource = ClientAccess.argusclientMinecraft.level.getChunkSource();
      ChunkPos capturedChunk = ClientAccess.argusclientMinecraft.player.chunkPosition();
      return ChunkPos.rangeClosed(capturedChunk, 2).allMatch(chunk -> {
         int chunkZ = chunk.z();
         int chunkX = chunk.x();
         return chunkSource.hasChunk(chunkX, chunkZ);
      }) || !this.onlyLoadedSetting.getValue();
   }

   public boolean isElytraPassActive() {
      return this.elytraPassActive && elytraFly.isToggled();
   }

   public boolean isPathActive() {
      return ClientContext.baritoneBridge.isPathingOrCalculating();
   }

   private void updateObstacleTarget() {
      if (ClientContext.baritoneBridge.isPathingOrCalculating()) {
         BlockPos activeTarget = ClientContext.baritoneBridge.getGoalPosition();
         if (activeTarget != null) {
            Vec3 targetCenter = Vec3.atBottomCenterOf(activeTarget);
            Vec3 yawOrigin = ClientAccess.argusclientMinecraft.player.position();
            float targetYaw = RotationManager.calculateRotations(yawOrigin, targetCenter)[0];
            float currentTravelYaw = this.getTravelYaw();
            float yawDifference = Mth.degreesDifferenceAbs(targetYaw, currentTravelYaw);
            if (yawDifference > 100.0F) {
               ClientContext.baritoneBridge.cancelPathing();
               return;
            }

            Vec3 rangeOrigin = ClientAccess.argusclientMinecraft.player.position();
            float targetDistance = Vec3Utils.horizontalDistance(rangeOrigin, targetCenter);
            int maximumTravel = this.maxTravelSetting.getValue();
            if (targetDistance > maximumTravel + 2) {
               ClientContext.baritoneBridge.cancelPathing();
               return;
            }

            Vec3 arrivalOrigin = ClientAccess.argusclientMinecraft.player.position();
            float arrivalDistance = Vec3Utils.horizontalDistance(arrivalOrigin, targetCenter);
            if (arrivalDistance <= 1.0F) {
               ClientContext.baritoneBridge.cancelPathing();
               this.passCooldownTicks = 10;
               if (elytraFly.isToggled()) {
                  this.elytraPassActive = true;
               }
            }
         }
      }

      if (this.shouldStartPass() && !ClientContext.baritoneBridge.isPathingOrCalculating()) {
         this.correctionWindow.clear();
         Integer configuredHeight = this.ySetting.getValue();
         int travelHeight;
         if (configuredHeight == -1) {
            travelHeight = this.anchorPosition.getY();
         } else {
            Integer customHeight = this.ySetting.getValue();
            travelHeight = customHeight;
         }

         if (this.alignmentDirection != null && this.alignSetting.getValue()) {
            BlockPos alignedTarget = null;
            int alignedSearchOffset = 5;

            while (true) {
               Integer alignedTravelLimit = this.maxTravelSetting.getValue();
               if (alignedSearchOffset >= alignedTravelLimit) {
                  break;
               }

               BlockPos offsetPosition = this.alignmentDirection.positionAtOffset(alignedSearchOffset);
               alignedTarget = offsetPosition.atY(travelHeight);
               BlockPos groundBelow = alignedTarget.below();
               if (((BlockBehaviourAccessor)WorldUtils.getBlock(groundBelow)).isCollidable()) {
                  break;
               }

               alignedSearchOffset++;
            }

            ClientContext.baritoneBridge.pathTo(alignedTarget);
            return;
         }

         Vec3 anchorCenter = Vec3.atBottomCenterOf(this.anchorPosition);
         float projectionYaw = this.getTravelYaw();
         Vec3 horizontalDirection = this.createHorizontalDirection(projectionYaw);
         Vec3 playerPosition = ClientAccess.argusclientMinecraft.player.position();
         double anchorDistance = this.horizontalDistance(anchorCenter, playerPosition);
         Vec3 projectionOffset = horizontalDirection.scale(anchorDistance);
         BlockPos projectedPosition = BlockPos.containing(anchorCenter.add(projectionOffset));
         Vec3 projectedDistanceTarget = new Vec3(projectedPosition);
         double projectedDistance = this.horizontalDistance(playerPosition, projectedDistanceTarget);
         if (projectedDistance > 32.0) {
            this.anchorPosition = ClientAccess.argusclientMinecraft.player.blockPosition();
            this.captureTravelYaw();
            return;
         }

         BlockPos searchPosition = null;
         int searchOffset = 5;

         while (true) {
            Integer freeTravelLimit = this.maxTravelSetting.getValue();
            if (searchOffset >= freeTravelLimit) {
               break;
            }

            double freeOffset = searchOffset;
            BlockPos freePositionOffset = BlockPos.containing(horizontalDirection.scale(freeOffset));
            searchPosition = projectedPosition.offset(freePositionOffset);
            BlockPos freeGroundBelow = searchPosition.below();
            if (((BlockBehaviourAccessor)WorldUtils.getBlock(freeGroundBelow)).isCollidable()) {
               break;
            }

            searchOffset++;
         }

         ClientContext.baritoneBridge.pathTo(searchPosition.atY(travelHeight));
      }
   }

   private boolean shouldStartPass() {
      if (this.passCooldownTicks > 0) {
         return false;
      }

      boolean startPass = ClientAccess.argusclientMinecraft.player.horizontalCollision;
      if (this.shouldAdjustBounceYaw()) {
         double trackedMotion = ClientContext.movementSpeedTracker.getDisplaySpeed();
         if (trackedMotion > 15.0) {
            startPass = false;
         }
      }

      if (!startPass && !this.correctionWindow.exceedsLimit()) {
         if (!PlayerStateUtils.isFallFlying()) {
            int movementState = ClientContext.playerStateTracker.getGroundedTicks();
            if (movementState > 3 && ClientAccess.argusclientMinecraft.player.onGround() && elytraFly.isToggled()) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private void updatePathTransition() {
      if (ClientContext.baritoneBridge.isPathingOrCalculating()) {
         if (!this.pathWasActive) {
            this.pathWasActive = true;
            ClientContext.playerStateTracker.setSneaking(true);
            ClientContext.playerStateTracker.setSneaking(false);
         }
      } else if (this.pathWasActive) {
         this.pathWasActive = false;
      }
   }

   @Override
   public void onEnable() {
      if (this.isWorldUnavailable()) {
         this.toggle();
      } else {
         this.pathWasActive = false;
         this.alignmentDirection = null;
         this.passCooldownTicks = 0;
         this.anchorPosition = ClientAccess.argusclientMinecraft.player.blockPosition();
         this.correctionWindow.clear();
         this.captureTravelYaw();
      }
   }

   @Override
   public void onDisable() {
      ClientContext.baritoneBridge.cancelPathing();
      if (this.pathWasActive && autoArmor.isToggled()) {
         autoArmor.elytraSetting.setValue(true);
      }
   }

   public boolean isPassingObstacles() {
      return this.isToggled() && this.isPathActive();
   }

   private float getTravelYaw() {
      if (rotationLock.isToggled() && rotationLock.yawSetting.getValue()) {
         Boolean customYaw = rotationLock.customYawSetting.getValue();
         float yaw;
         if (customYaw) {
            Float yawValue = rotationLock.yawValueSetting.getValue();
            yaw = yawValue;
         } else {
            RotationLockModule yawLock = rotationLock;
            float baseYaw = this.travelYaw;
            yaw = yawLock.calculateYaw(baseYaw);
         }

         return yaw;
      } else {
         return this.travelYaw;
      }
   }

   @EventHandler(priority = -100)
   private void updatePassState(LocalPlayerPreTickEvent event) {
      if (this.canUseLoadedChunks()) {
         this.loadedChunksTimer.reset();
      }

      this.updatePathTransition();
      if (this.alignmentDirection == null) {
         float yaw = this.getTravelYaw();
         this.alignmentDirection = ObstacleAlignmentDirection.fromYaw(yaw);
      }

      if (this.passCooldownTicks > 0) {
         this.passCooldownTicks--;
      }

      this.correctionWindow.pruneExpired();
      this.updateObstacleTarget();
      if (!this.onlyLoadedSetting.getValue() || !this.loadedChunksTimer.hasElapsed(750L)) {
         if (!this.isPathActive()) {
            if (this.isElytraPassActive() || !ClientAccess.argusclientMinecraft.player.isFallFlying() && elytraFly.isToggled()) {
               ClientContext.inputOverrides.requestJump(true);
            }

            ClientContext.inputOverrides.requestMovement(1.0F, 0.0F);
         }
      }
   }

   @EventHandler
   private void applyTravelYaw(LocalPlayerTickEvent event) {
      if (!ClientContext.baritoneBridge.isPathingOrCalculating()) {
         float yaw = this.getTravelYaw();
         if (this.shouldAdjustBounceYaw() && !this.hasNearbyCollision()) {
            yaw += this.findYawAdjustment();
         }

         ClientAccess.argusclientMinecraft.player.setYRot(yaw);
      }
   }

   private Vec3 createHorizontalDirection(float yaw) {
      Vec3 direction = ClientAccess.argusclientMinecraft.player.calculateViewVector(0.0F, yaw);
      double directionX = direction.x;
      double absoluteX = Math.abs(directionX);
      if (absoluteX < 0.01) {
         double preservedYForX = direction.y;
         double preservedZ = direction.z;
         Vec3Utils.setComponents(direction, 0.0, preservedYForX, preservedZ);
      }

      double directionZ = direction.z;
      double absoluteZ = Math.abs(directionZ);
      if (absoluteZ < 0.01) {
         double preservedX = direction.x;
         double preservedYForZ = direction.y;
         Vec3Utils.setComponents(direction, preservedX, preservedYForZ, 0.0);
      }

      double horizontalX = direction.x;
      double horizontalZ = direction.z;
      Vec3Utils.setComponents(direction, horizontalX, 0.0, horizontalZ);
      return direction;
   }

   private double horizontalDistance(Vec3 first, Vec3 second) {
      Vec3 firstFlat = first.multiply(1.0, 0.0, 1.0);
      Vec3 secondFlat = second.multiply(1.0, 0.0, 1.0);
      return firstFlat.distanceTo(secondFlat);
   }

   @EventHandler
   private void onPositionCorrection(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         this.correctionWindow.recordCorrection();
      }
   }

   public static void bindModules(ModuleRegistry modules) {
      rotationLock = (RotationLockModule)modules.getModule(RotationLockModule.class);
      autoArmor = (AutoArmorModule)modules.getModule(AutoArmorModule.class);
      elytraFly = (ElytraFlyModule)modules.getModule(ElytraFlyModule.class);
   }

   private static String createDescription() {
      String warningColor = String.valueOf(ChatFormatting.YELLOW);
      return "Passes obstacles in your way. \n" + warningColor + "Requires either ElytraFly or Speed enabled.";
   }

   public ObstaclePasserModule() {
      super("ObstaclePasser", createDescription(), ModuleCategory.MOVEMENT);
      this.ySetting.setBoundaryLabel("~", SettingLabelCondition.MIN);
      this.alignSetting.setChangeCallback(() -> this.alignmentDirection = null);
   }
}
