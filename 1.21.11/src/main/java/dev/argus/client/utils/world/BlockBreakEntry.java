package dev.argus.client.utils.world;

import dev.argus.client.utils.ElapsedTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class BlockBreakEntry {
   private final ElapsedTimer inactivityTimer = new ElapsedTimer();
   private final ElapsedTimer progressTimer = new ElapsedTimer();
   private final int entityId;
   private final BlockPos position;
   private boolean extraBreak;
   private boolean broken;
   private float progress;
   private float previousProgress;
   private int airTicks;

   public void markBroken() {
      this.broken = true;
   }

   public int getAirTicks() {
      return this.airTicks;
   }

   public void resetInactivityTimer() {
      this.inactivityTimer.reset();
   }

   public boolean isExtraBreak() {
      return this.extraBreak;
   }

   public ElapsedTimer getProgressTimer() {
      return this.progressTimer;
   }

   public Entity getEntity() {
      ClientLevel level = Minecraft.getInstance().level;
      int entityIdSnapshot = this.entityId;
      return level.getEntity(entityIdSnapshot);
   }

   public BlockPos getPosition() {
      return this.position;
   }

   public boolean isBroken() {
      return this.broken;
   }

   public boolean isExpired() {
      float progressSnapshot = this.progress;
      if (progressSnapshot == 1.0F) {
         ElapsedTimer inactivityTimerSnapshot = this.inactivityTimer;
         if (inactivityTimerSnapshot.hasElapsed(2000L)) {
            return true;
         }

         if (this.isExtraBreak()) {
            return true;
         }
      }

      if (this.isExtraBreak() && this.isBroken()) {
         return true;
      }

      Entity entity = this.getEntity();
      if (entity == null) {
         return true;
      }

      Vec3 eyePositionSnapshot = entity.getEyePosition();
      Vec3 blockCenter = this.position.getCenter();
      double distanceSquared = eyePositionSnapshot.distanceToSqr(blockCenter);
      return distanceSquared >= 64.0;
   }

   public float getProgress() {
      return this.progress;
   }

   public int getEntityId() {
      return this.entityId;
   }

   public BlockBreakEntry(int entityId, BlockPos position, boolean extraBreak) {
      this.entityId = entityId;
      this.position = position;
      this.extraBreak = extraBreak;
   }

   public void setAirTicks(int airTicks) {
      this.airTicks = airTicks;
   }

   public float getInterpolatedProgress(float partialTick) {
      float previous = this.previousProgress;
      float current = this.progress;
      return Mth.lerp(partialTick, previous, current);
   }

   public void setProgress(float progress) {
      if (this.progress != progress) {
         this.previousProgress = progress == 0.0F ? 0.0F : this.progress;
         this.resetInactivityTimer();
         this.progress = Mth.clamp(progress, 0.0F, 1.0F);
      }
   }
}
