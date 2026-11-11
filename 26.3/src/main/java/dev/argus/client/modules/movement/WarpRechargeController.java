package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.settings.options.WarpRechargeChoice;
import java.util.LinkedList;
import java.util.Queue;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class WarpRechargeController implements ClientAccess {
   protected final Queue<ServerboundPongPacket> pendingPongs = new LinkedList<>();
   private final WarpModule warp;

   public WarpRechargeController(WarpModule warp) {
      ClientAccess.eventBus.subscribe(this);
      this.warp = warp;
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (!this.warp.hasDisplacement() && this.warp.modeSetting.getValue() == WarpModule.ModeChoice.PLAIN) {
         int maximumCharge = this.warp.chargeSetting.getValue();
         int increasedCharge = this.warp.plainCharge + this.warp.chargeSpeedSetting.getValue();
         this.warp.plainCharge = Math.min(maximumCharge, increasedCharge);
      }
   }

   @EventHandler
   private void onConnectionSendPacketEvent(PacketEvent.Send event) {
      if (!this.isWorldUnavailable()) {
         if (this.warp.isToggled()
            && this.warp.modeSetting.getValue() == WarpModule.ModeChoice.ALTERNATIVE
            && event.getPacket() instanceof ServerboundPongPacket pong) {
            if (!this.warp.hasMovementInputOrDisplacement()) {
               int pendingCount = this.pendingPongs.size();
               Integer chargeLimit = this.warp.chargeSetting.getValue();
               if (pendingCount < chargeLimit) {
                  event.cancel();
                  this.pendingPongs.add(pong);
               }
            }

            if (!this.pendingPongs.isEmpty()) {
               event.cancel();
            }
         }

         if (this.warp.modeSetting.getValue() == WarpModule.ModeChoice.PLAIN && event.getPacket() instanceof ServerboundMovePlayerPacket) {
            if (this.warp.hasDisplacement() && this.warp.plainCharge != 0) {
               if (this.warp.isToggled() || this.warp.rechargeSetting.getValue() != WarpRechargeChoice.HOLD) {
                  int decreasedCharge = this.warp.plainCharge - 1;
                  this.warp.plainCharge = Math.max(0, decreasedCharge);
               }
            }
         }
      }
   }
}
