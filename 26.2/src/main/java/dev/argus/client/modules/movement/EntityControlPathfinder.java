package dev.argus.client.modules.movement;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.event.events.BlockChangeEvent;
import baritone.api.event.events.ChunkEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.pathing.goals.GoalYLevel;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.interfaces.IGoalRenderPos;
import dev.argus.client.ClientAccess;
import dev.argus.client.utils.chat.ChatUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class EntityControlPathfinder implements ClientAccess, AbstractGameEventListener, IBaritoneProcess {
   private final EntityControlModule module;
   private final IBaritone baritone;
   private EntityControlPathContext context;
   private CompletableFuture<EntityControlPathContext.Route> calculation;
   private Goal goal;
   private List<Vec3> route = List.of();
   private boolean pathFinished;
   private int routeIndex;
   private int shortcutDelay;
   private boolean blocksChanged;
   private final Set<BlockPos> blockedPositions = new HashSet<>();
   private int retryTicks;
   private boolean warnedNoRoute;
   private boolean warnedUnsupported;
   private Vec3 lastPosition;
   private int stuckTicks;
   private Vec3 lastRepathPosition;
   private int repeatedRepaths;
   private Vec3 motion = Vec3.ZERO;
   private int motionTick = -1;

   EntityControlPathfinder(EntityControlModule module) {
      this.module = module;
      this.baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      this.baritone.getPathingControlManager().registerProcess(this);
      this.baritone.getGameEventHandler().registerEventListener(this);
   }

   Vec3 getMotion() {
      return argusclientMinecraft.player != null && argusclientMinecraft.player.tickCount == this.motionTick ? this.motion : null;
   }

   public boolean isActive() {
      Entity vehicle = this.getVehicle();
      Goal activeGoal = this.getGoal();
      if (vehicle == null || activeGoal == null || target(activeGoal, vehicle.position()) == null) {
         return false;
      } else if (!EntityControlPathContext.isSupported()) {
         this.warnUnsupported("This Baritone version doesn't contain a supported elytra pathfinder.");
         return false;
      } else if (!isBelowNetherRoof(vehicle)) {
         this.warnUnsupported("This Baritone version pathfinder works only in Nether.");
         return false;
      } else {
         return true;
      }
   }

   public PathingCommand onTick(boolean calculationFailed, boolean safeToCancel) {
      Entity vehicle = this.getVehicle();
      Goal activeGoal = this.getGoal();
      Vec3 position = vehicle == null ? null : vehicle.position();
      Vec3 destination = position != null && activeGoal != null ? target(activeGoal, position) : null;
      if (destination == null) {
         return new PathingCommand(null, PathingCommandType.DEFER);
      }

      if (!activeGoal.equals(this.goal)) {
         this.reset();
         this.goal = activeGoal;
         this.message("Going to " + BlockPos.containing(destination).toShortString() + ".");
      }

      if (isAtGoal(activeGoal, vehicle, position, destination)) {
         this.message("Done :^)");
         return this.finish();
      }

      EntityControlPathContext pathContext = this.getContext(vehicle);
      if (pathContext == null) {
         this.warning("Failed to start the pathfinder.", ChatUtils.MessagePriority.HIGH);
         return this.finish();
      }

      AABB bounds = relativeBounds(vehicle);
      if (this.calculation != null && this.calculation.isDone()) {
         EntityControlPathContext.Route result = this.calculation.getNow(null);
         this.calculation = null;
         if (result != null && !result.nodes().isEmpty()) {
            this.warnedNoRoute = false;
            this.route = routePoints(result.nodes(), bounds);
            this.pathFinished = result.finished();
            this.shortcutDelay = 0;
            this.stuckTicks = 0;
            Vec3 last = this.route.getLast();
            if (this.pathFinished && last.distanceTo(destination) <= 16.0 && isLoaded(destination) && canTraverse(vehicle, last, destination)) {
               this.route.add(destination);
            } else if (this.pathFinished && last.distanceTo(position) < 1.5) {
               this.message("Stopped " + Math.round(position.distanceTo(destination)) + "m away, impossible to reach the destination.");
               return this.finish();
            }

            this.routeIndex = this.nearestReachablePoint(vehicle, position);
         } else {
            if (!this.warnedNoRoute) {
               this.warning("No route found yet, retrying.", ChatUtils.MessagePriority.MID);
            }

            this.warnedNoRoute = true;
            this.retryTicks = 40;
         }
      }

      Vec3 next = this.nextTarget(vehicle, position);
      boolean stuck = this.updateStuck(position, next != null);
      if (this.retryTicks > 0) {
         this.retryTicks--;
      }

      if (this.calculation == null
         && this.retryTicks == 0
         && (next == null || stuck || this.routeChanged(bounds) || !this.pathFinished && this.route.getLast().distanceTo(position) < 64.0)) {
         if (stuck) {
            this.markObstruction(pathContext, position, bounds, next);
         }

         this.calculation = pathContext.findPath(
            BlockPos.containing(position.add(bounds.getCenter())), BlockPos.containing(destination.add(bounds.getCenter()))
         );
      }

      this.motion = this.calculateMotion(vehicle, position, bounds, next);
      this.motionTick = argusclientMinecraft.player.tickCount;
      return new PathingCommand(activeGoal, PathingCommandType.SET_GOAL_AND_PAUSE);
   }

   public boolean isTemporary() {
      return true;
   }

   public void onLostControl() {
      this.reset();
      this.goal = null;
      this.motion = Vec3.ZERO;
      this.motionTick = -1;
   }

   public double priority() {
      return 100.0;
   }

   public String displayName0() {
      return "EntityControl to " + this.goal;
   }

   public void onChunkEvent(ChunkEvent event) {
      if (this.context != null && this.context.level == argusclientMinecraft.level && event.isPostPopulate()) {
         this.context.loadChunk(event.getX(), event.getZ());
         this.blocksChanged = true;
      }
   }

   public void onBlockChange(BlockChangeEvent event) {
      if (this.context != null && this.context.level == argusclientMinecraft.level) {
         this.context.onBlockChange(event);
         this.blocksChanged = true;
      }
   }

   private void message(String message) {
      ChatUtils.printChat(Component.literal(message), ChatUtils.moduleSignature(this.module));
   }

   private void warning(String message, ChatUtils.MessagePriority priority) {
      ChatUtils.printChat(Component.literal(message), ChatUtils.moduleSignature(this.module), priority);
   }

   private void warnUnsupported(String message) {
      if (!this.warnedUnsupported) {
         this.warning(message, ChatUtils.MessagePriority.HIGH);
      }

      this.warnedUnsupported = true;
   }

   private PathingCommand finish() {
      this.reset();
      this.motion = Vec3.ZERO;
      this.motionTick = argusclientMinecraft.player.tickCount;
      this.baritone.getCustomGoalProcess().onLostControl();
      return new PathingCommand(this.goal, PathingCommandType.SET_GOAL_AND_PAUSE);
   }

   private void reset() {
      if (this.calculation != null && this.context != null) {
         this.context.cancel();
      }

      this.calculation = null;
      this.route = List.of();
      this.routeIndex = 0;
      this.blocksChanged = false;
      this.blockedPositions.clear();
      this.retryTicks = 0;
      this.warnedNoRoute = false;
      this.lastPosition = null;
      this.stuckTicks = 0;
      this.lastRepathPosition = null;
      this.repeatedRepaths = 0;
   }

   private EntityControlPathContext getContext(Entity vehicle) {
      int height = isBelowNetherRoof(vehicle) ? 128 : Math.min(argusclientMinecraft.level.getHeight(), 128);
      if (this.context != null && (this.context.level != argusclientMinecraft.level || this.context.height != height)) {
         this.context.close();
         this.context = null;
         this.calculation = null;
      }

      if (this.context == null) {
         EntityControlPathContext created = new EntityControlPathContext(argusclientMinecraft.level, height);
         if (!created.isValid()) {
            created.close();
            return null;
         }

         this.context = created;
         this.context.loadChunks(vehicle.chunkPosition());
      }

      this.context.cullChunks(vehicle.chunkPosition());
      return this.context;
   }

   private void markObstruction(EntityControlPathContext pathContext, Vec3 position, AABB bounds, Vec3 next) {
      this.repeatedRepaths = this.lastRepathPosition != null && position.distanceTo(this.lastRepathPosition) < 8.0 ? this.repeatedRepaths + 1 : 1;
      this.lastRepathPosition = position;
      this.stuckTicks = 0;
      if (this.repeatedRepaths >= 2) {
         BlockPos obstruction = firstBlock(
            bounds,
            position,
            position.add(this.motionToTarget(position, next)),
            block -> !argusclientMinecraft.level.getBlockState(block).getCollisionShape(argusclientMinecraft.level, block).isEmpty()
         );
         if (obstruction != null) {
            pathContext.markBlocked(obstruction, 1);
         }
      }
   }

   private boolean routeChanged(AABB bounds) {
      if (this.blocksChanged && argusclientMinecraft.player.tickCount % 5 == 0) {
         this.blocksChanged = false;
         AABB contracted = bounds.deflate(0.05);

         for (int index = Math.max(this.routeIndex - 1, 0);
            index + 1 < Math.min(this.route.size(), this.routeIndex + 400) && isLoaded(this.route.get(index + 1));
            index++
         ) {
            BlockPos obstruction = firstBlock(
               contracted, this.route.get(index), this.route.get(index + 1), block -> !argusclientMinecraft.level.getBlockState(block).isAir()
            );
            if (obstruction != null && this.blockedPositions.add(obstruction)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private int nearestReachablePoint(Entity vehicle, Vec3 position) {
      List<Integer> closest = IntStream.range(0, this.route.size())
         .boxed()
         .sorted(Comparator.comparingDouble(index -> this.route.get(index).distanceToSqr(position)))
         .toList();
      return closest.stream().limit(12L).filter(index -> canTraverse(vehicle, position, this.route.get(index))).findFirst().orElse(closest.getFirst());
   }

   private Vec3 nextTarget(Entity vehicle, Vec3 position) {
      while (this.routeIndex < this.route.size() && isNear(this.route.get(this.routeIndex), position)) {
         this.routeIndex++;
      }

      if (this.routeIndex >= this.route.size()) {
         return null;
      }

      if (this.shortcutDelay-- <= 0) {
         this.shortcutDelay = 4;
         this.advanceTarget(vehicle, position, this.routeIndex + 1);
      }

      return this.route.get(this.routeIndex);
   }

   private boolean advanceTarget(Entity vehicle, Vec3 position, int minimum) {
      for (int index = Math.min(this.routeIndex + 8, this.route.size() - 1); index >= minimum; index--) {
         Vec3 point = this.route.get(index);
         if (isLoaded(point) && canTraverse(vehicle, position, point)) {
            this.routeIndex = index;
            return true;
         }
      }

      return false;
   }

   private boolean updateStuck(Vec3 position, boolean hasTarget) {
      this.stuckTicks = hasTarget && this.lastPosition != null && position.distanceToSqr(this.lastPosition) < 0.0025 ? this.stuckTicks + 1 : 0;
      this.lastPosition = position;
      return this.stuckTicks >= 20;
   }

   private Vec3 calculateMotion(Entity vehicle, Vec3 position, AABB bounds, Vec3 next) {
      if (!argusclientMinecraft.level.noBlockCollision(vehicle, bounds.move(position))) {
         return escapeMotion(vehicle, position, bounds);
      }

      boolean escapeHazard = vehicle.isInLava() || isHazard(bounds.move(position));
      Vec3 result = this.tryMotion(vehicle, position, bounds, next, escapeHazard);
      if (result == null && next != null && this.advanceTarget(vehicle, position, Math.max(0, this.routeIndex - 8))) {
         result = this.tryMotion(vehicle, position, bounds, this.route.get(this.routeIndex), escapeHazard);
      }

      return result == null ? Vec3.ZERO : result;
   }

   private Vec3 tryMotion(Entity vehicle, Vec3 position, AABB bounds, Vec3 next, boolean escapeHazard) {
      Vec3 desired = next == null ? Vec3.ZERO : this.motionToTarget(position, next);
      List<Vec3> candidates = new ArrayList<>(
         List.of(desired, desired.scale(0.5), desired.scale(0.25), new Vec3(desired.x, 0.0, desired.z), new Vec3(0.0, desired.y, 0.0))
      );
      if (escapeHazard) {
         candidates.addFirst(new Vec3(0.0, Mth.clamp(this.module.getEntityPathVerticalSpeed(), 0.1, 1.0), 0.0));
      }

      return candidates.stream().filter(candidate -> allowsMotion(vehicle, position, bounds, candidate, escapeHazard)).findFirst().orElse(null);
   }

   private static Vec3 escapeMotion(Entity vehicle, Vec3 position, AABB bounds) {
      for (double distance : new double[]{0.25, 0.5, 1.0, 2.0}) {
         for (Direction direction : Direction.values()) {
            Vec3 motion = direction.getUnitVec3().scale(distance);
            AABB moved = bounds.move(position.add(motion));
            if (argusclientMinecraft.level.noBlockCollision(vehicle, moved) && !isHazard(moved)) {
               return motion;
            }
         }
      }

      return Vec3.ZERO;
   }

   private static boolean allowsMotion(Entity vehicle, Vec3 position, AABB bounds, Vec3 motion, boolean escapeHazard) {
      if (motion.lengthSqr() < 1.0E-6) {
         return true;
      } else {
         return !escapeHazard && motion.y < 0.0 && isHazard(bounds.move(position.add(motion)).expandTowards(0.0, -2.0, 0.0))
            ? false
            : firstCollision(vehicle, bounds, position, position.add(motion), !escapeHazard) == null;
      }
   }

   private static boolean canTraverse(Entity vehicle, Vec3 from, Vec3 to) {
      return firstCollision(vehicle, relativeBounds(vehicle), from, to, true) == null;
   }

   private static BlockPos firstBlock(AABB bounds, Vec3 from, Vec3 to, Predicate<BlockPos> predicate) {
      int steps = Math.max(1, Mth.ceil(from.distanceTo(to) / 0.5));

      for (int step = 0; step <= steps; step++) {
         AABB moved = bounds.move(from.lerp(to, (double)step / steps));

         for (BlockPos position : BlockPos.betweenClosed(
            BlockPos.containing(moved.minX, moved.minY, moved.minZ), BlockPos.containing(moved.maxX, moved.maxY, moved.maxZ)
         )) {
            if (predicate.test(position)) {
               return position.immutable();
            }
         }
      }

      return null;
   }

   private static AABB firstCollision(Entity vehicle, AABB bounds, Vec3 from, Vec3 to, boolean avoidHazards) {
      int steps = Math.max(1, Mth.ceil(from.distanceTo(to) / 0.25));

      for (int step = 0; step <= steps; step++) {
         AABB moved = bounds.move(from.lerp(to, (double)step / steps));
         if (!argusclientMinecraft.level.noBlockCollision(vehicle, moved) || avoidHazards && isHazard(moved)) {
            return moved;
         }
      }

      return null;
   }

   private Vec3 motionToTarget(Vec3 from, Vec3 to) {
      Vec3 direction = to.subtract(from);
      double horizontal = Math.hypot(direction.x, direction.z);
      double speed = this.module.getEntityPathSpeed();
      double scale = horizontal > speed ? speed / horizontal : 1.0;
      double vertical = Math.max(this.module.getEntityPathVerticalSpeed(), 0.1);
      if (Math.abs(direction.y) * scale > vertical) {
         scale = vertical / Math.abs(direction.y);
      }

      return direction.scale(scale);
   }

   private static List<Vec3> routePoints(List<EntityControlPathContext.Node> nodes, AABB bounds) {
      Vec3 offset = bounds.getCenter();
      List<Vec3> points = new ArrayList<>();
      AABB previous = null;

      for (EntityControlPathContext.Node node : nodes) {
         AABB box = node.bounds();
         if (previous != null) {
            addPortal(points, previous, box, bounds);
         }

         points.add(box.getCenter().subtract(offset));
         previous = box;
      }

      return points;
   }

   private static void addPortal(List<Vec3> points, AABB from, AABB to, AABB bounds) {
      Axis touching = null;

      for (Axis axis : Axis.values()) {
         double overlap = overlap(from, to, axis);
         if (overlap < -1.0E-6) {
            return;
         }

         if (overlap < 1.0E-6) {
            if (touching != null) {
               return;
            }

            touching = axis;
         }
      }

      if (touching != null) {
         Vec3 half = new Vec3(bounds.getXsize() / 2.0, bounds.getYsize() / 2.0, bounds.getZsize() / 2.0).add(0.1);

         for (Axis axis : Axis.values()) {
            if (axis != touching && overlap(from, to, axis) < 2.0 * half.get(axis)) {
               return;
            }
         }

         double width = 2.0 * half.get(touching);
         if (!(from.max(touching) - from.min(touching) < width) && !(to.max(touching) - to.min(touching) < width)) {
            double sign = Math.signum(to.getCenter().get(touching) - from.getCenter().get(touching));
            Vec3 center = from.intersect(to).getCenter();

            for (int side = -1; side <= 1; side += 2) {
               points.add(center.with(touching, center.get(touching) + side * sign * half.get(touching)).subtract(bounds.getCenter()));
            }
         }
      }
   }

   private static double overlap(AABB first, AABB second, Axis axis) {
      return Math.min(first.max(axis), second.max(axis)) - Math.max(first.min(axis), second.min(axis));
   }

   private static boolean isNear(Vec3 first, Vec3 second) {
      return Math.hypot(first.x - second.x, first.z - second.z) < 0.3 && Math.abs(first.y - second.y) < 0.3;
   }

   private static boolean isLoaded(Vec3 position) {
      return argusclientMinecraft.level.getChunkSource().hasChunk(Mth.floor(position.x) >> 4, Mth.floor(position.z) >> 4);
   }

   private static boolean isHazard(AABB bounds) {
      return argusclientMinecraft.level.getBlockStates(bounds).anyMatch(EntityControlPathContext::isHazard);
   }

   private static boolean isBelowNetherRoof(Entity vehicle) {
      return argusclientMinecraft.level.dimension() == Level.NETHER && vehicle.getY() < 128.0;
   }

   private static AABB relativeBounds(Entity vehicle) {
      return vehicle.getBoundingBox().minmax(argusclientMinecraft.player.getBoundingBox()).move(vehicle.position().reverse());
   }

   private static boolean isAtGoal(Goal goal, Entity vehicle, Vec3 position, Vec3 destination) {
      if (goal instanceof GoalXZ) {
         return Math.hypot(destination.x - position.x, destination.z - position.z) < 1.0;
      } else {
         return goal instanceof GoalYLevel
            ? Math.abs(destination.y - position.y) < 0.5
            : position.distanceTo(destination) < 1.0 || goal.isInGoal(vehicle.blockPosition());
      }
   }

   private static Vec3 target(Goal goal, Vec3 position) {
      if (goal instanceof IGoalRenderPos rendered) {
         return Vec3.atBottomCenterOf(rendered.getGoalPos());
      } else if (goal instanceof GoalXZ horizontal) {
         int x = horizontal.getX();
         int z = horizontal.getZ();
         boolean surface = !argusclientMinecraft.level.dimensionType().hasCeiling() && argusclientMinecraft.level.getChunkSource().hasChunk(x >> 4, z >> 4);
         return new Vec3(x + 0.5, surface ? argusclientMinecraft.level.getHeight(Types.MOTION_BLOCKING, x, z) : position.y, z + 0.5);
      } else if (goal instanceof GoalYLevel vertical) {
         return new Vec3(position.x, vertical.level, position.z);
      } else if (!(goal instanceof GoalComposite composite)) {
         return null;
      } else {
         Vec3 nearest = null;

         for (Goal child : composite.goals()) {
            Vec3 candidate = target(child, position);
            if (candidate != null && (nearest == null || candidate.distanceToSqr(position) < nearest.distanceToSqr(position))) {
               nearest = candidate;
            }
         }

         return nearest;
      }
   }

   private Goal getGoal() {
      ICustomGoalProcess process = this.baritone.getCustomGoalProcess();
      return process.isActive() ? process.getGoal() : null;
   }

   private Entity getVehicle() {
      if (argusclientMinecraft.player != null && argusclientMinecraft.level != null && this.module.isEntityPathfindingEnabled()) {
         Entity vehicle = argusclientMinecraft.player.getVehicle();
         return vehicle != null && vehicle.getControllingPassenger() == argusclientMinecraft.player ? vehicle : null;
      } else {
         return null;
      }
   }
}
