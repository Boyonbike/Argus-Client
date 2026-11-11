package dev.argus.client.utils.inventory;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.modules.combat.AutoMineModule;
import dev.argus.client.modules.combat.AutoMineTargetSelector;
import dev.argus.client.modules.combat.AutoTrapModule;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class AutoMineHeadProvider extends AutoMineProviderBase {
   private static AutoTrapModule autoTrap = ClientContext.getModule(AutoTrapModule.class);
   private static AntiCheatModule antiCheat = ClientContext.getModule(AntiCheatModule.class);

   private boolean isAlternateHeadModeEnabled() {
      return antiCheat.is2b2tEnabled();
   }

   public AutoMineHeadProvider(AutoMineModule module) {
      super(module);
   }

   @Override
   public void collectCandidates(AutoMineCandidates candidates) {
      AutoMineTargetSelector targetSelector = this.autoMine.targetSelector;
      Player target = targetSelector.selectTarget();
      if (target != null && !this.hasNoCollisionBelow(target)) {
         Setting<Boolean> inCrawlSetting = this.autoMine.inCrawlSetting;
         Boolean inCrawl = inCrawlSetting.getValue();
         if (inCrawl || !target.isVisuallyCrawling()) {
            Set<BlockPos> occupiedPositions = PlayerStateUtils.getOccupiedBlockPositions(target);
            AABB targetBounds = target.getBoundingBox();
            boolean extraBreak = this.autoMine.isExtraBreakEnabled();
            AABB floorBounds = targetBounds.setMaxY(targetBounds.minY);
            boolean intersectsBedrock = BlockPos.betweenClosedStream(floorBounds)
               .anyMatch(position -> BlockInteractionUtils.getBlock(position) == Blocks.BEDROCK);
            AABB insetBounds = targetBounds.deflate(0.05, 0.05, 0.05);
            if (BlockPos.betweenClosedStream(insetBounds)
               .filter(
                  position -> BlockInteractionUtils.getBlock(position).getExplosionResistance() >= 600.0F
                     && !this.isAlternateHeadModeEnabled()
                     && BlockInteractionUtils.getBlock(position) != Blocks.AIR
               )
               .toList()
               .isEmpty()) {
               AABB upperBounds = targetBounds.setMaxY(targetBounds.maxY + 0.5);
               Optional<BlockPos> upperResult = BlockPos.betweenClosedStream(upperBounds)
                  .map(BlockPos::immutable)
                  .filter(this::isHeadCandidateValid)
                  .max(Comparator.comparing(position -> this.isAlreadyMining(position)));
               BlockPos primaryPosition = upperResult.orElse(null);
               Optional<BlockPos> headResult = PlayerStateUtils.getSurroundPositions(target)
                  .stream()
                  .map(BlockPos::immutable)
                  .map(BlockPos::above)
                  .filter(this::isHeadCandidateValid)
                  .max(Comparator.comparing(position -> this.getCandidatePriority(position, candidates)));
               BlockPos secondaryPosition = headResult.orElse(null);
               if (this.isAlternateHeadModeEnabled() && intersectsBedrock) {
                  secondaryPosition = null;
               }

               if (primaryPosition == null && secondaryPosition != null && !this.isAlternateHeadModeEnabled()) {
                  primaryPosition = secondaryPosition;
                  secondaryPosition = null;
               }

               if (primaryPosition != null) {
                  BlockPos selectedPrimary = primaryPosition;
                  BlockPos selectedSecondary = secondaryPosition;
                  if (this.isAlternateHeadModeEnabled()) {
                     int alternatePriority;
                     if (intersectsBedrock) {
                        if (extraBreak) {
                           alternatePriority = 900;
                        } else {
                           alternatePriority = 1000;
                        }
                     } else {
                        alternatePriority = extraBreak ? 850 : 950;
                     }

                     Consumer<AutoMineCandidates> alternateUpdate = output -> {
                        if (!extraBreak) {
                           output.setPrimaryPosition(selectedPrimary);
                        } else if (selectedSecondary != null) {
                           output.setSecondaryPosition(selectedPrimary);
                           output.setPrimaryPosition(selectedSecondary);
                        } else {
                           output.setPrimaryPosition(selectedPrimary);
                        }
                     };
                     candidates.offer(alternatePriority, alternateUpdate);
                  } else {
                     int normalPriority = 599;
                     BlockPos priorityPosition = primaryPosition;
                     if (this.isAlreadyMining(priorityPosition)) {
                        normalPriority = 650;
                     }

                     Setting<Boolean> trapOverrideSetting = this.autoMine.trapOverrideSetting;
                     if (trapOverrideSetting.getValue() && autoTrap.isToggled()) {
                        normalPriority = 650;
                     }

                     if (target.isVisuallyCrawling() && occupiedPositions.size() == 1) {
                        ClientLevel level = ClientAccess.argusclientMinecraft.level;
                        BlockPos selfPosition = ClientAccess.argusclientMinecraft.player.blockPosition();
                        if (level.getBlockState(selfPosition).isAir()) {
                           Consumer<AutoMineCandidates> crawlUpdate = output -> output.setSecondaryPosition(selectedPrimary);
                           candidates.offer(650, crawlUpdate);
                           return;
                        }
                     }

                     Consumer<AutoMineCandidates> normalUpdate = output -> output.setPrimaryPosition(selectedPrimary);
                     candidates.offer(normalPriority, normalUpdate);
                  }
               }
            }
         }
      }
   }

   private boolean isHeadCandidateValid(BlockPos position) {
      if (this.intersectsPlayer(position)) {
         return false;
      }

      boolean baseAllowed = BlockInteractionUtils.getBlock(position) == Blocks.OBSIDIAN || this.isAlternateHeadModeEnabled();
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      BlockPos abovePosition = position.above();
      boolean aboveAllowed = level.isEmptyBlock(abovePosition) || this.isAlternateHeadModeEnabled();
      return (!WorldUtils.canMineBlock(position) || !baseAllowed) && !this.isAlreadyMining(position) ? false : aboveAllowed;
   }

   private boolean intersectsPlayer(BlockPos position) {
      AABB blockBounds = new AABB(position);
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      Iterator<AbstractClientPlayer> players = level.players().iterator();

      while (players.hasNext()) {
         AABB playerBounds = players.next().getBoundingBox();
         if (BoundingBoxUtils.hasVolumeOverlap(playerBounds, blockBounds)) {
            return true;
         }
      }

      return false;
   }

   private int getCandidatePriority(BlockPos position, AutoMineCandidates candidates) {
      if (this.isAlreadyMining(position)) {
         return 2;
      } else {
         return !position.equals(candidates.getSecondaryPosition()) && !position.equals(candidates.getPrimaryPosition()) ? 0 : 1;
      }
   }

   @Override
   public boolean isEnabled() {
      return this.autoMine.headSetting.getValue();
   }

   public static void bindModules(ModuleRegistry registry) {
      autoTrap = (AutoTrapModule)registry.getModule(AutoTrapModule.class);
      antiCheat = (AntiCheatModule)registry.getModule(AntiCheatModule.class);
   }
}
