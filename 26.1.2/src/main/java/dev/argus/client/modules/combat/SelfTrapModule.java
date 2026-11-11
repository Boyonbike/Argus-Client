package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.SocialRegistry;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.world.BlockInteractionUtils;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class SelfTrapModule extends AutoTrapModule {
   private Setting<Boolean> onlyHoleSetting;
   private Setting<Boolean> onlyStandingSetting;
   private Setting<Boolean> smartSetting;
   protected Setting<Float> enemyRangeSetting;

   @Override
   public String getInfo() {
      return null;
   }

   private void initializeSelfTrapSettings() {
      BooleanSetting onlyHoleEditor = new BooleanSetting("OnlyHole", false);
      this.onlyHoleSetting = this.add(onlyHoleEditor);
      BooleanSetting onlyStandingEditor = new BooleanSetting("OnlyStanding", false);
      this.onlyStandingSetting = this.add(onlyStandingEditor);
      BooleanSetting smartEditor = new BooleanSetting("Smart", false);
      Setting<Boolean> smartDependency = smartEditor.markVisibilityDependency();
      this.smartSetting = this.add(smartDependency);
      NumberSetting<Float> enemyRange = new NumberSetting<>("EnemyRange", 3.0F, 1.0F, 6.0F, unused -> this.smartSetting.isExpanded());
      NumberSetting<Float> enemyRangeWithUnit = enemyRange.withUnit("m");
      this.enemyRangeSetting = this.add(enemyRangeWithUnit);
   }

   @Override
   protected boolean shouldPlaceBelow() {
      return false;
   }

   private Entity findNearestEnemy() {
      Iterable<Entity> entities = ClientAccess.argusclientMinecraft.level.entitiesForRendering();
      Spliterator<Entity> entitySpliterator = entities.spliterator();
      Stream<Entity> candidateStream = StreamSupport.stream(entitySpliterator, false).filter(candidate -> {
         if (candidate == ClientAccess.argusclientMinecraft.player) {
            return false;
         } else {
            float candidateDistance = ClientAccess.argusclientMinecraft.player.distanceTo(candidate);
            if (candidateDistance <= this.enemyRangeSetting.getValue() && candidate instanceof Player) {
               SocialRegistry friends = ClientContext.socialRegistry;
               Component candidateName = candidate.getName();
               String name = candidateName.getString();
               return !friends.isFriendName(name) && candidate.isAlive();
            } else {
               return false;
            }
         }
      });
      LocalPlayer distancePlayer = ClientAccess.argusclientMinecraft.player;
      Comparator<Entity> nearestFirst = Comparator.comparing(distancePlayer::distanceToSqr);
      Optional<Entity> enemy = candidateStream.min(nearestFirst);
      return enemy.orElse(null);
   }

   private boolean shouldSkipUnsupportedPlacement(BlockPos blockPos) {
      boolean attackCheckFlag = true;
      if (!BlockInteractionUtils.canPlaceAt(blockPos, true, attackCheckFlag)) {
         return true;
      }

      Setting<Boolean> airPlaceSettingRef = this.placementAirPlaceSetting;
      if (airPlaceSettingRef.getValue()) {
         return false;
      }

      boolean strictDirectionSnapshot = BlockInteractionUtils.isStrictDirectionEnabled();
      Setting<Boolean> raytraceSettingRef = this.placementRaytraceSetting;
      boolean raytrace = raytraceSettingRef.getValue();
      return BlockInteractionUtils.findSupportDirection(blockPos, strictDirectionSnapshot, raytrace) == null;
   }

   @Override
   protected Player selectTarget() {
      return ClientAccess.argusclientMinecraft.player;
   }

   @Override
   public List getPlacementPositions() {
      List<BlockPos> placements = super.getPlacementPositions();
      placements.removeIf(this::shouldSkipUnsupportedPlacement);
      if (this.onlyStandingSetting.getValue() && PlayerStateUtils.isLocalHeightBelowOne()) {
         return Collections.emptyList();
      } else if (this.smartSetting.getValue() && this.findNearestEnemy() == null) {
         return Collections.emptyList();
      } else {
         return this.onlyHoleSetting.getValue() && !ClientContext.holeManager.isInHole() ? Collections.emptyList() : placements;
      }
   }

   public SelfTrapModule() {
      super("SelfTrap", "Covers you with blocks.", ModuleCategory.COMBAT);
      this.setDisableOnRespawn(true);
      this.initializeSelfTrapSettings();
      Setting<Float> range = this.rangeSetting;
      this.unregister(range);
      this.unregister(this.ignoreNakedsSetting);
      this.unregister(this.ignoreCrawlSetting);
      this.unregister(this.onlyPhasedSetting);
      this.unregister(this.logoutsSetting);
      this.unregister(this.belowSetting);
   }
}
