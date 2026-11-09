package dev.argus.client.utils.inventory;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;

public class AutoMineCandidates {
   private BlockPos primaryPosition;
   private BlockPos secondaryPosition;
   private boolean miningCancellationRequested;
   private int priority;

   public void reset() {
      this.primaryPosition = null;
      this.miningCancellationRequested = false;
   }

   public void requestMiningCancellation() {
      this.miningCancellationRequested = true;
   }

   public boolean isMiningCancellationRequested() {
      return this.miningCancellationRequested;
   }

   public BlockPos getPrimaryPosition() {
      return this.primaryPosition;
   }

   public void setPrimaryPosition(BlockPos position) {
      this.primaryPosition = position;
   }

   public BlockPos getSecondaryPosition() {
      return this.secondaryPosition;
   }

   public void setSecondaryPosition(BlockPos position) {
      this.secondaryPosition = position;
   }

   public void swapPositions() {
      BlockPos previousPrimary = this.getPrimaryPosition();
      this.primaryPosition = this.secondaryPosition;
      this.secondaryPosition = previousPrimary;
   }

   public int getPriority() {
      return this.priority;
   }

   public void setPriority(int newPriority) {
      this.priority = newPriority;
   }

   public void offer(int incomingPriority, Consumer<AutoMineCandidates> update) {
      if (this.priority <= incomingPriority || this.getPrimaryPosition() == null || this.isMiningCancellationRequested()) {
         update.accept(this);
         this.priority = incomingPriority;
      }
   }
}
