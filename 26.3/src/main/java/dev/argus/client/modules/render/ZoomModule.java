package dev.argus.client.modules.render;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftRunTickEvent;
import dev.argus.client.events.MouseHandlerOnScrollEvent;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.FloatAnimation;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.util.Mth;

public class ZoomModule extends Module {
   private final Setting<Boolean> smoothSetting = this.add(new BooleanSetting("Smooth", true));
   protected final Setting<Boolean> smoothCameraSetting = this.add(new BooleanSetting("SmoothCamera", true));
   private final Setting<Boolean> scrollSetting = this.add(new BooleanSetting("Scroll", false));
   private final Setting<Integer> amountSetting;
   private final ZoomOptionsUpdater optionsUpdater;
   protected int baselineFov;
   protected boolean savedSmoothCamera;
   protected boolean fovUpdateActive;
   private final FloatAnimation zoomAnimation;
   private final FloatAnimation scrollAnimation;

   protected int calculateFov() {
      float baseline = this.baselineFov;
      Integer divisor = this.amountSetting.getValue();
      int fovDifference = 75 / divisor - this.baselineFov;
      float distance = Math.abs(fovDifference);
      int fov = (int)(baseline - distance * this.getZoomBlend());
      Boolean scrollEnabled = this.scrollSetting.getValue();
      if (!scrollEnabled) {
         return fov;
      }

      float adjustedFov = fov;
      FloatAnimation animation = this.scrollAnimation;
      adjustedFov -= animation.getValue();
      return (int)Mth.clamp(adjustedFov, 10.0F, 150.0F);
   }

   public float getZoomBlend() {
      FloatAnimation animation = this.zoomAnimation;
      double progress = 1.0F - animation.getValue();
      return (float)(1.0 - Math.pow(progress, 4.0));
   }

   @Override
   public void onEnable() {
      if (this.isWorldUnavailable()) {
         this.disable();
      } else {
         EventBus bus = ClientAccess.eventBus;
         ZoomOptionsUpdater updater = this.optionsUpdater;
         bus.subscribe(updater);
         if (!this.fovUpdateActive) {
            Options options = ClientAccess.argusclientMinecraft.options;
            OptionInstance fov = options.fov();
            this.baselineFov = (Integer)fov.get();
         }

         FloatAnimation animation = this.zoomAnimation;
         long duration = this.getSmoothingDuration(200);
         animation.setTarget(1.0F, duration);
         this.savedSmoothCamera = ClientAccess.argusclientMinecraft.options.smoothCamera;
      }
   }

   @Override
   public void onDisable() {
      FloatAnimation primaryAnimation = this.zoomAnimation;
      long primaryDuration = this.getSmoothingDuration(100);
      primaryAnimation.setTarget(0.0F, primaryDuration);
      FloatAnimation scroll = this.scrollAnimation;
      long scrollDuration = this.getSmoothingDuration(100);
      scroll.setTarget(0.0F, scrollDuration);
      ClientAccess.argusclientMinecraft.options.smoothCamera = this.savedSmoothCamera;
   }

   private int getSmoothingDuration(int duration) {
      return !this.smoothSetting.getValue() ? 0 : duration;
   }

   @EventHandler
   private void onMouseHandlerOnScrollEvent(MouseHandlerOnScrollEvent event) {
      if (event.getVerticalOffset() != 0.0) {
         Boolean enabled = this.scrollSetting.getValue();
         if (enabled) {
            float baseline = this.baselineFov;
            Integer divisor = this.amountSetting.getValue();
            int fovDifference = 75 / divisor - this.baselineFov;
            float distance = Math.abs(fovDifference);
            int fov = (int)(baseline - distance * this.getZoomBlend());
            double currentValue = this.scrollAnimation.getValue();
            double delta = event.getVerticalOffset();
            float target = (float)(currentValue + delta * 10.0);
            float minimum = fov - this.baselineFov;
            float maximum = fov - 10;
            int clampedTarget = (int)Mth.clamp(target, minimum, maximum);
            FloatAnimation targetScroll = this.scrollAnimation;
            float scrollTarget = clampedTarget;
            long duration = this.getSmoothingDuration(100);
            targetScroll.setTarget(scrollTarget, duration);
            event.cancel();
         }
      }
   }

   @EventHandler
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      if (this.isWorldUnavailable()) {
         this.disable();
      }
   }

   public ZoomModule() {
      super("Zoom", "Zooms your camera in.", ModuleCategory.RENDER);
      String amountKey = new String("Amount");
      this.amountSetting = this.add(new NumberSetting<>(amountKey, 2, 2, 5));
      this.optionsUpdater = new ZoomOptionsUpdater(this);
      this.zoomAnimation = new FloatAnimation();
      this.scrollAnimation = new FloatAnimation();
   }
}
