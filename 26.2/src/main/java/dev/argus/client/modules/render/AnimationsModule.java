package dev.argus.client.modules.render;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.CameraSetupEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.math.Vec3Utils;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.Vec3;

public class AnimationsModule extends Module {
   private final Setting<Boolean> smoothCameraSetting = this.add(new BooleanSetting("SmoothCamera", false));
   public final Setting<Boolean> crystalsSetting = this.add(new BooleanSetting("Crystals", false).markVisibilityDependency());
   public final Setting<Float> floatFactorSetting = this.add(new NumberSetting<>("FloatFactor", 1.0F, 0.0F, 1.0F).dependsOn(this.crystalsSetting));
   public final Setting<Float> rotationSpeedSetting = this.add(new NumberSetting<>("RotationSpeed", 1.0F, 0.0F, 6.0F).dependsOn(this.crystalsSetting));
   public final Setting<Float> crystalScaleSetting = this.add(new NumberSetting<>("CrystalScale", 1.0F, 0.0F, 2.0F).dependsOn(this.crystalsSetting));
   private final Setting<Boolean> partsSetting = this.add(
      new BooleanSetting("Parts", false).markAsGroup().markVisibilityDependency().dependsOn(this.crystalsSetting)
   );
   private final Setting<Boolean> innerSetting = this.add(new BooleanSetting("Inner", true).dependsOn(this.crystalsSetting, this.partsSetting));
   private final Setting<Boolean> outerSetting = this.add(new BooleanSetting("Outer", true).dependsOn(this.crystalsSetting, this.partsSetting));
   private final Setting<Boolean> coreSetting = this.add(new BooleanSetting("Core", true).dependsOn(this.crystalsSetting, this.partsSetting));
   private final Setting<Boolean> bottomSetting = this.add(new BooleanSetting("Bottom", true).dependsOn(this.crystalsSetting, this.partsSetting));
   public final Setting<Boolean> playersSetting = this.add(new BooleanSetting("Players", false).markVisibilityDependency());
   public final Setting<Boolean> staticSetting = this.add(new BooleanSetting("Static", false).dependsOn(this.playersSetting));
   public final Setting<Boolean> sneakSetting = this.add(new BooleanSetting("Sneak", false).dependsOn(this.playersSetting));
   public final Setting<Float> playerScaleSetting = this.add(new NumberSetting<>("PlayerScale", 1.0F, 0.0F, 2.0F).dependsOn(this.playersSetting));
   private boolean verticalCameraCatchUp;
   private boolean horizontalCameraCatchUp;
   private Vec3 cameraPosition;
   private Vec3 previousCameraPosition;

   public boolean shouldHideCrystalPart(int partIndex) {
      if (!this.isToggled()) {
         return false;
      }

      return switch (partIndex) {
         case 0 -> this.shouldHideCrystalBottom();
         case 1 -> this.shouldHideCrystalOuter();
         case 2 -> this.shouldHideCrystalInner();
         case 3 -> this.shouldHideCrystalCore();
         default -> false;
      };
   }

   public boolean isPlayerScalingEnabled() {
      if (this.isToggled()) {
         Boolean playersEnabled = this.playersSetting.getValue();
         if (playersEnabled) {
            float scale = this.playerScaleSetting.getValue();
            if (scale != 1.0F) {
               return true;
            }
         }
      }

      return false;
   }

   public void resetCrystalModelVisibility() {
      EndCrystal crystal = new EndCrystal(EntityTypes.END_CRYSTAL, ClientAccess.argusclientMinecraft.level);
      EntityRenderDispatcher dispatcherSnapshot = ClientAccess.argusclientMinecraft.getEntityRenderDispatcher();
      EndCrystalRenderer renderer = (EndCrystalRenderer)dispatcherSnapshot.<EndCrystal>getRenderer(crystal);
      renderer.model.cube.skipDraw = false;
      renderer.model.innerGlass.skipDraw = false;
      renderer.model.outerGlass.skipDraw = false;
   }

   public boolean shouldHideCrystalBottom() {
      return this.isCrystalAnimationEnabled() && !this.bottomSetting.getValue();
   }

   public boolean shouldHideCrystalOuter() {
      return this.isCrystalAnimationEnabled() && !this.outerSetting.getValue();
   }

   public boolean isStaticPlayerAnimationEnabled() {
      return this.isToggled() && this.playersSetting.getValue() && this.staticSetting.getValue();
   }

   public boolean isSneakingAnimationEnabled() {
      return this.isToggled() && this.playersSetting.getValue() && this.sneakSetting.getValue();
   }

   public boolean shouldHideCrystalCore() {
      return this.isCrystalAnimationEnabled() && !this.coreSetting.getValue();
   }

   public boolean isCrystalAnimationEnabled() {
      return this.isToggled() && this.crystalsSetting.getValue();
   }

   public boolean shouldHideCrystalInner() {
      return this.isCrystalAnimationEnabled() && !this.innerSetting.getValue();
   }

   @Override
   public void onDisable() {
      this.resetCrystalModelVisibility();
   }

   private boolean updateCameraCatchUp(boolean previousCatchUp, double distance, double catchUpThreshold) {
      if (!(distance > catchUpThreshold) && !PlayerStateUtils.isFallFlying()) {
         return distance < 0.1 ? false : previousCatchUp;
      } else {
         return true;
      }
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Vec3 playerPosition = player.position();
      Boolean smoothCameraEnabled = this.smoothCameraSetting.getValue();
      if (smoothCameraEnabled && this.cameraPosition != null && this.previousCameraPosition != null) {
         this.previousCameraPosition = this.cameraPosition;
         Vec3 cameraPositionSnapshot = this.cameraPosition;
         double horizontalDistance = Vec3Utils.horizontalDistance(cameraPositionSnapshot, playerPosition);
         double verticalDelta = playerPosition.y - this.cameraPosition.y;
         double verticalDistance = Math.abs(verticalDelta);
         boolean previousHorizontalCatchUp = this.horizontalCameraCatchUp;
         this.horizontalCameraCatchUp = this.updateCameraCatchUp(previousHorizontalCatchUp, horizontalDistance, 3.0);
         boolean previousVerticalCatchUp = this.verticalCameraCatchUp;
         this.verticalCameraCatchUp = this.updateCameraCatchUp(previousVerticalCatchUp, verticalDistance, 3.5);
         double horizontalFraction = Math.min(1.0, horizontalDistance) / horizontalDistance;
         if (Double.isNaN(horizontalFraction) || horizontalDistance > 64.0) {
            horizontalFraction = 1.0;
         } else if (this.horizontalCameraCatchUp) {
            horizontalFraction = Math.min(5.0, horizontalDistance) / horizontalDistance;
         }

         double verticalFraction = Math.min(0.7, verticalDistance) / verticalDistance;
         if (Double.isNaN(verticalFraction) || this.verticalCameraCatchUp) {
            verticalFraction = 1.0;
         }

         this.cameraPosition = new Vec3(
            Mth.lerp(horizontalFraction, this.cameraPosition.x, playerPosition.x),
            Mth.lerp(verticalFraction, this.cameraPosition.y, playerPosition.y),
            Mth.lerp(horizontalFraction, this.cameraPosition.z, playerPosition.z)
         );
      } else {
         this.previousCameraPosition = this.cameraPosition = playerPosition;
      }
   }

   @EventHandler(priority = 100)
   private void onCameraSetupEvent(CameraSetupEvent event) {
      if (this.smoothCameraSetting.getValue()) {
         Options options = ClientAccess.argusclientMinecraft.options;
         CameraType cameraType = options.getCameraType();
         if (cameraType.isFirstPerson() && this.cameraPosition != null && this.previousCameraPosition != null) {
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            if (!player.getAbilities().flying) {
               float partialTick = event.getPartialTick();
               Vec3 currentPosition = this.cameraPosition;
               Vec3 interpolatedPosition = this.previousCameraPosition.lerp(currentPosition, partialTick);
               GameRenderer gameRenderer = ClientAccess.argusclientMinecraft.gameRenderer;
               Camera camera = gameRenderer.mainCamera();
               float currentEyeHeight = camera.eyeHeight;
               float previousEyeHeight = camera.eyeHeightOld;
               float eyeHeight = Mth.lerp(partialTick, previousEyeHeight, currentEyeHeight);
               Vec3 cameraPosition = interpolatedPosition.add(0.0, eyeHeight, 0.0);
               event.setPosition(cameraPosition);
               event.cancel();
            }
         }
      }
   }

   public AnimationsModule() {
      super("Animations", "Modifies entity animations.", ModuleCategory.RENDER);
      Setting<Boolean> callbackSetting = this.crystalsSetting;
      Runnable callback = this::resetCrystalModelVisibility;
      callbackSetting.setChangeCallback(callback);
   }
}
