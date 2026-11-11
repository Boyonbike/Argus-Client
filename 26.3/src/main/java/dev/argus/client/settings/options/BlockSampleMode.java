package dev.argus.client.settings.options;

import dev.argus.client.ClientAccess;
import dev.argus.client.settings.NamedEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public enum BlockSampleMode implements ClientAccess, NamedEntry {
   FAST("Fast", new Vec3(0.5, 0.5, 0.5)),
   X2("X2", new Vec3(0.5, 0.05, 0.5), new Vec3(0.5, 1.0, 0.5)),
   X4("X4", new Vec3(0.05, 0.05, 0.05), new Vec3(0.05, 0.05, 0.95), new Vec3(0.95, 0.05, 0.05), new Vec3(0.95, 0.05, 0.95)),
   X8(
      "X8",
      new Vec3(0.05, 0.05, 0.05),
      new Vec3(0.05, 0.05, 0.95),
      new Vec3(0.05, 0.95, 0.05),
      new Vec3(0.05, 0.95, 0.95),
      new Vec3(0.95, 0.05, 0.05),
      new Vec3(0.95, 0.05, 0.95),
      new Vec3(0.95, 0.95, 0.05),
      new Vec3(0.95, 0.95, 0.95)
   );

   private final List<Vec3> offsets = new ArrayList<>();
   private final String displayName;

   public List<Vec3> getOffsets() {
      return this.offsets;
   }

   @Override
   public String getName() {
      return this.displayName;
   }

   BlockSampleMode(String displayName, Vec3... initialOffsets) {
      this.displayName = displayName;
      this.offsets.addAll(Arrays.asList(initialOffsets));
   }

   public List<Vec3> sampleBlock(BlockPos position) {
      return this.getOffsets().stream().map(point -> {
         Vec3 blockOrigin = Vec3.atLowerCornerOf(position);
         point = point.add(blockOrigin);
         if (this == X4) {
            LocalPlayer player = ClientAccess.argusclientMinecraft.player;
            double eyeY = player.getEyeY();
            if (eyeY > position.getY() + 1) {
               return point.add(0.0, 0.9, 0.0);
            }
         }

         return point;
      }).collect(Collectors.toList());
   }

   private boolean isOppositeFaceCoordinate(int step, int roundedCoordinate) {
      return step == 1 && roundedCoordinate == 0 || step == -1 && roundedCoordinate == 1;
   }

   public List<Vec3> sampleFace(BlockPos position, Direction face) {
      ArrayList<Vec3> samples = new ArrayList<>();

      for (Vec3 offset : this.offsets) {
         int axisStep = face.getStepX();
         double x = offset.x;
         int roundedX = (int)Math.round(x);
         if (!this.isOppositeFaceCoordinate(axisStep, roundedX)) {
            axisStep = face.getStepY();
            double y = offset.y;
            int roundedY = (int)Math.round(y);
            if (!this.isOppositeFaceCoordinate(axisStep, roundedY)) {
               axisStep = face.getStepZ();
               double z = offset.z;
               int roundedZ = (int)Math.round(z);
               if (!this.isOppositeFaceCoordinate(axisStep, roundedZ)) {
                  Vec3 blockOrigin = Vec3.atLowerCornerOf(position);
                  samples.add(offset.add(blockOrigin));
               }
            }
         }
      }

      return samples;
   }
}
