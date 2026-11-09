package dev.argus.client.modules.render;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftRunTickEvent;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.render.GameOptionsOverrides;
import dev.argus.client.settings.Setting;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

public class ZoomOptionsUpdater implements ClientAccess {
   private final ZoomModule zoomModule;

   public ZoomOptionsUpdater(ZoomModule zoomModule) {
      this.zoomModule = zoomModule;
   }

   @EventHandler
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      ZoomModule fovModule = this.zoomModule;
      int fov = fovModule.calculateFov();
      OptionInstance fovOption = ClientAccess.argusclientMinecraft.options.fov();
      Integer fovValue = fov;
      GameOptionsOverrides.forceSetOptionValue(fovOption, fovValue);
      Options cameraOptions = ClientAccess.argusclientMinecraft.options;
      ZoomModule toggleModule = this.zoomModule;
      boolean smoothCamera = false;
      if (toggleModule.isToggled()) {
         Setting<Boolean> smoothCameraSetting = this.zoomModule.smoothCameraSetting;
         smoothCamera = smoothCameraSetting.getValue();
      }

      cameraOptions.smoothCamera = smoothCamera;
      if (fov >= this.zoomModule.baselineFov) {
         ZoomModule currentToggleModule = this.zoomModule;
         if (!currentToggleModule.isToggled()) {
            EventBus bus = ClientAccess.eventBus;
            bus.unsubscribe(this);
            this.zoomModule.fovUpdateActive = false;
            return;
         }
      }

      this.zoomModule.fovUpdateActive = true;
   }
}
