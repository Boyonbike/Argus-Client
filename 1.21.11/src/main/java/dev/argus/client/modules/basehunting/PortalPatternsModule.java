package dev.argus.client.modules.basehunting;

import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import java.awt.Color;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Finds the outline of broken or removed Nether portals in caves. Caves are carved with cave air, so a flat rectangle
 * of plain air the size of a portal frame is where someone built and later broke one. Useful for finding the portal a
 * base used. Based on Trouser Streak's PortalPatternFinder, with a stricter rectangle check.
 */
public class PortalPatternsModule extends AirPocketModule {
   private final Setting<Integer> minWidthSetting = this.add(new NumberSetting<>("MinWidth", 2, 2, 21));
   private final Setting<Integer> minHeightSetting = this.add(new NumberSetting<>("MinHeight", 3, 3, 21));
   private final Setting<Integer> maxWidthSetting = this.add(new NumberSetting<>("MaxWidth", 8, 4, 23));
   private final Setting<Integer> maxHeightSetting = this.add(new NumberSetting<>("MaxHeight", 8, 5, 23));
   private final Setting<Boolean> cornersSetting = this.add(new BooleanSetting("AllowNoCorners", true));
   private final Setting<Integer> sidesSetting = this.add(new NumberSetting<>("SolidSides", 0, 0, 100).withUnit("%"));

   public PortalPatternsModule() {
      super(
         "PortalPatterns",
         "Finds the outline of broken or removed Nether portals in caves.",
         module -> new HuntMarkers(module, new Color(200, 60, 255, 60), new Color(200, 60, 255, 230), true)
      );
   }

   /** How much of the pocket has something other than plain air on both of its flat faces, as a percentage. */
   private static int solidSidesPercent(ClientLevel level, List<int[]> pocket, int[] shape) {
      boolean flatX = shape[0] == shape[3];
      BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
      int solid = 0;
      for (int[] block : pocket) {
         int ax = flatX ? 1 : 0;
         int az = flatX ? 0 : 1;
         boolean one = !level.getBlockState(position.set(block[0] + ax, block[1], block[2] + az)).is(Blocks.AIR);
         boolean two = !level.getBlockState(position.set(block[0] - ax, block[1], block[2] - az)).is(Blocks.AIR);
         if (one && two) {
            solid++;
         }
      }

      return solid * 100 / pocket.size();
   }

   @Override
   protected int pocketLimit() {
      return 23 * 23;
   }

   @Override
   protected void onPocket(ClientLevel level, List<int[]> pocket) {
      int[] shape = AirPockets.portalShape(
         pocket,
         this.minWidthSetting.getValue(),
         this.minHeightSetting.getValue(),
         this.maxWidthSetting.getValue(),
         this.maxHeightSetting.getValue(),
         this.cornersSetting.getValue()
      );
      if (shape != null && this.sidesSetting.getValue() > 0 && solidSidesPercent(level, pocket, shape) < this.sidesSetting.getValue()) {
         shape = null;
      }

      if (shape != null) {
         BlockPos corner = new BlockPos(shape[0], shape[1], shape[2]);
         AABB box = new AABB(shape[0], shape[1], shape[2], shape[3] + 1, shape[4] + 1, shape[5] + 1);
         this.markers.report(corner, box, "a removed portal");
      }
   }
}
