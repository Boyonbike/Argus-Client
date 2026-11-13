package dev.argus.client.modules.render;

import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Staircase detection for Tunnels: one-wide dug stairways that climb a block per step, which players cut when mining
 * up or down. Ported from Trouser Streak's Hole/Tunnel/StairsESP.
 */
final class TunnelsStairs {
   private final Setting<Boolean> stairsSetting;
   private final Setting<Integer> minStepsSetting;
   private final Setting<Integer> minHeightSetting;
   private final Setting<Integer> maxHeightSetting;
   private final Setting<Color> fillSetting;
   private final Setting<Color> outlineSetting;
   private final Predicate<BlockPos> open;
   private final Map<Long, AABB> stairs = new ConcurrentHashMap<>();

   TunnelsStairs(TunnelsModule module, Predicate<BlockPos> open) {
      this.open = open;
      this.stairsSetting = module.add(new BooleanSetting("Stairs", true).markVisibilityDependency());
      this.minStepsSetting = module.add(new NumberSetting<>("StairSteps", 3, 2, 16).dependsOn(this.stairsSetting));
      this.minHeightSetting = module.add(new NumberSetting<>("StairMinHeight", 3, 2, 6).withUnit("m").dependsOn(this.stairsSetting));
      this.maxHeightSetting = module.add(new NumberSetting<>("StairMaxHeight", 5, 2, 8).withUnit("m").dependsOn(this.stairsSetting));
      this.fillSetting = module.add(new ColorSetting("StairFill", new Color(255, 165, 0, 50)).dependsOn(this.stairsSetting));
      this.outlineSetting = module.add(new ColorSetting("StairOutline", new Color(255, 165, 0, 230)).dependsOn(this.stairsSetting));
   }

   /** Checks whether a staircase starts at this open block. Called from the Tunnels scanner thread. */
   void scan(BlockPos position) {
      if (!this.stairsSetting.getValue() || this.open.test(position.below())) {
         return;
      }

      for (Direction direction : Plane.HORIZONTAL) {
         // Only start from the bottom step, so one staircase is not found again from each step up it.
         if (!this.isStep(position, direction) || this.isStep(position.relative(direction.getOpposite()).below(), direction)) {
            continue;
         }

         BlockPos.MutableBlockPos step = position.mutable();
         int steps = 0;
         double minX = position.getX(), minY = position.getY(), minZ = position.getZ();
         double maxX = minX + 1, maxY = minY + 1, maxZ = minZ + 1;
         while (steps < 64 && this.isStep(step, direction)) {
            int height = this.openHeight(step);
            minX = Math.min(minX, step.getX());
            minZ = Math.min(minZ, step.getZ());
            maxX = Math.max(maxX, step.getX() + 1);
            maxZ = Math.max(maxZ, step.getZ() + 1);
            maxY = Math.max(maxY, step.getY() + height);
            step.move(direction).move(Direction.UP);
            steps++;
         }

         if (steps >= this.minStepsSetting.getValue()) {
            this.stairs.put(position.asLong() * 4 + direction.get2DDataValue(), new AABB(minX, minY, minZ, maxX, maxY, maxZ));
         }
      }
   }

   private int openHeight(BlockPos position) {
      int max = this.maxHeightSetting.getValue();
      int height = 0;
      while (height < max + 1 && this.open.test(position.above(height))) {
         height++;
      }

      return height;
   }

   /** A step: solid floor, a gap of the right height, solid roof, and solid walls on both sides. */
   private boolean isStep(BlockPos position, Direction direction) {
      if (!this.open.test(position) || this.open.test(position.below())) {
         return false;
      }

      int height = this.openHeight(position);
      if (height < this.minHeightSetting.getValue() || height > this.maxHeightSetting.getValue()) {
         return false;
      }

      Direction left = direction.getClockWise();
      for (int offset = 0; offset < height; offset++) {
         BlockPos level = position.above(offset);
         if (this.open.test(level.relative(left)) || this.open.test(level.relative(left.getOpposite()))) {
            return false;
         }
      }

      return true;
   }

   void render(WorldRenderEvent event, float lineWidth) {
      if (!this.stairsSetting.getValue() || this.stairs.isEmpty()) {
         return;
      }

      Color fill = this.fillSetting.getValue();
      Color outline = this.outlineSetting.getValue();
      for (AABB box : this.stairs.values()) {
         if (RotationManager.isVisible(box)) {
            WorldBoxRenderer.fillBox(event.getPoseStack(), box, fill);
            WorldBoxRenderer.outlineBox(event.getPoseStack(), box, outline, lineWidth);
         }
      }
   }

   void forgetChunk(ChunkPos chunkPosition) {
      this.stairs.values().removeIf(box -> {
         Vec3 center = box.getCenter();
         return center.x >= chunkPosition.getMinBlockX() && center.x < chunkPosition.getMaxBlockX() + 1
            && center.z >= chunkPosition.getMinBlockZ() && center.z < chunkPosition.getMaxBlockZ() + 1;
      });
   }

   void pruneFarFrom(Vec3 position) {
      this.stairs.values().removeIf(box -> box.getCenter().distanceToSqr(position) > 65536.0);
   }

   void clear() {
      this.stairs.clear();
   }
}
