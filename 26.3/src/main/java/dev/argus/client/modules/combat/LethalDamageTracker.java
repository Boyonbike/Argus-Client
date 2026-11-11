package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.DamageUtils;
import dev.argus.client.utils.entity.EntityUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class LethalDamageTracker implements ClientAccess {
   private final ElapsedTimer lethalCrystalTimer = new ElapsedTimer();
   private final LethalDamageTracker.LethalCheckPolicy policy;

   public LethalDamageTracker(LethalDamageTracker.LethalCheckPolicy checkPolicy) {
      this.policy = checkPolicy;
   }

   public void update() {
      if (this.hasLethalCrystal()) {
         this.lethalCrystalTimer.reset();
      }
   }

   public boolean isLethalCheckEnabled() {
      return this.policy.isLethalCheckEnabled();
   }

   public boolean isCrystalLethal(Vec3 crystalPosition) {
      double squaredDistance = crystalPosition.distanceToSqr(ClientAccess.argusclientMinecraft.player.position());
      if (squaredDistance > 144.0) {
         return false;
      }

      for (int predictionStep = 4; predictionStep >= 0; predictionStep--) {
         AABB predictedPlayerBox = ClientContext.movementPredictionManager.getPredictedBoundingBox(ClientAccess.argusclientMinecraft.player, predictionStep);
         LocalPlayer playerForDamageCheck = ClientAccess.argusclientMinecraft.player;
         double predictedDamage = DamageUtils.getCrystalDamage(crystalPosition, playerForDamageCheck, predictedPlayerBox);
         if (predictedDamage >= EntityUtils.getPlayerHealthWithAbsorption()) {
            return true;
         }
      }

      return false;
   }

   private boolean isNearbyCrystal(Entity entity) {
      if (entity instanceof EndCrystal) {
         double squaredDistance = ClientAccess.argusclientMinecraft.player.distanceToSqr(entity);
         return squaredDistance <= 144.0;
      } else {
         return false;
      }
   }

   public boolean shouldHoldTotem() {
      return this.isLethalCheckEnabled() && !this.lethalCrystalTimer.hasElapsed(750L);
   }

   public boolean hasLethalCrystal() {
      synchronized (ClientAccess.argusclientMinecraft.level.entitiesForRendering()) {
         for (Entity entity : ClientAccess.argusclientMinecraft.level.entitiesForRendering()) {
            if (this.isNearbyCrystal(entity) && this.isCrystalLethal(entity.position())) {
               return true;
            }
         }

         return false;
      }
   }

   public interface LethalCheckPolicy {
      boolean isLethalCheckEnabled();
   }
}
