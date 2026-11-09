package dev.argus.client.modules.render;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.MultiPlayerGameModeUseItemOnEvent;
import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public class LootedStorageTracker implements ClientAccess {
   private static final Color LOOTED_COLOR = new Color(217, 199, 255);
   private final Set<BlockPos> lootedPositions = new HashSet<>(100);

   public static Color getLootedColor() {
      return LOOTED_COLOR;
   }

   void onBlockUpdate(BlockPos position, BlockState state) {
      if (state.isAir()) {
         this.lootedPositions.remove(position);
      } else {
         EnumProperty typeProperty = ChestBlock.TYPE;
         ChestType selectedChestType;
         if (state.hasProperty(typeProperty)) {
            EnumProperty<ChestType> chestTypeProperty = ChestBlock.TYPE;
            selectedChestType = state.getValue(chestTypeProperty);
         } else {
            selectedChestType = ChestType.SINGLE;
         }

         ChestType chestType = selectedChestType;
         if (chestType != ChestType.SINGLE) {
            Set<BlockPos> positionsToCheck = this.lootedPositions;
            Direction partnerDirection = this.getPartnerDirection(chestType, state);
            if (positionsToCheck.contains(position.relative(partnerDirection))) {
               this.lootedPositions.add(position);
            }
         }
      }
   }

   public boolean isLooted(BlockEntity blockEntity) {
      if (this.isTrackedStorage(blockEntity)) {
         Set<BlockPos> positions = this.lootedPositions;
         if (positions.contains(blockEntity.getBlockPos())) {
            return true;
         }
      }

      return false;
   }

   private Direction getPartnerDirection(ChestType chestType, BlockState state) {
      EnumProperty<Direction> facingProperty = ChestBlock.FACING;
      Direction facing = state.getValue(facingProperty);
      float facingYaw = facing.toYRot();
      int yawOffset = 90;
      float partnerYaw = facingYaw + yawOffset * (chestType == ChestType.LEFT ? 1 : -1);
      double wrappedYaw = Mth.wrapDegrees(partnerYaw);
      return Direction.fromYRot(wrappedYaw);
   }

   private boolean isTrackedStorage(BlockEntity blockEntity) {
      return blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity;
   }

   void onUseItemOn(MultiPlayerGameModeUseItemOnEvent event) {
      if (!event.isCancelled()) {
         LocalPlayer sneakCheckPlayer = ClientAccess.argusclientMinecraft.player;
         if (!sneakCheckPlayer.isShiftKeyDown()) {
            BlockHitResult hit = event.getHitResult();
            BlockPos position = hit.getBlockPos();
            ClientLevel entityLevel = ClientAccess.argusclientMinecraft.level;
            BlockEntity blockEntity = entityLevel.getBlockEntity(position);
            if (this.isTrackedStorage(blockEntity)) {
               if (this.lootedPositions.contains(position)) {
                  return;
               }

               ClientLevel stateLevel = ClientAccess.argusclientMinecraft.level;
               BlockState state = stateLevel.getBlockState(position);
               EnumProperty typeProperty = ChestBlock.TYPE;
               ChestType selectedChestType;
               if (state.hasProperty(typeProperty)) {
                  EnumProperty<ChestType> chestTypeProperty = ChestBlock.TYPE;
                  selectedChestType = state.getValue(chestTypeProperty);
               } else {
                  selectedChestType = ChestType.SINGLE;
               }

               ChestType chestType = selectedChestType;
               if (chestType != ChestType.SINGLE) {
                  Set<BlockPos> positionsToMark = this.lootedPositions;
                  Direction partnerDirection = this.getPartnerDirection(chestType, state);
                  positionsToMark.add(position.relative(partnerDirection));
               }

               this.lootedPositions.add(position);
            }

            return;
         }
      }
   }
}
