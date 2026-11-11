package dev.argus.client.utils.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.commands.FakeplayerCommand;
import dev.argus.client.settings.Setting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BoundingBoxUtils implements ClientAccess {
   public static double blockCornerInset = 0.0625;

   public static AABB getBlockShapeBounds(BlockPos position) {
      BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(position);
      if (state.isAir()) {
         return new AABB(position);
      }

      Block airBlock = Blocks.AIR;
      if (state.is(airBlock)) {
         return new AABB(position);
      }

      VoxelShape shape = state.getShape(ClientAccess.argusclientMinecraft.level, position);
      if (shape.isEmpty()) {
         shape = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
      }

      AABB bounds = shape.bounds();
      return bounds.move(position);
   }

   private BoundingBoxUtils() {
   }

   public static boolean hasUnitDimensions(AABB bounds) {
      return bounds.getZsize() == 1.0 && bounds.getYsize() == 1.0 && bounds.getXsize() == 1.0;
   }

   public static List getCornersByDistance(AABB bounds, double y) {
      List<Vec3> corners = new ArrayList<>(
         List.of(
            new Vec3(bounds.minX, y, bounds.minZ),
            new Vec3(bounds.maxX, y, bounds.minZ),
            new Vec3(bounds.minX, y, bounds.maxZ),
            new Vec3(bounds.maxX, y, bounds.maxZ)
         )
      );
      corners.sort(Comparator.comparingDouble(corner -> ClientAccess.argusclientMinecraft.player.getEyePosition().distanceToSqr(corner)));
      return corners;
   }

   public static int getPredictionTicks(Setting<Integer> ticksSetting) {
      return ticksSetting.getValue();
   }

   public static AABB recenter(AABB bounds, Vec3 center) {
      double halfX = bounds.getXsize() * 0.5;
      double halfY = bounds.getYsize() * 0.5;
      double halfZ = bounds.getZsize() * 0.5;
      return new AABB(center.x - halfX, center.y - halfY, center.z - halfZ, center.x + halfX, center.y + halfY, center.z + halfZ);
   }

   public static AABB getBlockCollisionBounds(BlockPos position, Block block) {
      BlockState collisionState = block.defaultBlockState();
      VoxelShape shape = collisionState.getCollisionShape(ClientAccess.argusclientMinecraft.level, position);
      if (shape.isEmpty()) {
         shape = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
      }

      AABB bounds = shape.bounds();
      return bounds.move(position);
   }

   public static AABB placeAtBlock(AABB bounds, BlockPos position) {
      Vec3 center = new Vec3(position.getX() + 0.5, position.getY() + bounds.getYsize() * 0.5, position.getZ() + 0.5);
      return recenter(bounds, center);
   }

   public static Vec3 getBottomCenter(AABB bounds) {
      return new Vec3(Mth.lerp(0.5, bounds.minX, bounds.maxX), bounds.minY, Mth.lerp(0.5, bounds.minZ, bounds.maxZ));
   }

   public static boolean hasUnitDimensions(VoxelShape shape) {
      if (shape.isEmpty()) {
         return false;
      }

      AABB boundsSnapshot = shape.bounds();
      return hasUnitDimensions(boundsSnapshot);
   }

   public static AABB getRenderBounds(Entity entity, float partialTick) {
      double fraction = partialTick;
      double previousX = entity.xOld;
      double interpolatedX = Mth.lerp(fraction, previousX, entity.getX());
      double offsetX = interpolatedX - entity.getX();
      double previousY = entity.yOld;
      double interpolatedY = Mth.lerp(fraction, previousY, entity.getY());
      double offsetY = interpolatedY - entity.getY();
      double previousZ = entity.zOld;
      double interpolatedZ = Mth.lerp(fraction, previousZ, entity.getZ());
      double offsetZ = interpolatedZ - entity.getZ();
      return entity.getBoundingBox().move(offsetX, offsetY, offsetZ);
   }

   public static boolean hasVolumeOverlap(AABB first, AABB second) {
      if (!first.intersects(second)) {
         return false;
      }

      AABB intersection = first.intersect(second);
      double width = intersection.getXsize();
      double height = intersection.getYsize();
      double depth = intersection.getZsize();
      double minimumHeightDepth = Math.min(height, depth);
      double minimumDimension = (float)Math.min(width, minimumHeightDepth);
      return minimumDimension > 1.0E-7;
   }

   public static Vec3 getLowerQuarterCenter(AABB bounds) {
      Vec3 bottomCenter = getBottomCenter(bounds);
      double height = bounds.getYsize();
      double quarterHeight = height * 0.25;
      return bottomCenter.add(0.0, quarterHeight, 0.0);
   }

   public static Vec3 clampToBounds(Vec3 point, AABB bounds) {
      return new Vec3(
         Mth.clamp(point.x(), bounds.minX, bounds.maxX), Mth.clamp(point.y(), bounds.minY, bounds.maxY), Mth.clamp(point.z(), bounds.minZ, bounds.maxZ)
      );
   }

   public static boolean intersectsBlock(AABB bounds, BlockPos position) {
      double minX = position.getX();
      double minY = position.getY();
      double minZ = position.getZ();
      double maxX = position.getX() + 1;
      double maxY = position.getY() + 1;
      double maxZ = position.getZ() + 1;
      return bounds.intersects(minX, minY, minZ, maxX, maxY, maxZ);
   }

   public static Vec3 getTopCenter(AABB bounds) {
      return new Vec3(Mth.lerp(0.5, bounds.minX, bounds.maxX), bounds.maxY, Mth.lerp(0.5, bounds.minZ, bounds.maxZ));
   }

   public static AABB getInterpolationBounds(LivingEntity entity) {
      if (entity instanceof FakeplayerCommand.SimulatedPlayer) {
         return entity.getBoundingBox();
      }

      AABB bounds = entity.getBoundingBox();
      InterpolationHandler interpolation = entity.getInterpolation();
      PositionAndRotation target = interpolation.target();
      Vec3 position = target == null ? Vec3.ZERO : target.position();
      if (position.lengthSqr() == 0.0) {
         return entity.getBoundingBox();
      }

      double width = bounds.getXsize();
      double halfX = width / 2.0;
      double halfZ = bounds.getZsize() / 2.0;
      double minX = -halfX;
      double minZ = -halfZ;
      AABB centeredBounds = new AABB(minX, 0.0, minZ, halfX, bounds.getYsize(), halfZ);
      return centeredBounds.move(position);
   }
}
