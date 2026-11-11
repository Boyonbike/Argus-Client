package dev.argus.client.utils.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.MinecraftRunTickEvent;
import dev.argus.client.utils.ElapsedTimer;
import java.util.ArrayDeque;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

public class MovementSpeedTracker implements ClientAccess {
   private final ArrayDeque<Double> speedSamples = new ArrayDeque<>();
   private final ElapsedTimer displayTimer = new ElapsedTimer();
   private double averageSpeed;
   private double displaySpeed;
   private double currentSpeed;

   public double getAverageSpeed() {
      return this.averageSpeed;
   }

   public double getCurrentSpeed() {
      return this.currentSpeed;
   }

   public double getDisplaySpeed() {
      return this.displaySpeed;
   }

   public double calculateCurrentSpeed() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Entity entity = player.isPassenger() ? player.getVehicle() : player;
      return Math.hypot(entity.getX() - entity.xo, entity.getZ() - entity.zo) * 20.0 * 3.6;
   }

   public MovementSpeedTracker() {
      ClientAccess.eventBus.subscribe(this);
   }

   @EventHandler
   private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
      this.averageSpeed = 0.0;
      this.displaySpeed = 0.0;
      this.speedSamples.clear();
      this.displayTimer.reset();
   }

   @EventHandler
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      if (event.hasPendingTicks() && !this.isWorldUnavailable()) {
         this.currentSpeed = this.calculateCurrentSpeed();
         if (!(this.currentSpeed > 0.0) && ClientAccess.argusclientMinecraft.player.tickCount % 4 != 0) {
            this.speedSamples.pollFirst();
         } else {
            this.speedSamples.add(this.currentSpeed * ClientContext.tickRateController.getMultiplier());
         }

         while (this.speedSamples.size() > 9) {
            this.speedSamples.pollFirst();
         }

         double total = 0.0;

         for (double speed : this.speedSamples) {
            total += speed;
         }

         this.averageSpeed = this.speedSamples.isEmpty() ? 0.0 : total / this.speedSamples.size();
         if (this.averageSpeed < 21.0) {
            this.displaySpeed = this.averageSpeed;
         } else if (this.displayTimer.hasElapsed(50L)) {
            this.displaySpeed = this.averageSpeed;
            this.displayTimer.reset();
         }
      }
   }
}
