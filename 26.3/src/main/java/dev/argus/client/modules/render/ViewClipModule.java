package dev.argus.client.modules.render;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MouseHandlerOnScrollEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ClampedInterpolatedFloat;
import dev.argus.client.utils.FrameTiming;
import dev.argus.client.utils.KeyUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;

public class ViewClipModule extends Module {
   public final Setting<ViewClipModule.SkipChoice> skipSetting = this.add(new EnumSetting<>(new String("Skip"), ViewClipModule.SkipChoice.NONE));
   public final Setting<Float> rangeSetting = this.add(new NumberSetting<>(new String("Range"), 4.5F, 0.1F, 10.0F));
   public final Setting<Boolean> smoothSetting = this.add(new BooleanSetting(new String("Smooth"), true));
   public final Setting<Boolean> scrollSetting = this.add(new BooleanSetting(new String("Scroll"), false));
   public final ClampedInterpolatedFloat clipAnimation = new ClampedInterpolatedFloat(1.3F, true);
   private float scrollTargetOffset;
   private float scrollOffset;

   public float getCameraDistance() {
      float distance = this.rangeSetting.getValue() + this.scrollOffset;
      return Mth.clamp(distance, 0.1F, 10.0F);
   }

   @EventHandler
   private void onMouseHandlerOnScrollEvent(MouseHandlerOnScrollEvent event) {
      Boolean enabled = this.scrollSetting.getValue();
      if (enabled) {
         Camera camera = ClientAccess.argusclientMinecraft.gameRenderer.mainCamera();
         if (camera.isDetached()) {
            if (KeyUtils.isPhysicalKeyDown(341)) {
               float currentTarget = this.scrollTargetOffset;
               float nextTarget = currentTarget - (float)event.getVerticalOffset();
               Float minimumRangeValue = this.rangeSetting.getValue();
               float minimumOffset = -minimumRangeValue;
               float maximum = this.rangeSetting.getMaximumValue();
               Float currentRangeValue = this.rangeSetting.getValue();
               float maximumOffset = maximum - currentRangeValue;
               this.scrollTargetOffset = Mth.clamp(nextTarget, minimumOffset, maximumOffset);
               event.cancel();
            }
         }
      }
   }

   public boolean shouldSkipCameraType(CameraType cameraType) {
      if (!this.isToggled()) {
         return false;
      } else {
         Setting<ViewClipModule.SkipChoice> skip = this.skipSetting;
         if (skip.getValue() == ViewClipModule.SkipChoice.NONE) {
            return false;
         } else {
            return this.skipSetting.getValue() == ViewClipModule.SkipChoice.FRONT
               ? cameraType == CameraType.THIRD_PERSON_FRONT
               : cameraType == CameraType.THIRD_PERSON_BACK;
         }
      }
   }

   @EventHandler
   private void onRenderFrame(WorldRenderEvent.Main event) {
      FrameTiming timing = ClientContext.frameTiming;
      float frameFactor = timing.getFrameFactor(1.0F);
      float currentOffset = this.scrollOffset;
      float difference = this.scrollTargetOffset - this.scrollOffset;
      double exponent = -10.0F * frameFactor;
      this.scrollOffset = currentOffset + difference * (1.0F - (float)Math.exp(exponent));
      Camera camera = ClientAccess.argusclientMinecraft.gameRenderer.mainCamera();
      if (camera.isDetached()) {
         ClampedInterpolatedFloat animation = this.clipAnimation;
         animation.setTarget(true);
      } else {
         ClampedInterpolatedFloat animation = this.clipAnimation;
         animation.setValue(0.0F);
         this.scrollTargetOffset = this.scrollOffset = 0.0F;
      }
   }

   public ViewClipModule() {
      super("ViewClip", "Allows you to clip into blocks using 3rd person camera.", ModuleCategory.RENDER);
      this.setDrawn(false);
   }

   public enum SkipChoice implements NamedEntry {
      NONE("None"),
      FRONT("Front"),
      BACK("Back");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      SkipChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
