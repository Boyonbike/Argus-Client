package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.PlayerIsStayingOnGroundSurfaceEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.entity.EntityUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class SafeWalkModule extends Module {
   private final Setting<Boolean> sneakSetting;
   private final Setting<Integer> delaySetting;
   private final ElapsedTimer edgeDelayTimer;

   public SafeWalkModule() {
      super("SafeWalk", "Helps not to fall from blocks if you're bad.", ModuleCategory.MOVEMENT);
      BooleanSetting sneak = new BooleanSetting("Sneak", false);
      sneak.markVisibilityDependency();
      this.sneakSetting = this.add(sneak);
      NumberSetting<Integer> delay = new NumberSetting<>("Delay", 0, 0, 1000);
      delay.withUnit("ms");
      this.delaySetting = this.add(delay.dependsOn(this.sneakSetting));
      this.edgeDelayTimer = new ElapsedTimer();
   }

   @Override
   public void onDisable() {
      if (this.sneakSetting.getValue()) {
         ClientAccess.argusclientMinecraft.options.keyShift.setDown(false);
      }
   }

   @EventHandler
   private void onPlayerTick(LocalPlayerTickEvent event) {
      if (this.sneakSetting.getValue()) {
         BlockPos below = BlockPos.containing(ClientAccess.argusclientMinecraft.player.position().add(0.0, -0.1F, 0.0));
         BlockState block = ClientAccess.argusclientMinecraft.level.getBlockState(below);
         if (this.shouldSneakAtEdge(block)) {
            this.edgeDelayTimer.reset();
            ClientContext.inputOverrides.requestSneak(true);
         } else if (!this.edgeDelayTimer.hasElapsed(this.delaySetting.getValue().longValue())) {
            ClientContext.inputOverrides.requestSneak(true);
         }
      }
   }

   @EventHandler
   private void onGroundSurfaceQuery(PlayerIsStayingOnGroundSurfaceEvent event) {
      if (!ClientAccess.argusclientMinecraft.player.isSwimming()
         && !ClientAccess.argusclientMinecraft.player.isInLava()
         && !ClientAccess.argusclientMinecraft.player.isInWater()
         && !this.sneakSetting.getValue()) {
         event.cancel();
      }
   }

   private boolean shouldSneakAtEdge(BlockState block) {
      return EntityUtils.hasCollisionBelow(ClientAccess.argusclientMinecraft.player)
         && !ClientAccess.argusclientMinecraft.player.isInWater()
         && !ClientAccess.argusclientMinecraft.player.isInLava()
         && this.isUnsupportedBlock(block);
   }

   private boolean isUnsupportedBlock(BlockState block) {
      return block.is(Blocks.AIR) || block.getCollisionShape(ClientAccess.argusclientMinecraft.level, BlockPos.ZERO).isEmpty();
   }
}
