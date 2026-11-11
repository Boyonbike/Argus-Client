package dev.argus.client.modules.render;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.MinecraftRunTickEvent;
import dev.argus.client.events.MouseHandlerOnScrollEvent;
import dev.argus.client.events.ScreenChangeEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.player.FreecamModule;
import dev.argus.client.render.GameOptionsOverrides;
import dev.argus.client.render.RenderUtils;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.InterpolatedFloat;
import dev.argus.client.utils.movement.EntityMotionAccess;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.joml.Vector2f;

public class FreeLookModule extends Module {
   private static FreecamModule freecamModule = ClientContext.getModule(FreecamModule.class);
   private final InterpolatedFloat cameraDistanceAnimation = new InterpolatedFloat(2.0F, true);
   private final Vector2f cameraRotation = new Vector2f();
   private CameraType cameraType;
   private float cameraDistance;
   public final Setting<Boolean> scrollSetting;

   @Override
   public void onEnable() {
      if (this.isWorldUnavailable()) {
         this.disable();
      } else {
         this.cameraDistance = 100.0F;
         this.cameraDistanceAnimation.setValue(0.0F);
         Options options = ClientAccess.argusclientMinecraft.options;
         CameraType selectedCameraType;
         if (options.getCameraType() == CameraType.FIRST_PERSON) {
            selectedCameraType = CameraType.THIRD_PERSON_BACK;
         } else {
            selectedCameraType = ClientAccess.argusclientMinecraft.options.getCameraType();
         }

         this.cameraType = selectedCameraType;
         GameOptionsOverrides.registerCameraOverride(this);
         Vector2f rotation = this.cameraRotation;
         float yaw = ClientAccess.argusclientMinecraft.player.getYRot();
         float pitch = ClientAccess.argusclientMinecraft.player.getXRot();
         rotation.set(yaw, pitch);
         if (ClientAccess.argusclientMinecraft.player instanceof EntityMotionAccess cameraPlayer) {
            float cameraYaw = this.cameraRotation.x;
            cameraPlayer.setCameraYaw(cameraYaw);
            float cameraPitch = this.cameraRotation.y;
            cameraPlayer.setCameraPitch(cameraPitch);
         }
      }
   }

   @Override
   public void onDisable() {
      GameOptionsOverrides.unregisterCameraOverride(this);
      LocalPlayer yawPlayer = ClientAccess.argusclientMinecraft.player;
      Vector2f yawRotation = this.cameraRotation;
      float yaw = yawRotation.x();
      yawPlayer.setYRot(yaw);
      LocalPlayer previousYawBobPlayer = ClientAccess.argusclientMinecraft.player;
      LocalPlayer yawBobPlayer = ClientAccess.argusclientMinecraft.player;
      LocalPlayer previousYawPlayer = ClientAccess.argusclientMinecraft.player;
      Vector2f previousYawRotation = this.cameraRotation;
      previousYawBobPlayer.yBobO = yawBobPlayer.yBob = previousYawPlayer.yRotO = previousYawRotation.x();
      LocalPlayer pitchPlayer = ClientAccess.argusclientMinecraft.player;
      Vector2f pitchRotation = this.cameraRotation;
      float pitch = pitchRotation.y();
      pitchPlayer.setXRot(pitch);
      LocalPlayer previousPitchBobPlayer = ClientAccess.argusclientMinecraft.player;
      LocalPlayer pitchBobPlayer = ClientAccess.argusclientMinecraft.player;
      LocalPlayer previousPitchPlayer = ClientAccess.argusclientMinecraft.player;
      Vector2f previousPitchRotation = this.cameraRotation;
      previousPitchBobPlayer.xBobO = pitchBobPlayer.xBob = previousPitchPlayer.xRotO = previousPitchRotation.y();
      Camera camera = ClientAccess.argusclientMinecraft.gameRenderer.mainCamera();
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      LocalPlayer cameraPlayer = ClientAccess.argusclientMinecraft.player;
      camera.extractRenderState(new net.minecraft.client.renderer.state.level.CameraRenderState(), ClientAccess.argusclientMinecraft.getDeltaTracker());
   }

   public float getCameraDistanceOffset() {
      float offset = this.cameraDistanceAnimation.getValue();
      return offset / 10.0F;
   }

   public boolean isScrollEnabled() {
      return this.isToggled() && this.scrollSetting.getValue();
   }

   @EventHandler(priority = -200)
   private void onLocalPlayerPreTick(LocalPlayerPreTickEvent event) {
      FreecamModule freecam = freecamModule;
      if (!freecam.isToggled()) {
         Options options = ClientAccess.argusclientMinecraft.options;
         CameraType selectedCameraType = this.cameraType;
         options.setCameraType(selectedCameraType);
         Minecraft minecraft = ClientAccess.argusclientMinecraft;
         LocalPlayer pickPlayer = ClientAccess.argusclientMinecraft.player;
         double range = ClientAccess.argusclientMinecraft.player.blockInteractionRange();
         float partialTick = RenderUtils.getPartialTick();
         minecraft.hitResult = pickPlayer.pick(range, partialTick, false);
      }
   }

   @EventHandler
   private void onMouseHandlerOnScrollEvent(MouseHandlerOnScrollEvent event) {
      Boolean enabled = this.scrollSetting.getValue();
      if (enabled) {
         float previousDistance = this.cameraDistance;
         float scrollDelta = (float)event.getVerticalOffset() * this.cameraDistance;
         this.cameraDistance = previousDistance - scrollDelta * 0.08F;
         float distance = this.cameraDistance;
         this.cameraDistance = Mth.clamp(distance, 1.0F, 200.0F);
         event.cancel();
      }
   }

   @EventHandler
   private void onMinecraftSetScreenEvent(ScreenChangeEvent.Before event) {
      if (event.getNewScreen() instanceof LevelLoadingScreen) {
         this.disable();
      }
   }

   @EventHandler(priority = 200)
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      if (!this.isWorldUnavailable()) {
         InterpolatedFloat targetAnimation = this.cameraDistanceAnimation;
         float distance = this.cameraDistance;
         float target = distance - 100.0F;
         targetAnimation.setTarget(target);
         this.cameraDistanceAnimation.updateAndGet();
      }
   }

   public Vector2f getCameraRotation() {
      return this.cameraRotation;
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      freecamModule = (FreecamModule)registry.getModule(FreecamModule.class);
   }

   public FreeLookModule() {
      super("FreeLook", "Allows you to rotate your 3rd person camera without rotating your player.", ModuleCategory.RENDER);
      Boolean scrollDefault = false;
      String scrollName = new String("Scroll");
      BooleanSetting scroll = new BooleanSetting(scrollName, scrollDefault);
      this.scrollSetting = this.add(scroll);
   }
}
