package dev.argus.client.utils.inventory;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.modules.combat.AutoMineModule;
import dev.argus.client.modules.combat.AutoMineTargetSelector;
import dev.argus.client.modules.player.SpeedMineModule;
import dev.argus.client.utils.combat.DamageUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class AutoMineTerrainProvider extends AutoMineProviderBase {
   private static SpeedMineModule speedMine = ClientContext.getModule(SpeedMineModule.class);

   private boolean hasClearColumn(BlockPos position) {
      BlockPos abovePosition = position.above();
      if (!WorldUtils.isAir(abovePosition)) {
         return false;
      }

      AABB columnBounds = new AABB(position).expandTowards(0.0, 1.0, 0.0);
      ClientLevel level = ClientAccess.argusclientMinecraft.level;

      for (Entity entity : level.entitiesForRendering()) {
         if (!entity.isRemoved() && entity.isAlive() && !(entity instanceof EndCrystal) && entity.getBoundingBox().intersects(columnBounds)) {
            return false;
         }
      }

      return true;
   }

   public AutoMineTerrainProvider(AutoMineModule module) {
      super(module);
   }

   private BlockPos selectTerrainCandidate(Player target, BlockPos basePosition, BlockPos referencePosition) {
      Set<BlockPos> occupiedPositions = PlayerStateUtils.getSurroundPositions(target);
      BlockPos minimumPosition = basePosition.offset(-2, -1, -2);
      BlockPos maximumPosition = basePosition.offset(2, -1, 2);
      List<AutoMineTerrainProvider.TerrainMiningCandidate> scoredCandidates = BlockPos.betweenClosedStream(minimumPosition, maximumPosition)
         .map(BlockPos::immutable)
         .filter(position -> !occupiedPositions.contains(position.above()))
         .filter(position -> WorldUtils.isReplaceable(position) || this.canMinePosition(position))
         .filter(position -> this.hasClearColumn(position.above()) || WorldUtils.isAir(position))
         .map(position -> AutoMineTerrainProvider.TerrainMiningCandidate.create(position, referencePosition, target))
         .toList();
      if (scoredCandidates.stream().anyMatch(candidate -> WorldUtils.isReplaceable(candidate.position()) && candidate.damage() >= 6.0)) {
         return null;
      }

      AutoMineTerrainProvider.TerrainMiningCandidate selectedCandidate = scoredCandidates.stream()
         .max(Comparator.comparingDouble(AutoMineTerrainProvider.TerrainMiningCandidate::getPriorityScore))
         .orElse(null);
      if (selectedCandidate != null && !WorldUtils.isAir(selectedCandidate.position())) {
         if (!selectedCandidate.currentlyMining() && selectedCandidate.damage() <= 4.0) {
            return null;
         } else {
            return !WorldUtils.isBedrockOrObsidian(selectedCandidate.position()) && !WorldUtils.isReplaceable(selectedCandidate.position())
               ? selectedCandidate.position()
               : null;
         }
      } else {
         return null;
      }
   }

   @Override
   public void collectCandidates(AutoMineCandidates candidates) {
      AutoMineTargetSelector targetSelector = this.autoMine.targetSelector;
      Player target = targetSelector.selectTarget();
      if (target != null && !this.hasNoCollisionBelow(target)) {
         BlockPos targetPosition = BlockPos.containing(target.position());
         ClientLevel level = ClientAccess.argusclientMinecraft.level;
         Block baseBlock = level.getBlockState(targetPosition).getBlock();
         boolean resistantBase = baseBlock.getExplosionResistance() >= 600.0F && baseBlock != Blocks.AIR;
         boolean occupiedPolicy = PlayerStateUtils.getSurroundPositions(target).stream().anyMatch(position -> !WorldUtils.isBedrockOrObsidian(position));
         if (!resistantBase || occupiedPolicy) {
            if (candidates.getPrimaryPosition() != null) {
               if (candidates.getPrimaryPosition().getY() == targetPosition.getY()) {
                  BlockPos referencePosition = occupiedPolicy ? candidates.getSecondaryPosition() : candidates.getPrimaryPosition();
                  BlockPos candidatePosition = this.selectTerrainCandidate(target, targetPosition, referencePosition);
                  if (candidatePosition != null) {
                     int priority;
                     if (occupiedPolicy) {
                        priority = 700;
                     } else {
                        priority = 600;
                     }

                     candidates.offer(priority, output -> {
                        if (occupiedPolicy) {
                           output.setPrimaryPosition(candidatePosition);
                        } else {
                           output.setSecondaryPosition(candidatePosition);
                        }
                     });
                  }
               }
            }
         }
      }
   }

   @Override
   public boolean isEnabled() {
      return this.autoMine.terrainSetting.getValue();
   }

   public static void bindModules(ModuleRegistry registry) {
      speedMine = (SpeedMineModule)registry.getModule(SpeedMineModule.class);
   }

   private record TerrainMiningCandidate(BlockPos position, double damage, boolean currentlyMining) {
      static AutoMineTerrainProvider.TerrainMiningCandidate create(BlockPos position, BlockPos referencePosition, Player target) {
         Vec3 elevatedCenter = position.getCenter().add(0.0, 0.5, 0.0);
         AABB targetBounds = target.getBoundingBox();
         BlockPos ignoredSupport = position.below();
         float damage = DamageUtils.getExplosionDamage(elevatedCenter, target, targetBounds, 6.0, true, referencePosition, ignoredSupport);
         SpeedMineModule presenceSpeedMine = AutoMineTerrainProvider.speedMine;
         boolean currentlyMining = false;
         if (presenceSpeedMine.getExtraBreakTarget() != null) {
            currentlyMining = AutoMineTerrainProvider.speedMine.getExtraBreakTarget().getPosition().equals(position);
         }

         return new AutoMineTerrainProvider.TerrainMiningCandidate(position, damage, currentlyMining);
      }

      public double getPriorityScore() {
         return this.currentlyMining ? Double.MAX_VALUE : this.damage;
      }
   }
}
