package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.entity.PlayerState;
import dev.argus.client.utils.world.WorldUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

public final class CrystalPlacementValidator implements ClientAccess {
   private boolean requireUpperAir;
   private boolean checkEntityCollisions = true;
   private boolean blockNearbyCrystals;
   private boolean allowFire = true;
   private boolean lowerHitbox;
   private boolean expandMovingPlayers;
   private boolean ignoreBaseBlock;
   private boolean ignoreYoungItems;
   private int predictionTicks;
   private float ignoredCrystalRangeSquared = 16.0F;

   public boolean canPlace(BlockPos base) {
      if (!this.ignoreBaseBlock && !WorldUtils.isBedrockOrObsidian(base)) {
         return false;
      }

      BlockPos crystalSpace = base.offset(0, 1, 0);
      BlockState spaceState = ClientAccess.argusclientMinecraft.level.getBlockState(crystalSpace);
      Block spaceBlock = spaceState.getBlock();
      boolean clearSpace;
      if (spaceBlock instanceof AirBlock) {
         clearSpace = true;
      } else {
         clearSpace = crystalSpace.equals(ClientContext.worldStateManager.getUnconfirmedBreak());
      }

      FluidState fluid = spaceState.getFluidState();
      if (!fluid.isEmpty()) {
         clearSpace = false;
      }

      if (this.allowFire) {
         if (!clearSpace && spaceBlock != Blocks.FIRE) {
            return false;
         }
      } else if (!clearSpace) {
         return false;
      }

      if (this.requireUpperAir) {
         ClientLevel upperLevel = ClientAccess.argusclientMinecraft.level;
         BlockPos upperSpace = base.offset(0, 2, 0);
         BlockState upperState = upperLevel.getBlockState(upperSpace);
         if (!(upperState.getBlock() instanceof AirBlock)) {
            return false;
         }
      }

      AABB spaceBounds = new AABB(crystalSpace);
      int spaceY = crystalSpace.getY();
      int hitboxHeight;
      if (this.lowerHitbox) {
         hitboxHeight = 1;
      } else {
         hitboxHeight = 2;
      }

      double top = spaceY + hitboxHeight;
      AABB collisionBox = spaceBounds.setMaxY(top);
      int collisionCount = 0;

      for (Entity entity : ClientAccess.argusclientMinecraft.level.entitiesForRendering()) {
         if (entity != null && !entity.isRemoved() && entity.isAlive()) {
            AABB entityBounds = entity.getBoundingBox();
            if (entity instanceof Player && entity != ClientAccess.argusclientMinecraft.player) {
               entityBounds = BoundingBoxUtils.getInterpolationBounds((LivingEntity)entity);
               if (this.expandMovingPlayers && !((PlayerState)entity).argusclient$isNextToWall()) {
                  entityBounds = entityBounds.inflate(1.0E-4, 0.0, 1.0E-4);
               }

               for (int predictionTick = 1; predictionTick <= this.predictionTicks; predictionTick++) {
                  Player predictionPlayer = (Player)entity;
                  AABB predictedBox = ClientContext.movementPredictionManager.getPredictedBoundingBox(predictionPlayer, predictionTick);
                  double currentTop = entityBounds.maxY;
                  AABB predictedTop = predictedBox.setMaxY(currentTop);
                  double currentBottom = entityBounds.minY;
                  AABB predictedBounds = predictedTop.setMinY(currentBottom);
                  if (predictedBounds.intersects(collisionBox)) {
                     collisionCount++;
                  }
               }
            } else {
               if (entity instanceof EndCrystal
                  && !this.blockNearbyCrystals
                  && ClientAccess.argusclientMinecraft.player.getEyePosition().distanceToSqr(entity.position()) <= this.ignoredCrystalRangeSquared) {
                  continue;
               }

               if (entity instanceof ItemEntity && this.ignoreYoungItems) {
                  int itemAge = entity.tickCount;
                  if (itemAge < 3) {
                     continue;
                  }
               }
            }

            if (entityBounds.intersects(collisionBox)) {
               collisionCount++;
               break;
            }
         }
      }

      return !this.checkEntityCollisions || collisionCount == 0;
   }

   public CrystalPlacementValidator requireUpperAir(boolean enabled) {
      this.requireUpperAir = enabled;
      return this;
   }

   public CrystalPlacementValidator checkEntityCollisions(boolean enabled) {
      this.checkEntityCollisions = enabled;
      return this;
   }

   public CrystalPlacementValidator blockNearbyCrystals(boolean enabled) {
      this.blockNearbyCrystals = enabled;
      return this;
   }

   public CrystalPlacementValidator allowFire(boolean enabled) {
      this.allowFire = enabled;
      return this;
   }

   public CrystalPlacementValidator lowerHitbox(boolean enabled) {
      this.lowerHitbox = enabled;
      return this;
   }

   public CrystalPlacementValidator expandMovingPlayers(boolean enabled) {
      this.expandMovingPlayers = enabled;
      return this;
   }

   public CrystalPlacementValidator ignoreBaseBlock(boolean enabled) {
      this.ignoreBaseBlock = enabled;
      return this;
   }

   public CrystalPlacementValidator ignoreYoungItems(boolean enabled) {
      this.ignoreYoungItems = enabled;
      return this;
   }

   public CrystalPlacementValidator predictionTicks(int ticks) {
      this.predictionTicks = ticks;
      return this;
   }

   public CrystalPlacementValidator ignoredCrystalRange(float range) {
      this.ignoredCrystalRangeSquared = range * range;
      return this;
   }
}
