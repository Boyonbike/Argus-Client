package dev.argus.client.modules.combat;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public final class PusherFacingResolver {
   PusherFacingResolver() {
   }

   public Direction resolveFacing(float[] angles) {
      float angleComponent = angles[1];
      float pitchRadians = angleComponent * (float) (Math.PI / 180.0);
      angleComponent = -angles[0];
      float negativeYawRadians = angleComponent * (float) (Math.PI / 180.0);
      double pitchForSin = pitchRadians;
      float sinPitch = Mth.sin(pitchForSin);
      double pitchForCos = pitchRadians;
      float cosPitch = Mth.cos(pitchForCos);
      double yawForSin = negativeYawRadians;
      float sinYaw = Mth.sin(yawForSin);
      double yawForCos = negativeYawRadians;
      float cosYaw = Mth.cos(yawForCos);
      boolean positiveX = sinYaw > 0.0F;
      boolean upward = sinPitch < 0.0F;
      boolean positiveZ = cosYaw > 0.0F;
      float absSinYaw = positiveX ? sinYaw : -sinYaw;
      float absSinPitch = upward ? -sinPitch : sinPitch;
      float absCosYaw = positiveZ ? cosYaw : -cosYaw;
      float xProjection = absSinYaw * cosPitch;
      float zProjection = absCosYaw * cosPitch;
      Direction xDirection = positiveX ? Direction.EAST : Direction.WEST;
      Direction yDirection = upward ? Direction.UP : Direction.DOWN;
      Direction zDirection = positiveZ ? Direction.SOUTH : Direction.NORTH;
      if (absSinYaw > absCosYaw) {
         return absSinPitch > xProjection ? yDirection : xDirection;
      } else {
         return absSinPitch > zProjection ? yDirection : zDirection;
      }
   }
}
