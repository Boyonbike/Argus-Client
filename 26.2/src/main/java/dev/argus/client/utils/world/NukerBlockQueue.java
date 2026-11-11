package dev.argus.client.utils.world;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.player.NukerModule;
import dev.argus.client.modules.player.SpeedMineModule;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.inventory.ExtraBreakTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class NukerBlockQueue implements ClientAccess {
   private static SpeedMineModule speedMineModule = ClientContext.getModule(SpeedMineModule.class);
   private final List<BlockPos> positions = new ArrayList<>();
   private final NukerModule nukerModule;

   public void pruneInvalidPositions() {
      this.positions.removeIf(this::shouldRemovePosition);
   }

   public List<BlockPos> getPositions() {
      return this.positions;
   }

   public NukerBlockQueue(NukerModule nukerModule) {
      this.nukerModule = nukerModule;
   }

   private boolean shouldRemovePosition(BlockPos blockPos) {
      SpeedMineModule speedMineForToggle = speedMineModule;
      if (speedMineForToggle.isToggled()) {
         SpeedMineModule speedMineSnapshot = speedMineModule;
         ExtraBreakTracker pendingTaskSnapshot = speedMineSnapshot.getExtraBreakTracker();
         if (pendingTaskSnapshot.isTracked(blockPos)) {
            return true;
         }
      }

      if (!WorldUtils.canMineBlock(blockPos)) {
         return true;
      }

      Vec3 eyePosition = ClientAccess.argusclientMinecraft.player.getEyePosition();
      AABB blockBounds = new AABB(blockPos);
      Vec3 closestPoint = BoundingBoxUtils.clampToBounds(eyePosition, blockBounds);
      double distance = eyePosition.distanceTo(closestPoint);
      Setting<Float> rangeSettingRef = this.nukerModule.rangeSetting;
      Float configuredRange = rangeSettingRef.getValue();
      return distance > configuredRange.floatValue();
   }

   public void enqueue(BlockPos blockPos) {
      if (!this.positions.contains(blockPos)) {
         this.positions.add(blockPos);
      }
   }

   public List<BlockPos> getNextPositions(boolean includeSecond) {
      if (this.positions.isEmpty()) {
         return Collections.emptyList();
      } else {
         return this.positions.size() == 1
            ? new ArrayList<>(List.of(this.positions.getFirst()))
            : new ArrayList<>(includeSecond ? List.of(this.positions.getFirst(), this.positions.get(1)) : Collections.singletonList(this.positions.getFirst()));
      }
   }

   public static void bindModules(ModuleRegistry modules) {
      speedMineModule = (SpeedMineModule)modules.getModule(SpeedMineModule.class);
   }
}
