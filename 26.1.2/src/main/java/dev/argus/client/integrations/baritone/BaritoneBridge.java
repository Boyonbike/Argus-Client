package dev.argus.client.integrations.baritone;

import dev.argus.client.NamedFeature;
import net.minecraft.core.BlockPos;

public interface BaritoneBridge {
   void pathElytraTo(BlockPos var1);

   void setGoal(BlockPos var1);

   boolean isPathingOrCalculating();

   boolean isInSelection(BlockPos var1);

   boolean isPauseRequested();

   BlockPos getGoalPosition();

   void pathTo(BlockPos var1);

   void cancelPathing();

   boolean isAvailable();

   void requestPause(NamedFeature var1);

   void releasePause(NamedFeature var1);

   void pathForward();
}
