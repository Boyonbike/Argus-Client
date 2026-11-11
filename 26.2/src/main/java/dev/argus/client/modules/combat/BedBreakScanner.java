package dev.argus.client.modules.combat;

import net.minecraft.util.Mth;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.utils.combat.DamageUtils;
import dev.argus.client.utils.entity.EntityUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;

public class BedBreakScanner implements ClientAccess {
   private final BedAuraModule module;
   private Vec3 eyePosition;
   private float range;
   private float minimumDamage;
   private float maximumSelfDamage;

   private void refreshSettings() {
      LocalPlayer eyePlayer = ClientAccess.argusclientMinecraft.player;
      this.eyePosition = eyePlayer.getEyePosition();
      Float rangeValue = this.module.breakRangeSetting.getValue();
      this.range = rangeValue;
      Float minimumValue = this.module.minDamageSetting.getValue();
      this.minimumDamage = minimumValue;
      Float maximumValue = this.module.maxSelfDamageSetting.getValue();
      this.maximumSelfDamage = maximumValue;
   }

   public BlockPos scanBreakPosition(LivingEntity target) {
      this.refreshSettings();
      float bestDamage = 0.0F;
      BlockPos bestPosition = null;
      BlockPos eyeBlock = BlockPos.containing(this.eyePosition);
      int rangeBlocks = Mth.ceil(this.range);

      for (BlockPos candidate : BlockPos.betweenClosed(
         eyeBlock.offset(-rangeBlocks, -rangeBlocks, -rangeBlocks),
         eyeBlock.offset(rangeBlocks, rangeBlocks, rangeBlocks))) {
         BlockState stateSnapshot = ClientAccess.argusclientMinecraft.level.getBlockState(candidate);
         if (!(stateSnapshot.getBlock() instanceof BedBlock)) {
            continue;
         }
         EnumProperty partProperty = BedBlock.PART;
         if ((BedPart)stateSnapshot.getValue(partProperty) == BedPart.FOOT) {
            continue;
         }
         Vec3 rangeCenterSnapshot = Vec3.atCenterOf(candidate);
         if (rangeCenterSnapshot.distanceTo(this.eyePosition) > this.range) {
            continue;
         }
         Vec3 targetCenterSnapshot = Vec3.atCenterOf(candidate);
         LivingEntity damageTarget = target;
         float targetDamage = DamageUtils.getBedDamage(targetCenterSnapshot, damageTarget);
         Vec3 selfCenterSnapshot = Vec3.atCenterOf(candidate);
         LocalPlayer selfPlayer = ClientAccess.argusclientMinecraft.player;
         float selfDamage = DamageUtils.getBedDamage(selfCenterSnapshot, selfPlayer);
         if ((!(selfDamage >= EntityUtils.getPlayerHealthWithAbsorption()) || !this.module.antiSuicideSetting.getValue())
            && !(selfDamage > this.maximumSelfDamage)
            && !(targetDamage < this.minimumDamage)
            && (bestPosition == null || targetDamage > bestDamage)) {
            bestPosition = candidate.immutable();
            bestDamage = targetDamage;
         }
      }

      return bestPosition;
   }

   public BedBreakScanner(BedAuraModule module) {
      this.module = module;
   }
}
