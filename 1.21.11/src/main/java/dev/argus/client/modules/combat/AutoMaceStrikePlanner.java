package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.modules.movement.FireworksModule;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

final class AutoMaceStrikePlanner {
   private static final double[] RADII = new double[]{2.5, 4.5, 7.0, 10.0};
   private static final double[] HEIGHTS = new double[]{0.5, 2.0, 4.0};
   private static final double[] CENTER_HEIGHTS = new double[]{5.0, 9.0};
   private final Long2ObjectOpenHashMap<List<AABB>> collisionCache = new Long2ObjectOpenHashMap();
   private final List<Vec3> path = new ArrayList<>();
   private CollisionContext collisionContext;
   private AABB playerBody;
   private AABB targetBox;
   private Vec3 playerPosition;
   private Vec3 initialVelocity;
   private Vec3 position;
   private Vec3 velocity;
   private Vec3 look;
   private Vec3 controlVelocity;
   private double playerX;
   private double playerZ;
   private double targetX;
   private double targetY;
   private double targetZ;
   private double targetMotionX;
   private double targetMotionZ;
   private double attackRange;
   private double eyeHeight;
   private double gravity;
   private double controlSpeed;
   private double verticalSpeed;
   private double glide;
   private double boostBudget;
   private double initialFallDistance;
   private float initialYaw;
   private float initialPitch;
   private float yaw;
   private float pitch;
   private float simulationYaw;
   private float simulationPitch;
   private boolean active;
   private boolean control;
   private boolean usingRocket;
   private boolean rocketActive;
   private boolean descend;
   private boolean simulationDown;
   private boolean selectedDown;
   private int simulationStage;
   private int selectedHitTicks = Integer.MAX_VALUE;
   private float[] selectedRotation;
   private Vec3 selectedOffset;
   private int selectedMode;
   private int refreshTicks;
   private int searchCursor;
   private boolean solution;
   private int simulationCount;
   private double score;
   private boolean limitReached;
   private boolean pathBlocked;
   private int simulationHitTicks;

   void reset() {
      this.active = false;
      this.solution = false;
      this.selectedOffset = null;
      this.selectedMode = 0;
      this.selectedRotation = null;
      this.selectedHitTicks = Integer.MAX_VALUE;
      this.selectedDown = false;
      this.refreshTicks = 0;
      this.searchCursor = 0;
      this.path.clear();
      this.collisionCache.clear();
   }

   boolean isActive() {
      return this.active && this.solution;
   }

   float[] rotation() {
      return this.isActive() ? this.selectedRotation : null;
   }

   int hitTicks() {
      return this.isActive() ? this.selectedHitTicks : Integer.MAX_VALUE;
   }

   boolean shouldDescend() {
      return this.isActive() && this.selectedDown;
   }

   void updateNormal(Entity target, double range, boolean rockets, double budget) {
      this.control = false;
      this.usingRocket = rockets;
      this.boostBudget = budget;
      this.update(target, range);
   }

   void updateControl(Entity target, double range, double speed, double vSpeed, double glideSpeed) {
      this.control = true;
      this.usingRocket = false;
      this.boostBudget = Double.POSITIVE_INFINITY;
      this.controlSpeed = speed;
      this.verticalSpeed = vSpeed;
      this.glide = glideSpeed;
      this.update(target, range);
   }

   private void update(Entity target, double range) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      this.active = true;
      this.collisionCache.clear();
      this.collisionContext = CollisionContext.of(player);
      EntityDimensions dimensions = player.getDimensions(Pose.FALL_FLYING);
      this.playerBody = dimensions.makeBoundingBox(0.0, 0.0, 0.0).inflate(0.1);
      this.eyeHeight = dimensions.eyeHeight();
      this.gravity = player.getGravity();
      this.targetBox = target.getBoundingBox();
      this.targetX = target.getX();
      this.targetY = target.getY();
      this.targetZ = target.getZ();
      this.targetMotionX = this.targetX - target.xo;
      this.targetMotionZ = this.targetZ - target.zo;
      double targetSpeed = Math.sqrt(this.targetMotionX * this.targetMotionX + this.targetMotionZ * this.targetMotionZ);
      if (targetSpeed > 1.0) {
         this.targetMotionX /= targetSpeed;
         this.targetMotionZ /= targetSpeed;
      }

      this.playerPosition = player.position();
      this.playerX = this.playerPosition.x;
      this.playerZ = this.playerPosition.z;
      this.initialVelocity = ClientContext.playerStateTracker.getTickDisplacement();
      this.initialFallDistance = PlayerStateUtils.getTrackedFallDistance();
      float[] rotations = ClientContext.playerStateTracker.getPacketRotations();
      this.initialYaw = rotations[0];
      this.initialPitch = rotations[1];
      this.attackRange = range;
      this.simulationCount = 0;
      this.path.clear();
      boolean previousWorks = this.solution && this.simulate(this.selectedOffset, this.selectedMode, this.path);
      boolean good = previousWorks && this.acceptable();
      boolean finalWorks = previousWorks;
      if (!good && (this.selectedOffset != null || !this.solution)) {
         finalWorks = false;
         if (this.simulate(null, 1, null) && this.acceptable()) {
            this.selectedOffset = null;
            this.selectedMode = 1;
            good = true;
            previousWorks = true;
            this.solution = true;
            this.refreshTicks = this.searchCursor = 0;
         }
      }

      boolean search = false;
      if (previousWorks) {
         if (!good && ++this.refreshTicks >= 10) {
            search = true;
         }
      } else if (!this.solution && this.searchCursor == 0) {
         if (++this.refreshTicks >= 5) {
            search = true;
         }
      } else {
         search = true;
      }

      if (search) {
         this.refreshSearch(previousWorks);
         finalWorks = false;
      }

      if (!finalWorks) {
         this.path.clear();
         finalWorks = this.solution && this.simulate(this.selectedOffset, this.selectedMode, this.path);
      }

      if (finalWorks) {
         this.selectedMode = this.simulationStage;
         this.selectedHitTicks = this.simulationHitTicks;
         this.selectedRotation = new float[]{this.simulationYaw, this.simulationPitch};
         this.selectedDown = this.simulationDown;
      } else {
         this.solution = false;
         this.selectedRotation = null;
         this.selectedHitTicks = Integer.MAX_VALUE;
         this.path.clear();
      }
   }

   private List<Vec3> candidateOffsets() {
      List<Vec3> candidates = new ArrayList<>(194);

      for (double height : CENTER_HEIGHTS) {
         candidates.add(new Vec3(0.0, height, 0.0));
      }

      double bearing = Mth.atan2(this.playerZ - this.targetZ, this.playerX - this.targetX);

      for (int index = 0; index < 16; index++) {
         int offset = (index + 1) / 2 * (index % 2 == 1 ? 1 : -1);
         double angle = bearing + offset * Math.PI * 2.0 / 16.0;

         for (double radius : RADII) {
            for (double height : HEIGHTS) {
               candidates.add(new Vec3(Math.cos(angle) * radius, height, Math.sin(angle) * radius));
            }
         }
      }

      return candidates;
   }

   private List<AABB> collisionBoxes(int x, int y, int z) {
      long key = BlockPos.asLong(x, y, z);
      List<AABB> cached = (List<AABB>)this.collisionCache.get(key);
      if (cached != null) {
         return cached;
      }

      BlockPos position = new BlockPos(x, y, z);
      VoxelShape shape = ClientAccess.argusclientMinecraft
         .level
         .getBlockState(position)
         .getCollisionShape(ClientAccess.argusclientMinecraft.level, position, this.collisionContext);
      List<AABB> boxes = new ArrayList<>();

      for (AABB box : shape.toAabbs()) {
         boxes.add(box.move(x, y, z));
      }

      this.collisionCache.put(key, boxes);
      return boxes;
   }

   private boolean candidateClear(Vec3 offset) {
      AABB bounds = this.playerBody.move(this.targetX + offset.x, this.targetY + offset.y, this.targetZ + offset.z);

      for (int x = Mth.floor(bounds.minX); x < Mth.ceil(bounds.maxX); x++) {
         for (int z = Mth.floor(bounds.minZ); z < Mth.ceil(bounds.maxZ); z++) {
            for (int y = Mth.floor(bounds.minY) - 1; y < Mth.ceil(bounds.maxY); y++) {
               for (AABB box : this.collisionBoxes(x, y, z)) {
                  if (box.intersects(bounds)) {
                     return false;
                  }
               }
            }
         }
      }

      return true;
   }

   private boolean pointClear(double x, double y, double z) {
      int bx = Mth.floor(x);
      int by = Mth.floor(y);
      int bz = Mth.floor(z);

      for (int yy = by - 1; yy <= by; yy++) {
         for (AABB box : this.collisionBoxes(bx, yy, bz)) {
            if (box.intersects(x, y, z, x, y, z)) {
               return false;
            }
         }
      }

      return true;
   }

   private boolean lineClear(Vec3 from, Vec3 to) {
      Vec3 delta = to.subtract(from);
      int steps = Math.max(1, Mth.ceil(delta.length() / 0.25));

      for (int i = 0; i < steps; i++) {
         Vec3 point = from.add(delta.scale((double)i / steps));
         if (!this.pointClear(point.x, point.y, point.z)) {
            return false;
         }
      }

      return true;
   }

   private boolean bodyClear(Vec3 position) {
      AABB bounds = this.playerBody.move(position.x, position.y, position.z);

      for (int x = Mth.floor(bounds.minX); x < Mth.ceil(bounds.maxX); x++) {
         for (int z = Mth.floor(bounds.minZ); z < Mth.ceil(bounds.maxZ); z++) {
            for (int y = Mth.floor(bounds.minY) - 1; y < Mth.ceil(bounds.maxY); y++) {
               for (AABB box : this.collisionBoxes(x, y, z)) {
                  if (box.intersects(bounds)) {
                     return false;
                  }
               }
            }
         }
      }

      return true;
   }

   private boolean movementClear(Vec3 position, Vec3 velocity) {
      int steps = Math.max(1, Mth.ceil(velocity.length() / 0.5));

      for (int i = 1; i <= steps; i++) {
         if (!this.bodyClear(position.add(velocity.scale((double)i / steps)))) {
            return false;
         }
      }

      return true;
   }

   private boolean inAttackRange(Vec3 position, int tick) {
      Vec3 eye = position.add(0.0, this.eyeHeight, 0.0);
      AABB movingTarget = this.targetBox.move(this.targetMotionX * tick, 0.0, this.targetMotionZ * tick);
      Vec3 nearest = BoundingBoxUtils.clampToBounds(eye, movingTarget);
      return eye.distanceToSqr(nearest) <= this.attackRange * this.attackRange && this.lineClear(eye, nearest);
   }

   private boolean followOnHit(Vec3 position, Vec3 velocity, int tick) {
      for (int i = 0; i < 6; i++) {
         if (this.inAttackRange(position, tick + i)) {
            return true;
         }

         if (!this.movementClear(position, velocity)) {
            return false;
         }

         position = position.add(velocity);
         velocity = new Vec3(velocity.x * 0.91F, (velocity.y - this.gravity) * 0.98F, velocity.z * 0.91F);
      }

      return false;
   }

   private void setAim(Vec3 direction, boolean clampPitch) {
      this.yaw = Mth.wrapDegrees((float)(Mth.atan2(direction.z, direction.x) * (180.0 / Math.PI)) - 90.0F);
      this.pitch = (float)(Mth.atan2(-direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)) * (180.0 / Math.PI));
      if (clampPitch) {
         this.pitch = Math.max(0.0F, this.pitch);
      }

      float pitchRadians = this.pitch * (float) (Math.PI / 180.0);
      float yawRadians = -this.yaw * (float) (Math.PI / 180.0);
      float cosPitch = Mth.cos(pitchRadians);
      this.look = new Vec3(Mth.sin(yawRadians) * cosPitch, -Mth.sin(pitchRadians), Mth.cos(yawRadians) * cosPitch);
   }

   private Vec3 elytraVelocity(Vec3 current) {
      float p = this.pitch * (float) (Math.PI / 180.0);
      double cos = Math.cos(p);
      double cosSquared = cos * cos;
      double lookHorizontal = Math.sqrt(this.look.x * this.look.x + this.look.z * this.look.z);
      double horizontal = Math.sqrt(current.x * current.x + current.z * current.z);
      double x = current.x;
      double y = current.y + this.gravity * (-1.0 + cosSquared * 0.75);
      double z = current.z;
      if (lookHorizontal > 0.0) {
         if (y < 0.0) {
            double lift = y * -0.1 * cosSquared;
            x += this.look.x * lift / lookHorizontal;
            y += lift;
            z += this.look.z * lift / lookHorizontal;
         }

         if (p < 0.0F) {
            double lift = horizontal * -Mth.sin(p) * 0.04;
            x -= this.look.x * lift / lookHorizontal;
            y += lift * 3.2;
            z -= this.look.z * lift / lookHorizontal;
         }

         x += (this.look.x / lookHorizontal * horizontal - x) * 0.1;
         z += (this.look.z / lookHorizontal * horizontal - z) * 0.1;
      }

      return new Vec3(x * 0.99F, y * 0.98F, z * 0.99F);
   }

   private void aimAt(Vec3 target, int tick) {
      Vec3 delta = target.subtract(this.position);
      if (this.control) {
         double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
         this.setAim(delta, true);
         double dx = this.targetX + this.targetMotionX * tick - this.position.x;
         double dz = this.targetZ + this.targetMotionZ * tick - this.position.z;
         double targetDistance = Math.sqrt(dx * dx + dz * dz);
         double speed = Math.min(Math.min(this.controlSpeed, targetDistance), 1.0);
         this.descend = delta.y < 0.0 && this.verticalSpeed > 0.0 && (this.descend || horizontal <= -delta.y / this.verticalSpeed * speed);
         this.controlVelocity = new Vec3(
            horizontal < 1.0E-4 ? 0.0 : delta.x / horizontal * speed,
            this.descend ? -this.verticalSpeed : -this.glide,
            horizontal < 1.0E-4 ? 0.0 : delta.z / horizontal * speed
         );
      } else {
         double length = delta.length();
         Vec3 desired = length < 1.0E-4 ? new Vec3(0.0, -1.0, 0.0) : delta.scale(1.0 / length);
         Vec3 adjusted = desired;
         if (this.usingRocket) {
            for (int i = 0; i < 2; i++) {
               this.setAim(adjusted, false);
               Vec3 projectedVelocity = this.elytraVelocity(this.velocity).scale(this.rocketActive ? 1.0 : 0.5);
               Vec3 perpendicular = projectedVelocity.subtract(desired.scale(projectedVelocity.dot(desired)));
               double amount = perpendicular.length();
               double gain = this.rocketActive ? 0.8 : 0.85;
               adjusted = amount >= gain
                  ? perpendicular.scale(-1.0)
                  : desired.scale(Math.sqrt(1.0 - amount * amount / (gain * gain))).subtract(perpendicular.scale(1.0 / gain));
            }
         }

         this.setAim(adjusted, true);
      }
   }

   private boolean simulate(Vec3 offset, int requestedStage, List<Vec3> path) {
      this.position = this.playerPosition;
      this.velocity = this.initialVelocity;
      double fallDistance = this.initialFallDistance;
      this.limitReached = false;
      this.pathBlocked = false;
      this.rocketActive = false;
      float previousYaw = this.initialYaw;
      float previousPitch = this.initialPitch;
      this.descend = this.control && this.velocity.y < -this.glide - 0.01;
      int stage = offset == null ? 1 : requestedStage;
      Vec3 waypoint = offset == null
         ? new Vec3(this.targetX, this.targetY, this.targetZ)
         : new Vec3(this.targetX + offset.x, this.targetY + offset.y, this.targetZ + offset.z);
      if (this.inAttackRange(this.position, 0)) {
         this.aimAt(new Vec3(this.targetX, this.targetY + 0.25, this.targetZ), 0);
         this.simulationStage = 1;
         this.simulationYaw = this.yaw;
         this.simulationPitch = this.pitch;
         this.simulationDown = this.descend;
         this.simulationHitTicks = 0;
         this.score = fallDistance;
         return true;
      }

      for (int tick = 0; tick < 80; tick++) {
         this.simulationCount++;
         double mx = this.targetMotionX * tick;
         double mz = this.targetMotionZ * tick;
         Vec3 movedTarget = new Vec3(this.targetX + mx, this.targetY, this.targetZ + mz);
         this.rocketActive = this.usingRocket && this.position.distanceToSqr(movedTarget) > this.boostBudget * this.boostBudget;
         Vec3 movedWaypoint = waypoint.add(mx, 0.0, mz);
         if (stage == 0 && (this.position.y < waypoint.y || this.position.distanceToSqr(movedWaypoint) <= 9.0)) {
            stage = 1;
         }

         this.aimAt(stage == 0 ? movedWaypoint : movedTarget.add(0.0, 0.25, 0.0), tick);
         if (tick == 0) {
            this.simulationStage = stage;
            this.simulationYaw = this.yaw;
            this.simulationPitch = this.pitch;
            this.simulationDown = this.descend;
         }

         if (this.velocity.y > -0.5 && fallDistance > 1.0) {
            fallDistance = 1.0;
            this.limitReached = true;
         }

         Vec3 previousPosition = this.position;
         Vec3 previousVelocity = this.velocity;
         this.velocity = this.control ? this.controlVelocity : this.elytraVelocity(this.velocity);
         if (this.rocketActive) {
            this.velocity = this.velocity.add(FireworksModule.getGrimBoost(this.yaw, this.pitch, previousYaw, previousPitch, this.velocity));
         }

         previousYaw = this.yaw;
         previousPitch = this.pitch;
         if (!this.movementClear(this.position, this.velocity)) {
            return false;
         }

         this.position = this.position.add(this.velocity);
         if (this.velocity.y < 0.0) {
            fallDistance -= this.velocity.y;
         }

         if (this.usingRocket && !this.rocketActive) {
            this.velocity = this.velocity.add(this.look.scale(0.1)).add(this.look.scale(1.5).subtract(this.velocity).scale(0.5));
         }

         if (path != null) {
            path.add(this.position);
         }

         if (this.inAttackRange(this.position, tick + 1)) {
            this.simulationHitTicks = tick + 1;
            this.score = fallDistance - 0.15 * this.simulationHitTicks;
            if (!this.followOnHit(previousPosition, previousVelocity, tick)) {
               this.score -= 10.0;
               this.pathBlocked = true;
            }

            return true;
         }
      }

      this.simulationHitTicks = Integer.MAX_VALUE;
      this.score = fallDistance - 100.0;
      return true;
   }

   private boolean acceptable() {
      return !this.limitReached && !this.pathBlocked && this.simulationHitTicks != Integer.MAX_VALUE;
   }

   private void refreshSearch(boolean previousStillWorks) {
      this.refreshTicks = 0;
      List<Vec3> candidates = this.candidateOffsets();
      boolean found = false;
      Vec3 bestOffset = null;
      int bestMode = 0;
      double bestScore = 0.0;
      if (previousStillWorks && this.simulate(this.selectedOffset, this.selectedMode, null)) {
         found = true;
         bestOffset = this.selectedOffset;
         bestMode = this.selectedMode;
         bestScore = this.score + 1.0;
      }

      if (this.simulate(null, 1, null) && (!found || this.score > bestScore)) {
         found = true;
         bestOffset = null;
         bestMode = 1;
         bestScore = this.score;
      }

      int start = this.searchCursor;
      this.searchCursor = 0;

      for (int i = 0; i < candidates.size(); i++) {
         if (this.simulationCount >= 3000) {
            this.searchCursor = (start + i) % candidates.size();
            break;
         }

         Vec3 offset = candidates.get((start + i) % candidates.size());
         if (this.candidateClear(offset)
            && this.lineClear(new Vec3(this.targetX + offset.x, this.targetY + offset.y + this.eyeHeight, this.targetZ + offset.z), this.targetBox.getCenter())
            && this.simulate(offset, 0, null)
            && (!found || this.score > bestScore)) {
            found = true;
            bestOffset = offset;
            bestMode = 0;
            bestScore = this.score;
            if (this.acceptable()) {
               break;
            }
         }
      }

      this.solution = found;
      if (found && (bestOffset != this.selectedOffset || bestMode != this.selectedMode)) {
         this.selectedOffset = bestOffset;
         this.selectedMode = bestMode;
      }
   }
}
