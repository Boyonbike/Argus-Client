package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.CameraSetupEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.GameModeUseItemPacketEvent;
import dev.argus.client.events.KeyboardInputTickEvent;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftRunTickEvent;
import dev.argus.client.events.MouseHandlerOnScrollEvent;
import dev.argus.client.events.MouseHandlerTurnPlayerEvent;
import dev.argus.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.ScreenChangeEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.GameOptionsOverrides;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.InterpolatedFloat;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.client.utils.network.PacketSender;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class FreecamModule extends Module {
   private final FreecamInput freecamInput = new FreecamInput();
   private final Setting<Float> horizontalSpeedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 0.1F, 5.0F));
   private final Setting<Float> verticalSpeedSetting = this.add(new NumberSetting<>("VerticalSpeed", 0.5F, 0.1F, 5.0F));
   private final Setting<Boolean> scrollSpeedSetting = this.add(new BooleanSetting("Scroll", false));
   public final Setting<Boolean> noHandsSetting = this.add(new BooleanSetting("NoHands", true));
   private final Setting<Boolean> rotatePlayerSetting;
   private final Setting<Boolean> alwaysRotateSetting;
   private final Setting<Boolean> clampCameraYSetting;
   private final Setting<Float> minimumYSetting;
   private final Setting<Float> replacementYSetting;
   private final Setting<Float> maximumYSetting;
   public Vec3 cameraPosition;
   public Vec3 previousCameraPosition;
   private float[] savedPlayerRotation;
   public float cameraYaw;
   public float previousCameraYaw;
   public float cameraPitch;
   public float previousCameraPitch;
   private final InterpolatedFloat horizontalSpeedAnimation;
   private final InterpolatedFloat verticalSpeedAnimation;

   @Override
   public void onEnable() {
      InterpolatedFloat animation = this.horizontalSpeedAnimation;
      float targetSpeed = this.horizontalSpeedSetting.getValue();
      animation.setValue(targetSpeed);
      animation = this.verticalSpeedAnimation;
      targetSpeed = this.verticalSpeedSetting.getValue();
      animation.setValue(targetSpeed);
      if (this.isWorldUnavailable()) {
         this.disable();
      } else {
         GameOptionsOverrides.registerCameraOverride(this);
         KeyboardInputTickEvent resetInputEvent = new KeyboardInputTickEvent(ClientAccess.argusclientMinecraft.player.input);
         resetInputEvent.reset();
         GameRenderer gameRenderer = ClientAccess.argusclientMinecraft.gameRenderer;
         Camera camera = gameRenderer.getMainCamera();
         this.previousCameraPosition = this.cameraPosition = camera.position();
         float[] savedRotation = new float[2];
         LocalPlayer playerForYaw = ClientAccess.argusclientMinecraft.player;
         savedRotation[0] = playerForYaw.getYRot();
         LocalPlayer playerForPitch = ClientAccess.argusclientMinecraft.player;
         savedRotation[1] = playerForPitch.getXRot();
         this.savedPlayerRotation = savedRotation;
         this.previousCameraYaw = this.cameraYaw = this.savedPlayerRotation[0];
         this.previousCameraPitch = this.cameraPitch = this.savedPlayerRotation[1];
         float cameraY = (float)this.cameraPosition.y();
         float minimumY = this.minimumYSetting.getValue();
         float maximumY = this.maximumYSetting.getValue();
         if (maximumY < minimumY) {
            maximumY = minimumY;
            minimumY = this.maximumYSetting.getValue();
         }

         if (this.clampCameraYSetting.getValue() && (cameraY > maximumY || cameraY < minimumY)) {
            Vec3 positionBeforeClamp = this.cameraPosition;
            Axis yAxis = Axis.Y;
            double replacementY = this.replacementYSetting.getValue().floatValue();
            this.previousCameraPosition = this.cameraPosition = positionBeforeClamp.with(yAxis, replacementY);
         }
      }
   }

   @Override
   public void onDisable() {
      if (!this.isWorldUnavailable()) {
         LocalPlayer playerForYaw = ClientAccess.argusclientMinecraft.player;
         float savedYaw = this.savedPlayerRotation[0];
         playerForYaw.setYRot(savedYaw);
         LocalPlayer playerForPitch = ClientAccess.argusclientMinecraft.player;
         float savedPitch = this.savedPlayerRotation[1];
         playerForPitch.setXRot(savedPitch);
         GameOptionsOverrides.unregisterCameraOverride(this);
      }
   }

   public boolean shouldHideHands() {
      return !this.isToggled() ? false : this.noHandsSetting.getValue();
   }

   public void rotateCamera(double yawDelta, double pitchDelta) {
      this.previousCameraYaw = this.cameraYaw;
      this.previousCameraPitch = this.cameraPitch;
      this.cameraYaw = (float)(this.cameraYaw + yawDelta);
      this.cameraPitch = (float)(this.cameraPitch + pitchDelta);
      float requestedPitch = this.cameraPitch;
      float minimumPitch = -90.0F;
      float maximumPitch = 90.0F;
      this.cameraPitch = Mth.clamp(requestedPitch, minimumPitch, maximumPitch);
   }

   public double getInterpolatedX(float partialTick) {
      double capturedPartialTick = partialTick;
      double previousCoordinate = this.previousCameraPosition.x;
      double currentCoordinate = this.cameraPosition.x;
      return Mth.lerp(capturedPartialTick, previousCoordinate, currentCoordinate);
   }

   @EventHandler
   private void onMultiPlayerGameModeUseItemOnEvent(MultiPlayerGameModeUseItemOnEvent event) {
      LocalPlayer capturedPlayer = ClientAccess.argusclientMinecraft.player;
      InteractionHand hand = event.getHand();
      boolean holdingBucket = capturedPlayer.getItemInHand(hand).getItem() instanceof BucketItem;
      if (!holdingBucket) {
         Boolean rotateEnabled = this.rotatePlayerSetting.getValue();
         if (!rotateEnabled) {
            return;
         }

         if (!RotationManager.isMovementSyncEnabled()) {
            return;
         }
      }

      Vec3 capturedHitPosition = event.getHitResult().getLocation();
      float[] angles = RotationManager.getRotationsToPosition(capturedHitPosition);
      if (RotationManager.isSilentRotationMode()) {
         double capturedX = ClientAccess.argusclientMinecraft.player.getX();
         double capturedY = ClientAccess.argusclientMinecraft.player.getY();
         double capturedZ = ClientAccess.argusclientMinecraft.player.getZ();
         float capturedYaw = angles[0];
         float capturedPitch = angles[1];
         boolean rotationFlag = ClientContext.playerStateTracker.isPacketOnGround();
         PacketSender.sendPositionRotation(capturedX, capturedY, capturedZ, capturedYaw, capturedPitch, rotationFlag);
      } else {
         ClientContext.rotationManager.requestRotation(angles, 1000);
      }
   }

   @EventHandler
   private void onUseItemPacket(GameModeUseItemPacketEvent event) {
      Boolean rotateEnabled = this.rotatePlayerSetting.getValue();
      if (rotateEnabled && ClientAccess.argusclientMinecraft.hitResult != null) {
         if (!this.alwaysRotateSetting.getValue()) {
            HitResult hitResult = ClientAccess.argusclientMinecraft.hitResult;
            Vec3 capturedHitPosition = hitResult.getLocation();
            float[] angles = RotationManager.getRotationsToPosition(capturedHitPosition);
            event.setRotations(angles);
         }
      }
   }

   @EventHandler
   private void onKeyboardInputPre(KeyboardInputTickEvent.Pre event) {
      event.cancel();
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      Options options = ClientAccess.argusclientMinecraft.options;
      CameraType cameraType = CameraType.FIRST_PERSON;
      options.setCameraType(cameraType);
      this.previousCameraPosition = this.cameraPosition;
      float capturedYaw = this.cameraYaw;
      FreecamInput capturedInput = this.freecamInput;
      double horizontalSpeed = this.horizontalSpeedAnimation.getValue();
      double[] movement = MovementPhysics.getHorizontalMotion(capturedYaw, capturedInput, horizontalSpeed);
      Vec3 position = this.cameraPosition;
      double capturedDeltaX = movement[0];
      double deltaZ = movement[1];
      this.cameraPosition = position.add(capturedDeltaX, 0.0, deltaZ);
      if (ClientAccess.argusclientMinecraft.options.keyJump.isDown()) {
         Vec3 positionBeforeJump = this.cameraPosition;
         double capturedUpSpeed = this.verticalSpeedAnimation.getValue();
         this.cameraPosition = positionBeforeJump.add(0.0, capturedUpSpeed, 0.0);
      }

      if (ClientAccess.argusclientMinecraft.options.keyShift.isDown()) {
         Vec3 positionBeforeSneak = this.cameraPosition;
         double capturedDownSpeed = this.verticalSpeedAnimation.getValue();
         this.cameraPosition = positionBeforeSneak.subtract(0.0, capturedDownSpeed, 0.0);
      }

      this.freecamInput.tick();
   }

   @EventHandler
   private void onMouseHandlerOnScrollEvent(MouseHandlerOnScrollEvent event) {
      if (this.scrollSpeedSetting.getValue()) {
         InterpolatedFloat animation = this.horizontalSpeedAnimation;
         float scrollDelta = (float)event.getVerticalOffset();
         Setting<Float> speedSettingSnapshot = this.horizontalSpeedSetting;
         float maximumHorizontalSpeed = speedSettingSnapshot.getMaximumValue();
         this.adjustScrollSpeed(animation, scrollDelta, maximumHorizontalSpeed);
         animation = this.verticalSpeedAnimation;
         scrollDelta = (float)event.getVerticalOffset();
         speedSettingSnapshot = this.verticalSpeedSetting;
         float maximumVerticalSpeed = speedSettingSnapshot.getMaximumValue();
         this.adjustScrollSpeed(animation, scrollDelta, maximumVerticalSpeed);
         event.cancel();
      }
   }

   @EventHandler
   private void onMinecraftSetScreenEvent(ScreenChangeEvent.Before event) {
      if (event.getNewScreen() instanceof LevelLoadingScreen) {
         this.disable();
      }
   }

   private void adjustScrollSpeed(InterpolatedFloat animation, float scrollDelta, float maximumSpeed) {
      float currentSpeed = animation.getValue();
      float speedIncrement = Math.signum(scrollDelta) * 0.1F * currentSpeed;
      float requestedSpeed = currentSpeed + speedIncrement;
      float clampedSpeed = Mth.clamp(requestedSpeed, 0.0F, maximumSpeed);
      animation.setTarget(clampedSpeed);
   }

   @EventHandler
   private void onCameraSetupEvent(CameraSetupEvent event) {
      float partialTick = ClientAccess.argusclientMinecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
      Vec3 position = new Vec3(this.getInterpolatedX(partialTick), this.getInterpolatedY(partialTick), this.getInterpolatedZ(partialTick));
      event.setPosition(position);
      event.cancel();
   }

   @EventHandler
   private void onMouseHandlerTurnPlayerEvent(MouseHandlerTurnPlayerEvent event) {
      double yawDelta = event.getYawDelta();
      yawDelta *= 0.15F;
      double pitchDelta = event.getPitchDelta();
      pitchDelta *= 0.15F;
      this.rotateCamera(yawDelta, pitchDelta);
      event.cancel();
   }

   public double getInterpolatedZ(float partialTick) {
      double capturedPartialTick = partialTick;
      double previousCoordinate = this.previousCameraPosition.z;
      double currentCoordinate = this.cameraPosition.z;
      return Mth.lerp(capturedPartialTick, previousCoordinate, currentCoordinate);
   }

   public double getInterpolatedY(float partialTick) {
      double capturedPartialTick = partialTick;
      double previousCoordinate = this.previousCameraPosition.y;
      double currentCoordinate = this.cameraPosition.y;
      return Mth.lerp(capturedPartialTick, previousCoordinate, currentCoordinate);
   }

   public double getInterpolatedYaw(float partialTick) {
      if (ClientAccess.argusclientMinecraft.screen != null) {
         return this.cameraYaw;
      }

      float interpolationAmount = partialTick;
      float previousYaw = this.previousCameraYaw;
      float currentYaw = this.cameraYaw;
      return Mth.lerp(interpolationAmount, previousYaw, currentYaw);
   }

   public double getInterpolatedPitch(float partialTick) {
      if (ClientAccess.argusclientMinecraft.screen != null) {
         return this.cameraPitch;
      }

      float interpolationAmount = partialTick;
      float previousPitch = this.previousCameraPitch;
      float currentPitch = this.cameraPitch;
      return Mth.lerp(interpolationAmount, previousPitch, currentPitch);
   }

   @EventHandler
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      this.horizontalSpeedAnimation.updateAndGet();
      this.verticalSpeedAnimation.updateAndGet();
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundDisconnectPacket) {
         this.disable();
      }
   }

   @EventHandler(priority = 1000)
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
      boolean destroyingBlock = gameMode.isDestroying();
      if (event.getStage() != EventStage.Post) {
         Boolean rotateEnabled = this.rotatePlayerSetting.getValue();
         if (rotateEnabled && ClientAccess.argusclientMinecraft.hitResult != null) {
            if (RotationManager.isMovementSyncEnabled() && !destroyingBlock) {
               Boolean alwaysRotate = this.alwaysRotateSetting.getValue();
               if (!alwaysRotate) {
                  return;
               }
            }

            HitResult hitResult = ClientAccess.argusclientMinecraft.hitResult;
            Vec3 capturedHitPosition = hitResult.getLocation();
            float[] angles = RotationManager.getRotationsToPosition(capturedHitPosition);
            event.setRotation(angles);
         }
      }
   }

   public FreecamModule() {
      super("Freecam", "Allows your camera to fly through walls.", ModuleCategory.PLAYER);
      BooleanSetting rotate = new BooleanSetting("Rotate", false);
      rotate.markVisibilityDependency();
      this.rotatePlayerSetting = this.add(rotate);
      this.alwaysRotateSetting = this.add(new BooleanSetting("Always", false).dependsOn(this.rotatePlayerSetting));
      BooleanSetting clampY = new BooleanSetting("ClampY", false);
      clampY.markVisibilityDependency();
      this.clampCameraYSetting = this.add(clampY);
      this.minimumYSetting = this.add(new NumberSetting<>("Min", -64.0F, -64.0F, 320.0F).dependsOn(this.clampCameraYSetting));
      this.replacementYSetting = this.add(new NumberSetting<>("Value", 64.0F, -64.0F, 320.0F).dependsOn(this.clampCameraYSetting));
      this.maximumYSetting = this.add(new NumberSetting<>("Max", 300.0F, -64.0F, 320.0F).dependsOn(this.clampCameraYSetting));
      this.savedPlayerRotation = null;
      this.horizontalSpeedAnimation = new InterpolatedFloat(2.0F, true);
      this.verticalSpeedAnimation = new InterpolatedFloat(2.0F, true);
      Setting<Float> horizontalCallbackSetting = this.horizontalSpeedSetting;
      Runnable horizontalCallback = () -> {
         InterpolatedFloat animationToUpdate = this.horizontalSpeedAnimation;
         Setting<Float> changedSetting = this.horizontalSpeedSetting;
         Float changedValue = changedSetting.getValue();
         float newSpeed = changedValue;
         animationToUpdate.setValue(newSpeed);
      };
      horizontalCallbackSetting.setChangeCallback(horizontalCallback);
      Setting<Float> verticalCallbackSetting = this.verticalSpeedSetting;
      Runnable verticalCallback = () -> {
         InterpolatedFloat animationToUpdate = this.verticalSpeedAnimation;
         Setting<Float> changedSetting = this.verticalSpeedSetting;
         Float changedValue = changedSetting.getValue();
         float newSpeed = changedValue;
         animationToUpdate.setValue(newSpeed);
      };
      verticalCallbackSetting.setChangeCallback(verticalCallback);
      Setting<Float> settingToRefresh = this.horizontalSpeedSetting;
      settingToRefresh.disableNumericBounds();
      settingToRefresh = this.verticalSpeedSetting;
      settingToRefresh.disableNumericBounds();
   }
}
