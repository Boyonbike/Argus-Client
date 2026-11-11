package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.player.SpeedMineModule;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.ExtraBreakTarget;
import net.minecraft.core.BlockPos;

public final class AutoMineMiningTracker implements ClientAccess {
   private static SpeedMineModule speedMine = ClientContext.getModule(SpeedMineModule.class);
   private final ElapsedTimer recentTimer = new ElapsedTimer();
   private BlockPos recentPosition;

   public BlockPos getCurrentPosition() {
      ExtraBreakTarget miningData = speedMine.getExtraBreakTarget();
      return miningData == null ? null : miningData.getPosition();
   }

   public boolean hasMiningActivity() {
      BlockPos recent = this.getRecentPosition();
      return recent != null && !ClientAccess.argusclientMinecraft.level.isEmptyBlock(recent) ? true : speedMine.getExtraBreakTarget() != null;
   }

   public BlockPos getRecentPosition() {
      return this.recentTimer.hasElapsed(200L) ? null : this.recentPosition;
   }

   AutoMineMiningTracker() {
   }

   public void rememberPosition(BlockPos position) {
      this.recentPosition = position;
      this.recentTimer.reset();
   }

   public boolean hasRecentDelayElapsed(long delayMillis) {
      return this.recentTimer.hasElapsed(delayMillis);
   }

   public static void bindModules(ModuleRegistry registry) {
      speedMine = (SpeedMineModule)registry.getModule(SpeedMineModule.class);
   }
}
