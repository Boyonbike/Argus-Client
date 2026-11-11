package dev.argus.client.utils.math;

import dev.argus.client.ClientAccess;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.render.RenderUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class MathUtils implements ClientAccess {
   public static double distanceToBlockCenter(BlockPos blockPosition) {
      Vec3 blockCenter = Vec3.atCenterOf(blockPosition);
      return distanceToCamera(blockCenter);
   }

   public static Vec3 getPositionDelta(Entity entity) {
      double deltaX = entity.getX() - entity.xo;
      double deltaY = entity.getY() - entity.yo;
      double deltaZ = entity.getZ() - entity.zo;
      return new Vec3(deltaX, deltaY, deltaZ);
   }

   private MathUtils() {
   }

   public static float cameraRotationDifference(Vec3 targetPosition) {
      GameRenderer renderer = ClientAccess.argusclientMinecraft.gameRenderer;
      Camera camera = renderer.mainCamera();
      float[] targetRotations = RotationManager.getRotationsToPosition(targetPosition);
      float cameraYaw = camera.yaw();
      float targetYaw = targetRotations[0];
      float yawDifference = Mth.degreesDifferenceAbs(cameraYaw, targetYaw);
      float cameraPitch = camera.xRot();
      float targetPitch = targetRotations[1];
      float pitchDifference = Mth.degreesDifferenceAbs(cameraPitch, targetPitch);
      return (float)Math.hypot(yawDifference, pitchDifference);
   }

   public static float toRadians(float angle) {
      return angle * (float) (Math.PI / 180.0);
   }

   public static boolean passesRandomThreshold(int threshold) {
      return threshold != 0 && new Random().nextInt(100) <= threshold;
   }

   public static double floorToDecimals(double value, int decimalPlaces) {
      if (decimalPlaces < 0) {
         throw new IllegalArgumentException();
      } else {
         return BigDecimal.valueOf(value).setScale(decimalPlaces, RoundingMode.FLOOR).doubleValue();
      }
   }

   public static float randomFloat(float minimum, float maximum) {
      Random random = new Random();
      return minimum + random.nextFloat() * (maximum - minimum);
   }

   public static float rangeProgress(float value, float lowerBound, float upperBound) {
      if (lowerBound > upperBound) {
         float previousUpperBound = upperBound;
         upperBound = lowerBound;
         lowerBound = previousUpperBound;
      }

      if (lowerBound == upperBound) {
         return value >= upperBound ? 1.0F : 0.0F;
      } else if (value < lowerBound) {
         return 0.0F;
      } else {
         return value > upperBound ? 1.0F : (value - lowerBound) / (upperBound - lowerBound);
      }
   }

   public static double interpolateFrame(double start, double end) {
      double frameDelta = RenderUtils.getPartialTick();
      return Mth.lerp(frameDelta, start, end);
   }

   public static double getDistanceScale(Vec3 origin, Vec3 targetPosition, double scale) {
      double distance = origin.distanceTo(targetPosition);
      Options clientOptions = ClientAccess.argusclientMinecraft.options;
      OptionInstance fovOption = clientOptions.fov();
      double scaleWorkValue = ((Integer)fovOption.get()).intValue();
      double fovScale = scaleWorkValue / 110.0;
      double cappedScale = Math.min(1.0, scale);
      if (fovScale < 1.0) {
         fovScale = 1.0;
      }

      if (distance <= 10.0 * (scale > 1.0 ? 1.0 / scale : scale)) {
         return cappedScale * fovScale;
      }

      scaleWorkValue = distance * scale * fovScale;
      return scaleWorkValue * 0.1 / cappedScale;
   }

   private static Vec3 faceCorner(double x, double y, double z, Axis firstAxis, Axis secondAxis, int firstOffset, int secondOffset) {
      return new Vec3(
         x + (firstAxis == Axis.X ? firstOffset : 0) + (secondAxis == Axis.X ? secondOffset : 0),
         y + (firstAxis == Axis.Y ? firstOffset : 0) + (secondAxis == Axis.Y ? secondOffset : 0),
         z + (firstAxis == Axis.Z ? firstOffset : 0) + (secondAxis == Axis.Z ? secondOffset : 0)
      );
   }

   public static double lerp(double start, double end, double delta) {
      return start + (end - start) * delta;
   }

   public static float floorToDecimals(float value, int decimalPlaces) {
      if (decimalPlaces < 0) {
         throw new IllegalArgumentException();
      } else {
         return BigDecimal.valueOf(value).setScale(decimalPlaces, RoundingMode.FLOOR).floatValue();
      }
   }

   public static double getDistanceScale(Vec3 targetPosition, double scale) {
      GameRenderer renderer = ClientAccess.argusclientMinecraft.gameRenderer;
      Camera camera = renderer.mainCamera();
      Vec3 cameraPosition = camera.position();
      return getDistanceScale(cameraPosition, targetPosition, scale);
   }

   public static int randomInt(int minimum, int maximum) {
      float minimumAsFloat = minimum;
      Random random = new Random();
      return (int)(minimumAsFloat + random.nextFloat() * (maximum - minimum));
   }

   public static double nearestBound(double value, double lowerBound, double upperBound) {
      double distanceFromLower = value - lowerBound;
      double distanceFromUpper = upperBound - value;
      return distanceFromUpper > distanceFromLower ? lowerBound : upperBound;
   }

   public static Vec3 directionFromPitchYaw(float pitch, float yaw) {
      float scaledPitch = toRadians(pitch);
      float negatedYaw = -yaw;
      float scaledNegatedYaw = toRadians(negatedYaw);
      float cosineYaw = Mth.cos(scaledNegatedYaw);
      float sineYaw = Mth.sin(scaledNegatedYaw);
      float cosinePitch = Mth.cos(scaledPitch);
      float sinePitch = Mth.sin(scaledPitch);
      return new Vec3(sineYaw * cosinePitch, -sinePitch, cosineYaw * cosinePitch);
   }

   public static double distanceToCamera(Vec3 targetPosition) {
      GameRenderer renderer = ClientAccess.argusclientMinecraft.gameRenderer;
      Camera camera = renderer.mainCamera();
      Vec3 cameraPosition = camera.position();
      return cameraPosition.distanceTo(targetPosition);
   }

   public static List<Vec3> faceCorners(BlockPos blockPosition, Direction direction) {
      Axis faceAxis = direction.getAxis();
      double x = blockPosition.getX();
      double y = blockPosition.getY();
      double z = blockPosition.getZ();
      double faceOffset;
      if (direction.getAxisDirection() == AxisDirection.POSITIVE) {
         faceOffset = 1.0;
      } else {
         faceOffset = 0.0;
      }

      if (faceAxis == Axis.X) {
         x += faceOffset;
      }

      if (faceAxis == Axis.Y) {
         y += faceOffset;
      }

      if (faceAxis == Axis.Z) {
         z += faceOffset;
      }

      Axis firstAxis = faceAxis == Axis.X ? Axis.Y : Axis.X;
      Axis secondAxis = faceAxis == Axis.Z ? Axis.Y : Axis.Z;
      Vec3 firstCorner = faceCorner(x, y, z, firstAxis, secondAxis, 0, 0);
      Vec3 secondCorner = faceCorner(x, y, z, firstAxis, secondAxis, 1, 0);
      Vec3 thirdCorner = faceCorner(x, y, z, firstAxis, secondAxis, 1, 1);
      Vec3 fourthCorner = faceCorner(x, y, z, firstAxis, secondAxis, 0, 1);
      return List.of(firstCorner, secondCorner, thirdCorner, fourthCorner);
   }

   public static float interpolateFrame(float start, float end) {
      float frameDelta = RenderUtils.getPartialTick();
      return Mth.lerp(frameDelta, start, end);
   }

   public static float yawDifferenceToPlayer(Player targetPlayer) {
      float[] targetRotations = RotationManager.getRotationsToPosition(targetPlayer.position());
      float localYaw = ClientAccess.argusclientMinecraft.player.getYRot();
      float targetYaw = targetRotations[0];
      return Mth.degreesDifferenceAbs(localYaw, targetYaw);
   }

   public static float wrapAngle(float angle) {
      float wrapped = angle % 360.0F;
      return wrapped < 0.0F ? wrapped + 360.0F : wrapped;
   }
}
