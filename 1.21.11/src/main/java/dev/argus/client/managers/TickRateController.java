package dev.argus.client.managers;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.ModuleToggleEvent;
import dev.argus.client.modules.Module;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.Mth;

public class TickRateController implements ClientAccess {
   private final ConcurrentHashMap<Module, Boolean> moduleReleaseStates = new ConcurrentHashMap<>();
   private float multiplier = 1.0F;

   public TickRateController() {
      ClientAccess.eventBus.subscribe(this);
   }

   @EventHandler
   private void onModuleToggle(ModuleToggleEvent event) {
      Module module = event.getModule();
      if (!module.isToggled()) {
         Module moduleToRelease = event.getModule();
         this.releaseMultiplier(moduleToRelease);
      }
   }

   public void requestMultiplier(Module module, float requestedMultiplier) {
      this.moduleReleaseStates.compute(module, (keyModule, previousReleased) -> false);
      this.multiplier = requestedMultiplier;
   }

   public float getMultiplier() {
      return this.multiplier;
   }

   public void releaseMultiplier(Module module) {
      this.moduleReleaseStates.compute(module, (keyModule, previousReleased) -> {
         if (Boolean.FALSE.equals(previousReleased)) {
            this.multiplier = 1.0F;
         }

         return true;
      });
   }

   public static class PlayerTicks extends TickRateController {
      private float fractionalTicks;

      @EventHandler(priority = -200)
      private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
         float rate = this.getMultiplier();
         if (!(rate <= 1.0F)) {
            this.fractionalTicks += rate - (int)rate;

            for (int extraTicks = 0; extraTicks < Math.floor(rate) + Math.floor(this.fractionalTicks) - 1.0; extraTicks++) {
               PlayerTickExecutor.runExtraTick();
            }

            float fractionSnapshot = this.fractionalTicks;
            if (fractionSnapshot >= 1.0F) {
               float fractionToReduce = this.fractionalTicks;
               float reducedFraction = fractionToReduce - 1.0F;
               float maximumFraction = 1.0F;
               this.fractionalTicks = Mth.clamp(reducedFraction, 0.0F, maximumFraction);
            }
         }
      }
   }
}
