package dev.argus.client.utils.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.render.RenderUtils;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MovementPhysics implements ClientAccess {
   public static float baseMoveSpeed = 0.2873F;
   public static float scaledBaseMoveSpeed = baseMoveSpeed * 0.2F;

   public static float getMovementYaw() {
      return getMovementYaw(ClientAccess.argusclientMinecraft.player.getYRot());
   }

   public static double getJumpBoost() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      return !player.hasEffect(MobEffects.JUMP_BOOST) ? 0.0 : (player.getEffect(MobEffects.JUMP_BOOST).getAmplifier() + 1) * 0.1;
   }

   public static float getMovementYaw(float yaw) {
      double[] motion = getHorizontalMotion(yaw, ClientAccess.argusclientMinecraft.player.input, 1.0);
      return (float)(Math.toDegrees(Math.atan2(motion[1], motion[0])) - 90.0);
   }

   public static boolean isMovingToward(Player player, Vec3 target) {
      Vec3 velocity = player.getDeltaMovement();
      if (!isMoving(player)) {
         return false;
      }

      Vec3 forward = player.position().add(velocity.x() * 10000.0, 0.0, velocity.z() * 10000.0);
      Vec3 backward = player.position().add(velocity.x() * -10000.0, 0.0, velocity.z() * -10000.0);
      return forward.distanceToSqr(target) < backward.distanceToSqr(target);
   }

   public static double applySpeedEffects(boolean includeSlowness, double baseSpeed) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      double speed = baseSpeed;
      if (player.hasEffect(MobEffects.SPEED)) {
         speed *= 1.0 + 0.2 * (player.getEffect(MobEffects.SPEED).getAmplifier() + 1);
      }

      if (includeSlowness && player.hasEffect(MobEffects.SLOWNESS)) {
         speed /= 1.0 + 0.2 * (player.getEffect(MobEffects.SLOWNESS).getAmplifier() + 1);
      }

      return speed;
   }

   public static double[] getHorizontalMotion(float yaw, ClientInput input, double speed) {
      float forward = input.moveVector.y;
      float strafe = input.moveVector.x;
      if (forward != 0.0F) {
         if (strafe > 0.0F) {
            yaw += forward > 0.0F ? -45.0F : 45.0F;
         } else if (strafe < 0.0F) {
            yaw += forward > 0.0F ? 45.0F : -45.0F;
         }

         strafe = 0.0F;
         if (forward > 0.0F) {
            forward = 1.0F;
         } else if (forward < 0.0F) {
            forward = -1.0F;
         }
      }

      double radians = Math.toRadians(yaw);
      double x = forward * speed * -Math.sin(radians) + strafe * speed * Math.cos(radians);
      double z = forward * speed * Math.cos(radians) - strafe * speed * -Math.sin(radians);
      return new double[]{x, z};
   }

   public static double accelerateTo(double target, double initial, double durationSeconds, long startedAtMillis) {
      if (initial >= target) {
         return target;
      }

      double elapsedFraction = System.currentTimeMillis() - startedAtMillis;
      elapsedFraction /= durationSeconds * 1000.0;
      double progress = Mth.clamp(elapsedFraction, 0.0, 1.0);
      double value = initial + (target - initial) * progress;
      return Math.min(value, target);
   }

   public static double[] applyHorizontalMotion(LocalPlayerMoveEvent event, double speed) {
      if (!isLocalPlayerMoving()) {
         event.setHorizontalVelocity(0.0, 0.0);
         return new double[]{0.0, 0.0};
      } else {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         float yaw = player.getViewYRot(RenderUtils.getPartialTick());
         double[] motion = getHorizontalMotion(yaw, player.input, speed);
         event.setHorizontalVelocity(motion[0], motion[1]);
         return motion;
      }
   }

   public static double getHorizontalSpeedKmh(Entity entity) {
      double x = entity.getX() - entity.xo;
      double z = entity.getZ() - entity.zo;
      double speed = Math.hypot(x, z);
      speed *= 20.0;
      return speed * 3.6;
   }

   public static double findStepHeight(double maximumHeight) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (player.onGround() && player.horizontalCollision) {
         AABB probeBounds = player.getBoundingBox().move(0.0, 0.05, 0.0).inflate(0.05);
         probeBounds = probeBounds.setMaxY(probeBounds.maxY + maximumHeight);
         double height = -1.0;

         for (VoxelShape shape : ClientAccess.argusclientMinecraft.level.getCollisions(player, probeBounds)) {
            for (AABB collisionBox : shape.toAabbs()) {
               if (collisionBox.maxY > height) {
                  height = collisionBox.maxY;
               }
            }
         }

         height -= player.getY();
         return height > 0.0 && height <= maximumHeight ? height : 0.0;
      } else {
         return 0.0;
      }
   }

   public static boolean isLocalPlayerMoving() {
      return isMoving(ClientAccess.argusclientMinecraft.player);
   }

   public static boolean isMoving(Player player) {
      if (player == null) {
         return false;
      } else if (player instanceof LocalPlayer localPlayer) {
         Vec2 movement = localPlayer.input.getMoveVector();
         return movement.y != 0.0F || movement.x != 0.0F;
      } else {
         return player.zza != 0.0F || player.xxa != 0.0F;
      }
   }

   public static boolean isMovingToward(Vec3 target) {
      return isMovingToward(ClientAccess.argusclientMinecraft.player, target);
   }

   public static double getBaseMoveSpeed(boolean includeSlowness) {
      return applySpeedEffects(includeSlowness, baseMoveSpeed);
   }
}
