package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class AutoCrystalTargeting {
   private final List<LivingEntity> targets = new ArrayList<>();
   private final AutoCrystalModule module;

   AutoCrystalTargeting(AutoCrystalModule module) {
      this.module = module;
   }

   synchronized void refresh() {
      this.targets.clear();

      for (Entity entity : ClientAccess.argusclientMinecraft.level.entitiesForRendering()) {
         if (entity instanceof LivingEntity candidate && !this.module.isInvalidTarget(candidate)) {
            if (entity instanceof Player player) {
               this.targets.add(player);
            }

            if (this.module.bossesSetting.getValue() && (candidate instanceof WitherBoss || candidate instanceof EnderDragon || candidate instanceof Warden)) {
               this.targets.add(candidate);
            }
         }
      }
   }

   synchronized List<LivingEntity> snapshot() {
      return new ArrayList<>(this.targets);
   }

   LivingEntity findProximityTarget() {
      LivingEntity proximityTarget = this.module.target == null && !this.module.isInvalidTarget(this.module.previousTarget)
         ? this.module.previousTarget
         : this.module.target;
      if (proximityTarget == null) {
         double nearestDistance = Double.MAX_VALUE;

         for (LivingEntity candidate : this.snapshot()) {
            double distance = ClientAccess.argusclientMinecraft.player.distanceTo(candidate);
            if (!this.module.isInvalidTarget(candidate) && !(distance > nearestDistance)) {
               nearestDistance = distance;
               proximityTarget = candidate;
            }
         }

         if (proximityTarget == null) {
            return null;
         }

         this.module.target = proximityTarget;
      }

      return proximityTarget;
   }

   boolean hasObsidianDropNear(LivingEntity proximityTarget) {
      for (Entity entity : ClientAccess.argusclientMinecraft.level.entitiesForRendering()) {
         if (entity instanceof ItemEntity item) {
            ItemStack itemStack = item.getItem();
            if (itemStack.getItem() == Items.OBSIDIAN) {
               ItemStack countStack = item.getItem();
               int itemCount = countStack.getCount();
               if (itemCount >= 8 && entity.isAlive()) {
                  AABB targetBounds = proximityTarget.getBoundingBox();
                  AABB itemBounds = entity.getBoundingBox();
                  Vec3 targetCenter = targetBounds.getCenter();
                  double centerY = targetCenter.y();
                  AABB upperTargetBounds = targetBounds.setMinY(centerY);
                  double expandZ = 0.5;
                  double expandX = 0.5;
                  AABB proximityBox = upperTargetBounds.inflate(expandX, 0.0, expandZ);
                  if (itemBounds.intersects(proximityBox)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }
}
