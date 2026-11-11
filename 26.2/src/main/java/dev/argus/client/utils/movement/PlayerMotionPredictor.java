package dev.argus.client.utils.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.entity.EntityUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;

public final class PlayerMotionPredictor implements ClientAccess {
   private static final int PREDICTION_TICKS = 20;
   private static final float BASE_MOVEMENT_SPEED = MovementPhysics.baseMoveSpeed;
   private static final float SNEAKING_MINIMUM_SPEED = BASE_MOVEMENT_SPEED * 0.3F;
   private static final float MAXIMUM_HORIZONTAL_SPEED = BASE_MOVEMENT_SPEED * 1.4F + 0.2F;
   private final Player player;
   private final MotionHistory motionHistory = new MotionHistory(10);
   private final List<AABB> predictedBoxes = new ArrayList<>();
   private final Vector3d motion = new Vector3d(0.0);
   private final Vector3d lastObservedPosition = new Vector3d(0.0);

   public void updatePredictions() {
      synchronized (this.predictedBoxes) {
         this.motion.set(this.player.getX(), this.player.getY(), this.player.getZ());
         if (!this.lastObservedPosition.equals(this.motion)) {
            this.lastObservedPosition.set(this.motion);
            this.predictedBoxes.clear();
            this.motion.sub(this.player.xo, this.player.yo, this.player.zo);
            MotionSample previous = this.motionHistory.getLatest();
            double accelerationX = previous == null ? 0.0 : this.motion.x - previous.motionX();
            double accelerationZ = previous == null ? 0.0 : this.motion.z - previous.motionZ();
            double verticalMotion = this.motion.y;
            this.motionHistory
               .append(
                  new MotionSample(
                     this.motion.x,
                     this.motion.y,
                     this.motion.z,
                     accelerationX,
                     accelerationZ,
                     EntityUtils.hasCollisionBelow(this.player),
                     this.player.isShiftKeyDown(),
                     this.player.isFallFlying()
                  )
               );
            this.motion
               .set(this.motionHistory.weightedAverage(MotionSample::motionX), verticalMotion, this.motionHistory.weightedAverage(MotionSample::motionZ));
            double speedLimit = this.player.isFallFlying() ? 1.8F : MAXIMUM_HORIZONTAL_SPEED;
            double minimumSpeed = this.player.isShiftKeyDown() ? SNEAKING_MINIMUM_SPEED : BASE_MOVEMENT_SPEED;
            double horizontalSpeed = Math.hypot(this.motion.x, this.motion.z);
            if (horizontalSpeed > 1.0E-4) {
               double speed = Mth.clamp(horizontalSpeed, minimumSpeed, speedLimit);
               this.motion.set(this.motion.x / horizontalSpeed * speed, this.motion.y, this.motion.z / horizontalSpeed * speed);
            } else {
               this.motion.set(0.0, this.motion.y, 0.0);
            }

            if (EntityUtils.hasCollisionBelow(this.player) && this.motion.y > 0.0) {
               this.motion.y = -0.0784F;
            }

            AABB bounds = BoundingBoxUtils.getInterpolationBounds(this.player);
            if (horizontalSpeed < 1.0E-4 && Math.abs(this.motion.y) < 0.01) {
               this.predictedBoxes.addAll(Collections.nCopies(20, bounds));
            } else {
               double averageAccelerationX = this.motionHistory.average(MotionSample::accelerationX);
               double averageAccelerationZ = this.motionHistory.average(MotionSample::accelerationZ);

               for (int tick = 0; tick < 20; tick++) {
                  boolean grounded = this.hasBlockCollision(this.player, bounds.move(0.0, -0.01, 0.0));
                  double drag = grounded ? 0.54 : 0.98;
                  double accelerationWeight = Math.max(0.0, 1.0 - tick * 0.15);
                  this.motion
                     .set(
                        (this.motion.x + averageAccelerationX * accelerationWeight) * drag,
                        this.motion.y,
                        (this.motion.z + averageAccelerationZ * accelerationWeight) * drag
                     );
                  double predictedSpeed = Math.hypot(this.motion.x, this.motion.z);
                  if (predictedSpeed > speedLimit) {
                     this.motion.set(this.motion.x / predictedSpeed * speedLimit, this.motion.y, this.motion.z / predictedSpeed * speedLimit);
                  }

                  this.motion.y = grounded ? -0.0784F : this.applyGravityAndDrag(this.motion.y);
                  bounds = this.moveWithCollisions(bounds, this.motion.x, this.motion.y, this.motion.z);
                  this.predictedBoxes.add(bounds);
               }
            }
         }
      }
   }

   public synchronized List<AABB> getPredictedBoxes() {
      return this.predictedBoxes;
   }

   public PlayerMotionPredictor(Player player) {
      this.player = player;
   }

   public void reset() {
      this.motionHistory.clear();
   }

   private static AABB resolveMovement(AABB bounds, List<VoxelShape> collisions, double x, double y, double z) {
      if (collisions.isEmpty()) {
         return bounds.move(x, y, z);
      }

      AABB originalBounds = bounds;
      if (y != 0.0) {
         y = Shapes.collide(Axis.Y, bounds, collisions, y);
         if (y != 0.0) {
            bounds = bounds.move(0.0, y, 0.0);
         }
      }

      boolean zFirst = Math.abs(x) < Math.abs(z);
      if (zFirst && z != 0.0) {
         z = Shapes.collide(Axis.Z, bounds, collisions, z);
         if (z != 0.0) {
            bounds = bounds.move(0.0, 0.0, z);
         }
      }

      if (x != 0.0) {
         x = Shapes.collide(Axis.X, bounds, collisions, x);
         if (!zFirst && x != 0.0) {
            bounds = bounds.move(x, 0.0, 0.0);
         }
      }

      if (!zFirst && z != 0.0) {
         z = Shapes.collide(Axis.Z, bounds, collisions, z);
      }

      return originalBounds.move(x, y, z);
   }

   private boolean hasBlockCollision(Player player, AABB bounds) {
      return ClientAccess.argusclientMinecraft.level.getBlockCollisions(player, bounds).iterator().hasNext();
   }

   private AABB moveWithCollisions(AABB bounds, double x, double y, double z) {
      ArrayList<VoxelShape> collisions = new ArrayList<>(
         ClientAccess.argusclientMinecraft.level.getEntityCollisions(this.player, bounds.expandTowards(x, y, z))
      );
      collisions.addAll(Entity.collectAllColliders(this.player, ClientAccess.argusclientMinecraft.level, bounds.expandTowards(x, y, z)));
      return resolveMovement(bounds, collisions, x, y, z);
   }

   private double applyGravityAndDrag(double verticalMotion) {
      return this.player.isFallFlying() ? verticalMotion : (verticalMotion - 0.08) * 0.98;
   }
}
