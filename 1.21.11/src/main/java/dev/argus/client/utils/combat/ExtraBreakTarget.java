package dev.argus.client.utils.combat;

import dev.argus.client.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

public final class ExtraBreakTarget implements ClientAccess {
   private final BlockPos position;
   private float progress;

   public BlockPos getPosition() {
      return this.position;
   }

   public float getClampedProgress() {
      return Mth.clamp(this.progress, 0.0F, 1.0F);
   }

   public ExtraBreakTarget(BlockPos position) {
      this.position = position;
   }

   public void addProgress(float amount) {
      this.progress += amount;
   }

   public void setProgress(float progress) {
      this.progress = progress;
   }
}
