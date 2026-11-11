package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import net.minecraft.client.player.LocalPlayer;

public final class HoleSnapChargeController implements ClientAccess {
   private final HoleSnapModule module;
   private int chargeTicks;
   private boolean drainingCharge;

   public HoleSnapChargeController(HoleSnapModule module) {
      this.module = module;
      ClientAccess.eventBus.subscribe(this);
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (ClientContext.playerTickController.getMultiplier() > 1.0F) {
         this.drainingCharge = true;
      }

      boolean moving = this.isPlayerMoving();
      if (!moving) {
         this.drainingCharge = false;
      }

      if (moving && this.drainingCharge) {
         this.chargeTicks = Math.max(0, this.chargeTicks - 1);
      } else {
         int maximumCharge = this.module.chargeSetting.getValue();
         this.chargeTicks = Math.min(maximumCharge, this.chargeTicks + 1);
      }
   }

   public void requestShift() {
      if (this.chargeTicks > 0
         && this.isPlayerMoving()
         && ClientAccess.argusclientMinecraft.player.onGround()
         && ClientContext.playerStateTracker.getGroundedTicks() > 1) {
         float factor = this.module.factorSetting.getValue().intValue();
         ClientContext.playerTickController.requestMultiplier(this.module, factor);
      }
   }

   private boolean isPlayerMoving() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      return player.position().distanceToSqr(player.xo, player.yo, player.zo) > 0.0;
   }
}
