package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.managers.RotationManager;
import net.minecraft.core.BlockPos;

public enum ObstacleAlignmentDirection implements ClientAccess {
   XP,
   XN,
   ZP,
   ZN,
   XP_ZP,
   XN_ZP,
   XP_ZN,
   XN_ZN;

   public BlockPos positionAtOffset(Integer offset) {
      return switch (this) {
         case XP, XN -> {
            double x = ClientAccess.argusclientMinecraft.player.getX();
            yield BlockPos.containing(this == XP ? x + offset.intValue() : x - offset.intValue(), 0.0, 0.0);
         }
         case ZP, ZN -> {
            double z = ClientAccess.argusclientMinecraft.player.getZ();
            yield BlockPos.containing(0.0, 0.0, this == ZP ? z + offset.intValue() : z - offset.intValue());
         }
         case XP_ZP -> BlockPos.containing(projectedX() + offset, 0.0, projectedZ() + offset);
         case XN_ZP -> BlockPos.containing(projectedX() - offset, 0.0, projectedZ() + offset);
         case XP_ZN -> BlockPos.containing(projectedX() + offset, 0.0, projectedZ() - offset);
         case XN_ZN -> BlockPos.containing(projectedX() - offset, 0.0, projectedZ() - offset);
      };
   }

   private static int projectedX() {
      double coordinateMagnitude = maximumHorizontalCoordinate();
      double playerX = ClientAccess.argusclientMinecraft.player.getX();
      return (int)(coordinateMagnitude * Math.signum(playerX));
   }

   private static int projectedZ() {
      double coordinateMagnitude = maximumHorizontalCoordinate();
      double playerZ = ClientAccess.argusclientMinecraft.player.getZ();
      return (int)(coordinateMagnitude * Math.signum(playerZ));
   }

   private static int maximumHorizontalCoordinate() {
      return (int)Math.max(Math.abs(ClientAccess.argusclientMinecraft.player.getX()), Math.abs(ClientAccess.argusclientMinecraft.player.getZ()));
   }

   public static ObstacleAlignmentDirection fromYaw(float yaw) {
      return switch (RotationManager.getYawSector(yaw)) {
         case 0 -> ZP;
         case 1 -> XN_ZP;
         case 2 -> XN;
         case 3 -> XN_ZN;
         case 4 -> ZN;
         case 5 -> XP_ZN;
         case 6 -> XP;
         case 7 -> XP_ZP;
         default -> null;
      };
   }
}
