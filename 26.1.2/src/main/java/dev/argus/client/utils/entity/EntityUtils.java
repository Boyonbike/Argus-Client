package dev.argus.client.utils.entity;

import dev.argus.client.ClientAccess;
import dev.argus.client.utils.combat.ZombifiedPiglinAttackState;
import dev.argus.mixin.FireworkRocketEntityAccessor;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.ClientAsset.ResourceTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import net.minecraft.world.entity.animal.wolf.WolfVariant.AssetInfo;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.PiglinArmPose;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class EntityUtils implements ClientAccess {
   public static Set<BlockPos> getFireworkPositions() {
      HashSet<BlockPos> positions = new HashSet<>();
      ClientLevel level = ClientAccess.argusclientMinecraft.level;

      for (Entity entity : level.entitiesForRendering()) {
         if (entity instanceof FireworkRocketEntity) {
            positions.add(entity.blockPosition());
         }
      }

      return positions;
   }

   private EntityUtils() {
   }

   public static boolean isHostile(Entity entity) {
      if (entity instanceof EnderMan enderman) {
         return enderman.isCreepy();
      } else if (entity instanceof ZombifiedPiglinAttackState attackingEntity) {
         return attackingEntity.argusclient$isAttacking();
      } else if (entity instanceof Wolf wolf) {
         Identifier currentTexture = wolf.getTexture();
         Holder<WolfVariant> variantHolder = wolf.getVariant();
         WolfVariant variant = variantHolder.value();
         AssetInfo assets = variant.assetInfo();
         ResourceTexture angryTexture = assets.angry();
         return currentTexture == angryTexture.texturePath();
      } else if (entity instanceof NeutralMob neutralMob) {
         return neutralMob.isAngry();
      } else if (entity instanceof AbstractPiglin piglin) {
         if (entity instanceof PiglinBrute) {
            return true;
         }

         if (piglin.isBaby()) {
            return false;
         }

         PiglinArmPose armPose = piglin.getArmPose();
         return armPose == PiglinArmPose.CROSSBOW_CHARGE || armPose == PiglinArmPose.CROSSBOW_HOLD || armPose == PiglinArmPose.ATTACKING_WITH_MELEE_WEAPON;
      } else {
         return entity instanceof PolarBear || entity instanceof Enemy;
      }
   }

   public static boolean isNeutral(Entity entity) {
      return entity instanceof NeutralMob || entity instanceof AbstractPiglin && !(entity instanceof PiglinBrute);
   }

   public static boolean isLookingAtPlayer(Entity entity) {
      if (entity instanceof LivingEntity livingEntity) {
         Vec3 eyePosition = livingEntity.getEyePosition();
         float pitch = livingEntity.getXRot();
         float headYaw = livingEntity.getYHeadRot();
         Vec3 viewDirection = livingEntity.calculateViewVector(pitch, headYaw);
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         double playerAlignment = getViewAlignment(eyePosition, viewDirection, player);
         ClientLevel level = ClientAccess.argusclientMinecraft.level;
         AABB bounds = livingEntity.getBoundingBox();
         AABB searchBounds = bounds.inflate(16.0);
         Predicate<Entity> otherLivingEntities = candidate -> candidate instanceof LivingEntity
            && candidate != ClientAccess.argusclientMinecraft.player
            && candidate.isAlive();

         for (Entity nearby : level.getEntities(livingEntity, searchBounds, otherLivingEntities)) {
            if (getViewAlignment(eyePosition, viewDirection, nearby) > playerAlignment) {
               return false;
            }
         }

         return playerAlignment > 0.5;
      } else {
         return false;
      }
   }

   private static double getViewAlignment(Vec3 eyePosition, Vec3 viewDirection, Entity entity) {
      Vec3 toEntity = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ()).subtract(eyePosition);
      return toEntity.lengthSqr() < 1.0E-4 ? 1.0 : viewDirection.dot(toEntity.normalize());
   }

   public static float getPlayerHealthWithAbsorption() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      return getHealthWithAbsorption(player);
   }

   public static float getHealthFraction(LivingEntity entity) {
      float healthFraction = getHealthWithAbsorption(entity);
      healthFraction /= entity.getMaxHealth();
      float maximum = 1.0F;
      return Mth.clamp(healthFraction, 0.0F, maximum);
   }

   public static boolean isPassive(Entity entity) {
      return entity instanceof AbstractFish
         || entity instanceof AgeableMob
         || entity instanceof Squid
         || entity instanceof Dolphin
         || entity instanceof Villager;
   }

   public static boolean hasCollisionBelow(LivingEntity entity) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      AABB boundsSnapshot = entity.getBoundingBox();
      AABB expandedBounds = boundsSnapshot.expandTowards(0.0, -0.1, 0.0);
      return !level.noBlockCollision(entity, expandedBounds);
   }

   public static void setStepHeight(LivingEntity entity, float height) {
      Holder<Attribute> stepHeightAttribute = Attributes.STEP_HEIGHT;
      AttributeInstance attributeSnapshot = entity.getAttribute(stepHeightAttribute);
      attributeSnapshot.setBaseValue(height);
   }

   public static boolean isFirstPersonCameraEntity(Entity entity) {
      Options options = ClientAccess.argusclientMinecraft.options;
      if (options.getCameraType() != CameraType.FIRST_PERSON) {
         return false;
      } else {
         return entity == ClientAccess.argusclientMinecraft.player ? false : ClientAccess.argusclientMinecraft.getCameraEntity() == entity;
      }
   }

   public static boolean hasFirework(Entity shooter) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;

      for (Entity entity : level.entitiesForRendering()) {
         if (entity instanceof FireworkRocketEntityAccessor firework && firework.argusclient$getShooter() == shooter) {
            return true;
         }
      }

      return false;
   }

   public static float getHealthWithAbsorption(Entity entity) {
      if (entity instanceof LivingEntity livingEntity) {
         float health = livingEntity.getHealth();
         return health + livingEntity.getAbsorptionAmount();
      } else {
         return 2.0F;
      }
   }

   public static float getStepHeight(LivingEntity entity) {
      Holder<Attribute> stepHeightAttribute = Attributes.STEP_HEIGHT;
      AttributeInstance attribute = entity.getAttribute(stepHeightAttribute);
      return (float)attribute.getValue();
   }
}
