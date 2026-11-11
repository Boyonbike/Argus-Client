package dev.argus.client.settings.options;

import dev.argus.client.ClientAccess;
import dev.argus.client.settings.NamedEntry;
import java.util.function.ToDoubleFunction;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public enum NukerSortChoice implements NamedEntry {
   CLOSEST("Closest", blockPos -> {
      LocalPlayer playerForEye = ClientAccess.argusclientMinecraft.player;
      Vec3 eyeSnapshot = playerForEye.getEyePosition();
      Vec3 blockCenter = Vec3.atCenterOf(blockPos);
      return eyeSnapshot.distanceToSqr(blockCenter);
   }),
   FURTHEST("Furthest", blockPos -> {
      LocalPlayer playerForEye = ClientAccess.argusclientMinecraft.player;
      Vec3 eyeSnapshot = playerForEye.getEyePosition();
      Vec3 blockCenter = Vec3.atCenterOf(blockPos);
      return -eyeSnapshot.distanceToSqr(blockCenter);
   }),
   TOP("Top", blockPos -> -blockPos.getY()),
   BOTTOM("Bottom", blockPos -> blockPos.getY());

   private final String displayName;
   private final ToDoubleFunction<BlockPos> sortKeyFunction;

   public double getSortKey(BlockPos blockPos) {
      return this.sortKeyFunction.applyAsDouble(blockPos);
   }

   @Override
   public String getName() {
      return this.displayName;
   }

   NukerSortChoice(String displayName, ToDoubleFunction<BlockPos> sortKeyFunction) {
      this.displayName = displayName;
      this.sortKeyFunction = sortKeyFunction;
   }
}
