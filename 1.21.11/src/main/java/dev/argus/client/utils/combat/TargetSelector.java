package dev.argus.client.utils.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.util.Comparator;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.vehicle.VehicleEntity;

public final class TargetSelector implements ClientAccess {
   private boolean playersEnabled;
   private boolean neutralsEnabled;
   private boolean animalsEnabled;
   private boolean villagersEnabled;
   private boolean hostilesEnabled;
   private boolean crystalsEnabled;
   private boolean projectilesEnabled;
   private boolean vehiclesEnabled;
   private float wallRange;
   private float visibleRange;
   private boolean extraRangeEnabled;
   private boolean ignoreNamedEntities;
   private boolean requireArmor;
   private boolean ignoreInvisiblePlayers;
   private Comparator<Entity> comparator;
   private Predicate<Entity> predicate;

   public TargetSelector() {
      LocalPlayer comparatorPlayerSnapshot = ClientAccess.argusclientMinecraft.player;
      this.comparator = Comparator.comparing(comparatorPlayerSnapshot::distanceToSqr);
   }

   public Entity select() {
      Entity selected = null;
      ClientLevel level = ClientAccess.argusclientMinecraft.level;

      for (Entity candidate : level.entitiesForRendering()) {
         if (!(candidate instanceof ExperienceOrb)
            && WorldUtils.isTargetable(candidate)
            && this.matchesTargetType(candidate)
            && (candidate instanceof Player || !candidate.hasCustomName() || !this.ignoreNamedEntities)
            && !(candidate instanceof Player playerCandidate && playerCandidate.isInvisible() && this.ignoreInvisiblePlayers)
            && (this.predicate == null || this.predicate.test(candidate))) {
            boolean useExtraRange = this.extraRangeEnabled;
            double distance = WorldUtils.getEntityDistance(candidate, useExtraRange);
            float wallLimit = this.wallRange;
            float visibleLimit = this.visibleRange;
            if (!(distance > Math.max(visibleLimit, wallLimit))) {
               float rangeDifference = this.wallRange - this.visibleRange;
               boolean useVisibleRange = Math.abs(rangeDifference) <= 0.01;
               if (!useVisibleRange) {
                  useVisibleRange = RaycastUtils.hasVisibleEntitySample(candidate);
               }

               float applicableRange = useVisibleRange ? this.visibleRange : this.wallRange;
               if (!(distance > applicableRange) && (selected == null || this.comparator.compare(candidate, selected) < 0)) {
                  selected = candidate;
               }
            }
         }
      }

      return selected;
   }

   private boolean matchesTargetType(Entity candidate) {
      return candidate instanceof Player playerCandidate && this.playersEnabled && this.isEligiblePlayer(playerCandidate)
         ? true
         : EntityUtils.isPassive(candidate) && this.animalsEnabled && !(candidate instanceof Villager)
            || candidate instanceof Villager && this.villagersEnabled
            || EntityUtils.isHostile(candidate) && this.hostilesEnabled && (!EntityUtils.isNeutral(candidate) || EntityUtils.isLookingAtPlayer(candidate))
            || !(candidate instanceof PiglinBrute) && candidate instanceof Piglin && this.neutralsEnabled
            || candidate instanceof NeutralMob && this.neutralsEnabled
            || candidate instanceof EndCrystal && this.crystalsEnabled
            || (candidate instanceof Fireball || candidate instanceof WindCharge || candidate instanceof ShulkerBullet) && this.projectilesEnabled
            || candidate instanceof VehicleEntity && this.vehiclesEnabled;
   }

   private boolean isEligiblePlayer(Player player) {
      return this.requireArmor && !PlayerStateUtils.hasArmor(player) ? false : player.isAlive() && player.deathTime <= 0;
   }

   public TargetSelector withPlayers(boolean enabled) {
      this.playersEnabled = enabled;
      return this;
   }

   public TargetSelector withNeutrals(boolean enabled) {
      this.neutralsEnabled = enabled;
      return this;
   }

   public TargetSelector withAnimals(boolean enabled) {
      this.animalsEnabled = enabled;
      return this;
   }

   public TargetSelector withVillagers(boolean enabled) {
      this.villagersEnabled = enabled;
      return this;
   }

   public TargetSelector withHostiles(boolean enabled) {
      this.hostilesEnabled = enabled;
      return this;
   }

   public TargetSelector withCrystals(boolean enabled) {
      this.crystalsEnabled = enabled;
      return this;
   }

   public TargetSelector withProjectiles(boolean enabled) {
      this.projectilesEnabled = enabled;
      return this;
   }

   public TargetSelector withVehicles(boolean enabled) {
      this.vehiclesEnabled = enabled;
      return this;
   }

   public TargetSelector wallRange(float range) {
      this.wallRange = range;
      return this;
   }

   public TargetSelector range(float range) {
      this.visibleRange = range;
      this.wallRange = range;
      return this;
   }

   public TargetSelector visibleRange(float range) {
      this.visibleRange = range;
      return this;
   }

   public TargetSelector useExtraRange(boolean enabled) {
      this.extraRangeEnabled = enabled;
      return this;
   }

   public TargetSelector ignoreNamed(boolean enabled) {
      this.ignoreNamedEntities = enabled;
      return this;
   }

   public TargetSelector ignoreNakeds(boolean enabled) {
      this.requireArmor = enabled;
      return this;
   }

   public TargetSelector ignoreInvisibles(boolean enabled) {
      this.ignoreInvisiblePlayers = enabled;
      return this;
   }

   public TargetSelector sortBy(Comparator<Entity> comparator) {
      this.comparator = comparator;
      return this;
   }

   public TargetSelector filter(Predicate<Entity> predicate) {
      this.predicate = predicate;
      return this;
   }
}
