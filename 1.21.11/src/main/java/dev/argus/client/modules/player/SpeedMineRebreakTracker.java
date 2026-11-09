package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.ServerStateManager;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.world.WorldUtils;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SpeedMineRebreakTracker implements ClientAccess {
   private static final long MINIMUM_REBREAK_DELAY_MILLIS = 750L;
   private final SpeedMineModule speedMineModule;
   private final ElapsedTimer rebreakTimer = new ElapsedTimer();
   private boolean tracking;
   private BlockPos pos;

   public void validateTarget() {
      if (this.tracking && this.pos != null) {
         BlockPos trackedPos = this.pos;
         boolean validTarget = false;
         if (WorldUtils.canMineBlock(trackedPos)) {
            BlockPos currentPos = this.pos;
            validTarget = !WorldUtils.isAir(currentPos);
         }

         if (!validTarget) {
            this.reset();
         }

         if (this.pos != null) {
            BlockPos positionToCompare = this.pos;
            SpeedMineModule module = this.speedMineModule;
            if (!positionToCompare.equals(module.getMiningPos())) {
               this.reset();
            }
         }
      }
   }

   public boolean hasRebreakDelayElapsed() {
      if (this.tracking && this.pos != null) {
         double latencyTicks = ClientContext.serverStateManager.getLatencyMillis() * 15.0F / 50.0F;
         long delayMillisScratch = (long)(Math.ceil(latencyTicks) * 50.0);
         long minimumDelayMillis = 750L;
         long latencyDelayMillis = delayMillisScratch;
         delayMillisScratch = Math.max(latencyDelayMillis, minimumDelayMillis);
         float clampedDelayMillis = (float)delayMillisScratch;
         ServerStateManager tickRateTracker = ClientContext.serverStateManager;
         delayMillisScratch = (long)(clampedDelayMillis / tickRateTracker.getTickRateScale());
         float scaledDelayMillis = (float)delayMillisScratch;
         Float instantDelay = this.speedMineModule.instantDelaySetting.getValue();
         delayMillisScratch = (long)(scaledDelayMillis + instantDelay);
         return this.rebreakTimer.hasElapsed(delayMillisScratch);
      } else {
         return false;
      }
   }

   public SpeedMineRebreakTracker(SpeedMineModule module) {
      this.speedMineModule = module;
   }

   public void reset() {
      this.tracking = false;
      this.pos = null;
   }

   public void handleBlockUpdates(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundBlockUpdatePacket blockUpdate && blockUpdate.getBlockState().isAir() && blockUpdate.getPos().equals(this.pos)
         )
       {
         Minecraft minecraft = ClientAccess.argusclientMinecraft;
         Runnable resetTask = this::reset;
         minecraft.executeIfPossible(resetTask);
      }

      if (event.getPacket() instanceof ClientboundSectionBlocksUpdatePacket sectionUpdate) {
         BiConsumer<BlockPos, BlockState> resetOnAirUpdate = (updatedPos, updatedState) -> {
            if (updatedPos.equals(this.pos) && updatedState.isAir()) {
               Minecraft minecraftx = ClientAccess.argusclientMinecraft;
               Runnable resetTaskx = this::reset;
               minecraftx.executeIfPossible(resetTaskx);
            }
         };
         sectionUpdate.runUpdates(resetOnAirUpdate);
      }

      if (event.getPacket() instanceof ClientboundAddEntityPacket entitySpawn) {
         BlockPos spawnCheckPosition = this.pos;
         if (spawnCheckPosition == null) {
            return;
         }

         AABB trackedBounds = new AABB(spawnCheckPosition);
         Vec3 spawnPosition = new Vec3(entitySpawn.getX(), entitySpawn.getY(), entitySpawn.getZ());
         if (!trackedBounds.contains(spawnPosition)) {
            return;
         }

         Minecraft minecraft = ClientAccess.argusclientMinecraft;
         Runnable resetTask = this::reset;
         minecraft.execute(resetTask);
      }
   }

   public void startTracking(BlockPos blockPos) {
      if (!this.tracking) {
         this.tracking = true;
         this.pos = blockPos;
         this.rebreakTimer.reset();
      }
   }
}
