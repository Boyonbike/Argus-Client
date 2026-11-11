package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.player.SpeedMineModule;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.entity.PlayerStateUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TrapMiningSupport {
   private static SpeedMineModule speedMineModule = ClientContext.getModule(SpeedMineModule.class);
   private final AutoTrapModule trapModule;
   private final List<Setting<Boolean>> bodyPartSettings;

   private boolean hasEnemyBelowPosition(BlockPos blockPos) {
      AABB expandedBounds = new AABB(blockPos).inflate(1.0);

      for (AbstractClientPlayer player : ClientAccess.argusclientMinecraft.level.players()) {
         if (player != ClientAccess.argusclientMinecraft.player
            && !ClientContext.socialRegistry.isFriendPlayer(player)
            && !player.isDeadOrDying()
            && player.isAlive()) {
            AABB playerBounds = player.getBoundingBox();
            if (playerBounds.intersects(expandedBounds)) {
               BlockPos playerPos = PlayerStateUtils.getRoundedBlockPosition(player);
               int playerY = playerPos.getY();
               if (playerY < blockPos.getY()) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public TrapMiningSupport(AutoTrapModule trapModule) {
      this.trapModule = trapModule;
      Setting<Boolean> belowSetting = trapModule.belowSetting;
      Setting<Boolean> legsSetting = trapModule.legsSetting;
      Setting<Boolean> faceSetting = trapModule.faceSetting;
      Setting<Boolean> headSetting = trapModule.headSetting;
      this.bodyPartSettings = List.of(headSetting, faceSetting, legsSetting, belowSetting);
   }

   public List collectMiningPositions() {
      SpeedMineModule speedMineSnapshot = speedMineModule;
      BlockPos miningPos = speedMineSnapshot.getMiningPos();
      if (miningPos == null) {
         speedMineSnapshot = speedMineModule;
         miningPos = speedMineSnapshot.getRebreakPos();
      }

      if (miningPos == null) {
         return Collections.emptyList();
      } else {
         Vec3 eyeSnapshot = ClientAccess.argusclientMinecraft.player.getEyePosition();
         Vec3 blockCenter = Vec3.atCenterOf(miningPos);
         double distance = eyeSnapshot.distanceTo(blockCenter);
         Float configuredRange = this.trapModule.rangeSetting.getValue();
         if (distance > configuredRange.floatValue() || !this.hasEnemyBelowPosition(miningPos)) {
            return Collections.emptyList();
         } else {
            return this.trapModule.hasLogoutAbovePosition(miningPos) ? Collections.emptyList() : new ArrayList<>(List.of(miningPos));
         }
      }
   }

   public boolean shouldUseMiningSupport() {
      if (!this.trapModule.miningSupportAllowed) {
         return false;
      }

      for (Setting<Boolean> setting : this.bodyPartSettings) {
         if (setting.getValue()) {
            return false;
         }
      }

      return true;
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      speedMineModule = (SpeedMineModule)registry.getModule(SpeedMineModule.class);
   }
}
