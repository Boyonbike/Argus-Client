package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.modules.movement.FlightModule;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.AntiCheatRotationsChoice;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerStateTracker;
import dev.argus.client.utils.entity.PlayerStateUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.AABB;

public final class AuraAttackReadiness implements ClientAccess {
   private static AntiCheatModule antiCheat = ClientContext.getModule(AntiCheatModule.class);
   private static FlightModule flight = ClientContext.getModule(FlightModule.class);
   private static CriticalsModule criticals = ClientContext.getModule(CriticalsModule.class);
   private final AuraModule aura;

   private boolean shouldWaitForCriticalMovement() {
      boolean onGround = ClientAccess.argusclientMinecraft.player.onGround();
      if (!PlayerStateUtils.isInCobweb(ClientAccess.argusclientMinecraft.player) && !ClientAccess.argusclientMinecraft.player.isPassenger()) {
         Setting<AntiCheatRotationsChoice> rotations = antiCheat.rotationsSetting;
         boolean motionSynchronized = false;
         if (rotations.getValue() == AntiCheatRotationsChoice.MOTION) {
            Setting<Boolean> movementSync = antiCheat.movementSyncSetting;
            motionSynchronized = movementSync.getValue();
         }

         return motionSynchronized ? this.shouldWaitForMotionCritical(onGround) : this.shouldWaitForAirborneCritical(onGround);
      } else {
         return false;
      }
   }

   private boolean shouldWaitForCritical(Entity target) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (!flight.isToggled() && !player.getAbilities().flying && !player.isFallFlying() && !player.onClimbable() && !PlayerStateUtils.isInLiquid(player)) {
         if (criticals.isToggled() && criticals.getMode() == CriticalsModule.ModeChoice.GRIM) {
            return false;
         }

         Holder<MobEffect> levitation = MobEffects.LEVITATION;
         return player.hasEffect(levitation) ? false : target instanceof LivingEntity && this.shouldWaitForCriticalMovement();
      } else {
         return false;
      }
   }

   public AuraAttackReadiness(AuraModule aura) {
      this.aura = aura;
   }

   private boolean shouldWaitForAirborneCritical(boolean onGround) {
      return PlayerStateUtils.getTrackedFallDistance() <= 0.0F && !onGround ? true : onGround && ClientContext.playerStateTracker.getGroundedTicks() <= 1;
   }

   private boolean shouldWaitForMotionCritical(boolean onGround) {
      double currentY = ClientContext.playerStateTracker.getCurrentTickY();
      PlayerStateTracker previousYSource = ClientContext.playerStateTracker;
      float verticalMovement = (float)(currentY - previousYSource.getPreviousTickY());
      AABB collisionBounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
      if (verticalMovement > 0.0F) {
         double top = collisionBounds.maxY;
         AABB upperSlice = collisionBounds.setMinY(top);
         double upperY = verticalMovement;
         collisionBounds = upperSlice.deflate(0.0, upperY, 0.0);
      } else {
         double bottom = collisionBounds.minY;
         AABB lowerSlice = collisionBounds.setMaxY(bottom);
         double lowerY = verticalMovement;
         collisionBounds = lowerSlice.deflate(0.0, lowerY, 0.0);
      }

      CollisionGetter descendingLevel = ClientAccess.argusclientMinecraft.level;
      LocalPlayer descendingPlayer = ClientAccess.argusclientMinecraft.player;
      AABB descendingBounds = collisionBounds;
      if (!descendingLevel.noBlockCollision(descendingPlayer, descendingBounds) && verticalMovement < 0.0F) {
         return true;
      }

      CollisionGetter ascendingLevel = ClientAccess.argusclientMinecraft.level;
      LocalPlayer ascendingPlayer = ClientAccess.argusclientMinecraft.player;
      AABB ascendingBounds = collisionBounds;
      return !ascendingLevel.noBlockCollision(ascendingPlayer, ascendingBounds) && verticalMovement > 0.0F
         ? false
         : PlayerStateUtils.getTrackedFallDistance() <= 0.0F && !onGround;
   }

   public boolean canAttack(Entity target, float attackStrength) {
      if (target instanceof EndCrystal) {
         return true;
      }

      if (this.shouldWaitForCritical(target)) {
         return false;
      }

      if (!(target instanceof Player) && target instanceof LivingEntity livingTarget && !antiCheat.is2b2tEnabled()) {
         double health = EntityUtils.getHealthWithAbsorption(livingTarget);
         if (health <= 1.0) {
            float maximumHealth = livingTarget.getMaxHealth();
            if (maximumHealth <= 4.0F) {
               double strength = attackStrength;
               return strength >= 0.2;
            }
         }
      }

      AuraModule delaySource = this.aura;
      return attackStrength >= delaySource.getAttackDelay();
   }

   public static void bindModuleRegistry(ModuleRegistry modules) {
      antiCheat = (AntiCheatModule)modules.getModule(AntiCheatModule.class);
      flight = (FlightModule)modules.getModule(FlightModule.class);
      criticals = (CriticalsModule)modules.getModule(CriticalsModule.class);
   }
}
