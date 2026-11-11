package dev.argus.client.modules.misc;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.CameraSetupEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.ScreenChangeEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class HeavenModule extends Module {
   private long deathTimeMillis;

   @EventHandler
   private void onCameraSetupEvent(CameraSetupEvent event) {
      if (ClientAccess.argusclientMinecraft.gui.screen() instanceof DeathScreen) {
         long elapsedSinceDeath = System.currentTimeMillis() - this.deathTimeMillis;
         float clampedElapsed = (float)Mth.clamp(elapsedSinceDeath, 0L, 10000L);
         float riseProgress = clampedElapsed / 10000.0F;
         Vec3 capturedPosition = event.getPosition();
         double riseOffset = riseProgress * 400.0F;
         Vec3 position = capturedPosition.add(0.0, riseOffset, 0.0);
         Axis yAxis = Axis.Y;
         double capturedY = position.y;
         double heightLimit = 400.0;
         double clampedY = Math.min(capturedY, heightLimit);
         position = position.with(yAxis, clampedY);
         event.setPosition(position);
         event.cancel();
      }
   }

   @EventHandler
   private void onScreenChange(ScreenChangeEvent.AfterInit event) {
      if (event.getNewScreen() instanceof DeathScreen) {
         this.deathTimeMillis = System.currentTimeMillis();
      }
   }

   public HeavenModule() {
      super("Heaven", "Brings you to heaven after you die.", ModuleCategory.MISC);
   }
}
