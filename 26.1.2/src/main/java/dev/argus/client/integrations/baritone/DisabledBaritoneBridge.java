package dev.argus.client.integrations.baritone;

import dev.argus.client.NamedFeature;
import net.minecraft.core.BlockPos;

public final class DisabledBaritoneBridge implements BaritoneBridge {
   @Override
   public void pathElytraTo(BlockPos position) {
   }

   @Override
   public void setGoal(BlockPos position) {
   }

   @Override
   public boolean isPathingOrCalculating() {
      return false;
   }

   @Override
   public boolean isInSelection(BlockPos position) {
      return false;
   }

   @Override
   public boolean isPauseRequested() {
      return false;
   }

   @Override
   public BlockPos getGoalPosition() {
      return null;
   }

   @Override
   public void pathTo(BlockPos position) {
   }

   @Override
   public void cancelPathing() {
   }

   @Override
   public boolean isAvailable() {
      return false;
   }

   @Override
   public void requestPause(NamedFeature feature) {
   }

   @Override
   public void releasePause(NamedFeature feature) {
   }

   @Override
   public void pathForward() {
   }
}
