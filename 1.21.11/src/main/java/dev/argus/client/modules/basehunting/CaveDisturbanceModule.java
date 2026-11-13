package dev.argus.client.modules.basehunting;

import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import java.awt.Color;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Finds single blocks of plain air inside caves. Caves are carved with cave air since 1.13, so one odd block of plain
 * air is often where a player placed and removed something, like a torch. Expect false positives from falling gravel
 * and flowing water. Ported from Trouser Streak's CaveDisturbanceDetector.
 */
public class CaveDisturbanceModule extends AirPocketModule {
   private final Setting<Integer> isolationSetting = this.add(new NumberSetting<>("Isolation", 2, 1, 4).withUnit("m"));
   private final Setting<Boolean> fluidsSetting = this.add(new BooleanSetting("IgnoreFluids", true));
   private final Setting<Boolean> fallingSetting = this.add(new BooleanSetting("IgnoreFalling", true));

   public CaveDisturbanceModule() {
      super(
         "CaveDisturbance",
         "Finds single blocks of plain air inside caves, which are often where a player placed and removed something.",
         module -> new HuntMarkers(module, new Color(255, 230, 0, 70), new Color(255, 230, 0, 230), false, false)
      );
   }

   @Override
   protected int pocketLimit() {
      return 1;
   }

   @Override
   protected void onPocket(ClientLevel level, List<int[]> pocket) {
      if (pocket.size() != 1) {
         return;
      }

      int[] block = pocket.get(0);
      BlockPos position = new BlockPos(block[0], block[1], block[2]);
      int radius = this.isolationSetting.getValue();
      boolean fluids = this.fluidsSetting.getValue();
      boolean falling = this.fallingSetting.getValue();
      // Plain air close by means a tunnel or mined area rather than a lone disturbance.
      for (BlockPos nearby : BlockPos.betweenClosed(position.offset(-radius, -radius, -radius), position.offset(radius, radius, radius))) {
         if (nearby.equals(position)) {
            continue;
         }

         BlockState state = level.getBlockState(nearby);
         if (state.is(Blocks.AIR)) {
            return;
         }

         // Flowing water and lava, and gravel or sand that fell, leave plain air behind on their own.
         if (fluids && !state.getFluidState().isEmpty() || falling && state.getBlock() instanceof FallingBlock) {
            return;
         }
      }

      this.markers.report(position, new AABB(position), "a cave disturbance");
   }
}
