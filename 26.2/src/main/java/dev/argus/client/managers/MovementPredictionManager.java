package dev.argus.client.managers;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.ClientLevelRemoveEntityEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.MinecraftRunTickEvent;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.movement.PlayerMotionPredictor;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class MovementPredictionManager implements ClientAccess {
   private final Map<Integer, PlayerMotionPredictor> playerPredictions = Collections.synchronizedMap(new HashMap<>());
   private final ElapsedTimer predictionTimer = new ElapsedTimer();
   /** Predictions only run while a module has asked for one recently. */
   private static final long DEMAND_MILLIS = 2000L;
   private volatile long lastDemandMillis = Long.MIN_VALUE / 2;
   private Vec3 previousPlayerPosition;

   public Vec3 getAdjustedLocalMotion() {
      if (this.previousPlayerPosition == null) {
         return Vec3.ZERO;
      }

      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Vec3 motion = player.position().subtract(this.previousPlayerPosition);
      if (player.isSprinting() && !player.isFallFlying()) {
         motion = motion.multiply(0.9F, 1.0, 0.9F);
      }

      return motion;
   }

   public Vec3 getPredictedEyePosition() {
      return ClientAccess.argusclientMinecraft.player.getEyePosition().add(this.getAdjustedLocalMotion());
   }

   public MovementPredictionManager() {
      ClientAccess.eventBus.subscribe(this);
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      this.previousPlayerPosition = new Vec3(
         ClientAccess.argusclientMinecraft.player.xo, ClientAccess.argusclientMinecraft.player.yo, ClientAccess.argusclientMinecraft.player.zo
      );
   }

   @EventHandler
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      if (System.currentTimeMillis() - this.lastDemandMillis >= DEMAND_MILLIS) {
         // Nobody reads predictions: skip simulating every player, and drop old history so the
         // next request starts from fresh motion instead of positions from seconds ago.
         if (!this.playerPredictions.isEmpty()) {
            this.playerPredictions.clear();
         }

         return;
      }

      if (!this.isWorldUnavailable() && this.predictionTimer.hasElapsedAndReset(25L)) {
         for (AbstractClientPlayer player : ClientAccess.argusclientMinecraft.level.players()) {
            this.playerPredictions.computeIfAbsent(player.getId(), ignored -> new PlayerMotionPredictor(player)).updatePredictions();
         }
      }
   }

   @EventHandler
   private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
      this.playerPredictions.clear();
   }

   public synchronized AABB getPredictedBoundingBox(Player player, int tickIndex) {
      this.lastDemandMillis = System.currentTimeMillis();
      PlayerMotionPredictor prediction = this.playerPredictions.get(player.getId());
      if (prediction == null) {
         return player.getBoundingBox();
      }

      List<AABB> positions = prediction.getPredictedBoxes();
      return positions.isEmpty() ? player.getBoundingBox() : positions.get(Math.min(tickIndex, positions.size() - 1));
   }

   @EventHandler
   private void onClientLevelRemoveEntityEvent(ClientLevelRemoveEntityEvent event) {
      synchronized (this.playerPredictions) {
         this.playerPredictions.remove(event.getId());
      }
   }
}
