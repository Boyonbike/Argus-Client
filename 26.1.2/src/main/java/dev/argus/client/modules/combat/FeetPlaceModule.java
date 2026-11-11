package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.BlockUpdateAppliedEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.WorldStateManager;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.PlacementModule;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.modules.exploit.MultiTaskModule;
import dev.argus.client.modules.player.SpeedMineModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.BlockerTargetOffsets;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.BlockRaycastClassifier;
import dev.argus.client.utils.combat.BlockRaycastOverride;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.combat.ExtraBreakTarget;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.client.utils.movement.WallsVelocityHandler;
import dev.argus.client.utils.world.BlockInteractionUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FeetPlaceModule extends PlacementModule {
   private static AntiCheatModule antiCheat = ClientContext.getModule(AntiCheatModule.class);
   private static SpeedMineModule speedMine = ClientContext.getModule(SpeedMineModule.class);
   private final Setting<Boolean> fireworkSetting = this.add(new BooleanSetting("Firework", false));
   private final Setting<FeetPlaceModule.SequentialMode> sequentialSetting = this.add(new EnumSetting<>("Sequential", FeetPlaceModule.SequentialMode.NONE));
   private final Setting<Boolean> centerSetting = this.add(new BooleanSetting("Center", true));
   private final Setting<Boolean> flattenSetting = this.add(new BooleanSetting("Flatten", true));
   private final Setting<Boolean> coverSetting = this.add(new BooleanSetting("Cover", false));
   private final Setting<Boolean> smartFaceSetting = this.add(new BooleanSetting("SmartFace", false));
   private final Setting<FeetPlaceModule.BlockIgnoreMode> blockIgnoreSetting = this.add(new EnumSetting<>("BlockIgnore", FeetPlaceModule.BlockIgnoreMode.NONE));
   private final Setting<Boolean> onlyObbySetting = this.add(new BooleanSetting("OnlyObby", false));
   private final Setting<Boolean> instantSetting = this.add(new BooleanSetting("Instant", false));
   private final FeetPlaceFireworkSupport fireworkSupport = new FeetPlaceFireworkSupport();
   private boolean recentPositionCorrection;
   private boolean sequentialPlacementComplete;
   private int nearbyCrystalCount;
   private boolean centered;

   @Override
   protected BlockPos getPlacementOrigin() {
      if (!EntityUtils.hasCollisionBelow(ClientAccess.argusclientMinecraft.player)) {
         double x = ClientAccess.argusclientMinecraft.player.getX();
         double y = ClientAccess.argusclientMinecraft.player.getY();
         double z = ClientAccess.argusclientMinecraft.player.getZ();
         return BlockPos.containing(x, y, z);
      } else {
         return super.getPlacementOrigin();
      }
   }

   @Override
   protected boolean disableForJumpOrSleep() {
      return this.recentPositionCorrection ? false : super.disableForJumpOrSleep();
   }

   @Override
   protected boolean shouldDiscardAttackedCrystal() {
      return this.instantSetting.getValue() || RotationManager.isMovementSyncEnabled();
   }

   @Override
   protected int getPlacementRotationPriority() {
      return 999;
   }

   @Override
   protected InventoryUtils.SwapSlot findPlacementSwapSlot() {
      if (this.onlyObbySetting.getValue()) {
         Item obsidian = Items.OBSIDIAN;
         return InventoryUtils.findSwapSlot(obsidian, false);
      } else {
         return super.findPlacementSwapSlot();
      }
   }

   @Override
   public void onEnable() {
      this.nearbyCrystalCount = 0;
      if (!this.isWorldUnavailable()) {
         this.centered = false;
         this.countNearbyCrystals();
      }

      super.onEnable();
      this.fireworkSupport.onEnable();
   }

   private void countNearbyCrystals() {
      Set<BlockPos> occupiedPositions = PlayerStateUtils.getSurroundPositions(ClientAccess.argusclientMinecraft.player);

      for (Entity entity : ClientAccess.argusclientMinecraft.level.entitiesForRendering()) {
         if (entity instanceof EndCrystal) {
            BlockPos crystalBase = entity.blockPosition().below();
            if (occupiedPositions.contains(crystalBase)) {
               this.nearbyCrystalCount++;
            }
         }
      }
   }

   private boolean isIgnoredMiningPosition(BlockPos position) {
      if (!speedMine.isToggled()) {
         return false;
      }

      BlockPos miningPosition = speedMine.getRebreakPos();
      return miningPosition == null ? false : miningPosition.equals(position);
   }

   private boolean isIgnoredBlockPosition(BlockPos position, Set<BlockPos> enemyPositions) {
      return switch ((FeetPlaceModule.BlockIgnoreMode)this.blockIgnoreSetting.getValue()) {
         case NONE -> false;
         case MINING -> this.isIgnoredMiningPosition(position);
         case ENEMY -> enemyPositions.contains(position);
         case BOTH -> this.isIgnoredMiningPosition(position) || enemyPositions.contains(position);
      };
   }

   private Set<BlockPos> collectEnemyPositions() {
      Set<BlockPos> positions = new HashSet<>();

      for (Player player : ClientAccess.argusclientMinecraft.level.players()) {
         if (player.isAlive()
            && player != ClientAccess.argusclientMinecraft.player
            && !(player.distanceTo(ClientAccess.argusclientMinecraft.player) > 4.0F)
            && !ClientContext.socialRegistry.isFriendPlayer(player)) {
            for (BlockPos occupied : PlayerStateUtils.getOccupiedBlockPositions(player)) {
               BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(occupied);
               if (state.isAir() || BlockRaycastClassifier.IGNORE_LOW_RESISTANCE.test(occupied, state) == BlockRaycastOverride.IGNORE) {
                  for (Vec3i offset : BlockerTargetOffsets.SURROUND.getOffsets()) {
                     positions.add(occupied.offset(offset));
                  }
               }
            }
         }
      }

      return positions;
   }

   private int getPlayerHeightInBlocks() {
      double height = ClientAccess.argusclientMinecraft.player.getBbHeight();
      return (int)Math.ceil(height);
   }

   private boolean shouldSkipFlattenPosition(BlockPos position) {
      BlockPos placementPosition = ClientContext.worldStateManager.getUnconfirmedBreak();
      if (ClientContext.holeManager.intersectsHole(position) && !ClientAccess.argusclientMinecraft.player.onGround()) {
         return true;
      }

      if (PlayerStateUtils.isLocalLowProfile() && !antiCheat.is2b2tEnabled()) {
         return true;
      }

      if (!position.equals(placementPosition)) {
         if (speedMine.getExtraBreakTarget() != null) {
            ExtraBreakTarget miningAction = speedMine.getExtraBreakTarget();
            if (position.equals(miningAction.getPosition())) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private Comparator<BlockPos> getPlacementComparator() {
      double latency = ClientContext.movementSpeedTracker.getCurrentSpeed();
      Comparator<BlockPos> directionComparator;
      if (latency <= 10.0) {
         Function<BlockPos, Float> playerYawKey = position -> {
            float playerYaw = ClientAccess.argusclientMinecraft.player.getYRot();
            BlockPos targetPosition = position;
            float capturedYaw = playerYaw;
            return this.getPlacementPriority(capturedYaw, targetPosition);
         };
         directionComparator = Comparator.comparing(playerYawKey);
      } else {
         Function<BlockPos, Float> movementYawKey = position -> {
            float movementYaw = MovementPhysics.getMovementYaw();
            BlockPos targetPosition = position;
            float capturedYaw = movementYaw;
            return this.getYawPlacementCost(capturedYaw, targetPosition);
         };
         directionComparator = Comparator.comparing(movementYawKey);
      }

      Function<BlockPos, Boolean> pendingPlacementKey = candidate -> {
         WorldStateManager worldState = ClientContext.worldStateManager;
         BlockPos targetPosition = candidate;
         boolean pendingPlacement = worldState.hasPendingPlacement(targetPosition);
         return pendingPlacement;
      };
      Comparator<BlockPos> pendingPlacementComparator = Comparator.comparing(pendingPlacementKey);
      return pendingPlacementComparator.thenComparing(directionComparator);
   }

   public static boolean centerPlayer() {
      if (!ClientAccess.argusclientMinecraft.player.onGround()) {
         return false;
      }

      if (ClientContext.holeManager.isInHole()) {
         return false;
      }

      Set<BlockPos> occupiedPositions = PlayerStateUtils.getSurroundPositions(ClientAccess.argusclientMinecraft.player);
      int occupiedPositionCount = occupiedPositions.size();
      if (occupiedPositionCount <= 4) {
         return false;
      }

      if (WallsVelocityHandler.hasRaisedPlayerCollision()) {
         return false;
      }

      Vec3 originalPosition = ClientAccess.argusclientMinecraft.player.position();
      Vec3 centerPosition = new Vec3(
         Math.floor(ClientAccess.argusclientMinecraft.player.getX()) + 0.5,
         ClientAccess.argusclientMinecraft.player.getY(),
         Math.floor(ClientAccess.argusclientMinecraft.player.getZ()) + 0.5
      );
      Vec3 centerDelta = centerPosition.subtract(originalPosition);
      double distance = centerDelta.length();
      double baseMoveSpeed = MovementPhysics.getBaseMoveSpeed(false);
      double maximumStep = baseMoveSpeed + 0.02;
      if (distance > maximumStep) {
         double stepFraction = maximumStep / distance;
         Vec3 remainingDelta = centerPosition.subtract(originalPosition);
         Vec3 stepDelta = remainingDelta.scale(stepFraction);
         centerPosition = originalPosition.add(stepDelta);
      }

      ClientAccess.argusclientMinecraft.player.setPos(centerPosition);
      double movedDistance = originalPosition.distanceTo(centerPosition);
      return movedDistance <= 0.01;
   }

   @Override
   protected void runPlacementCycle() {
      if (!ClientAccess.argusclientMinecraft.player.isPassenger()) {
         ElapsedTimer timer = ClientContext.serverStateManager.getTeleportAckTimer();
         this.recentPositionCorrection = !timer.hasElapsed(100L);
         if (this.recentPositionCorrection) {
            LocalPlayer heightPlayer = ClientAccess.argusclientMinecraft.player;
            this.placementBaselineY = heightPlayer.getY();
         }

         try {
            if (this.fireworkSetting.getValue()) {
               MultiTaskModule multiTask = multiTaskModule;
               if (!multiTask.isItemUseRestricted()) {
                  Minecraft client = ClientAccess.argusclientMinecraft;
                  Runnable processFireworks = () -> {
                     FeetPlaceFireworkSupport support = this.fireworkSupport;
                     Setting<Boolean> placementAirPlaceSettingSnapshot = this.placementAirPlaceSetting;
                     boolean airPlaceEnabled = placementAirPlaceSettingSnapshot.getValue();
                     Setting<Boolean> raytraceSetting = this.placementRaytraceSetting;
                     boolean raytraceEnabled = raytraceSetting.getValue();
                     support.processFireworks(airPlaceEnabled, raytraceEnabled);
                  };
                  client.execute(processFireworks);
               }
            }

            super.runPlacementCycle();
         } catch (Throwable var5) {
         }
      }
   }

   private List<BlockPos> collectPlacementPositions(List<Integer> visitedHashes, BlockPos anchor, int offsetY) {
      InventoryUtils.SwapSlot blockSlot = this.findPlacementSwapSlot();
      Block defaultPlacementBlock = Blocks.OBSIDIAN;
      BlockState placementState = defaultPlacementBlock.defaultBlockState();
      if (blockSlot.isFound()) {
         ItemStack placementStack = blockSlot.getStack();
         if (placementStack.getItem() instanceof BlockItem blockItem) {
            Block placementBlock = blockItem.getBlock();
            placementState = placementBlock.defaultBlockState();
         }
      }

      List<BlockPos> placements = new ArrayList<>();
      int anchorHashCode = anchor.hashCode();
      Integer anchorHash = anchorHashCode;
      visitedHashes.add(anchorHash);

      for (Vec3 offset : this.getRelativePlacementOffsets(offsetY)) {
         BlockPos blockOffset = BlockPos.containing(offset);
         BlockPos position = anchor.offset(blockOffset);
         if (this.flattenSetting.getValue()) {
            double flattenOffsetY = offset.y;
            if (flattenOffsetY == -1.0 && this.shouldSkipFlattenPosition(position)) {
               continue;
            }
         }

         if (this.coverSetting.getValue()) {
            double coverOffsetY = offset.y();
            if (coverOffsetY == this.getPlayerHeightInBlocks() && PlayerStateUtils.isLocalLowProfile() && !RotationManager.is2b2tMode()) {
               continue;
            }
         }

         BlockState collisionState = placementState;
         ClientLevel collisionLevel = ClientAccess.argusclientMinecraft.level;
         BlockPos collisionOrigin = BlockPos.ZERO;
         VoxelShape collisionShape = collisionState.getCollisionShape(collisionLevel, collisionOrigin);
         AABB workingBounds;
         if (collisionShape.isEmpty()) {
            workingBounds = new AABB(position);
         } else {
            workingBounds = collisionShape.bounds();
            workingBounds = BoundingBoxUtils.placeAtBlock(workingBounds, position);
         }

         AABB placementBounds = workingBounds;
         this.breakInstantlyMineableBlock(position);
         BlockState positionState = ClientAccess.argusclientMinecraft.level.getBlockState(position);
         boolean occupied = !positionState.canBeReplaced();
         if (ClientContext.worldStateManager.hasActiveGlassUpdate(position)) {
            occupied = false;
         }

         if (occupied) {
            workingBounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
            AABB positionBounds = new AABB(position);
            if (!BoundingBoxUtils.hasVolumeOverlap(workingBounds, positionBounds)) {
               continue;
            }
         }

         ClientLevel entityLevel = ClientAccess.argusclientMinecraft.level;
         Class<LivingEntity> entityType = LivingEntity.class;
         Predicate<LivingEntity> blocksPlacement = entity -> {
            if (entity != ClientAccess.argusclientMinecraft.player) {
               LivingEntity candidateEntity = entity;
               AABB entityBounds = candidateEntity.getBoundingBox();
               if (!BoundingBoxUtils.hasVolumeOverlap(placementBounds, entityBounds)) {
                  return false;
               }
            }

            return true;
         };
         List<LivingEntity> blockingEntities = entityLevel.getEntitiesOfClass(entityType, placementBounds, blocksPlacement);
         if (!blockingEntities.isEmpty()) {
            int positionHashCode = position.hashCode();
            Integer positionHash = positionHashCode;
            if (!visitedHashes.contains(positionHash) && offset.y == offsetY) {
               placements.addAll(this.collectPlacementPositions(visitedHashes, position, offsetY));
               continue;
            }
         }

         if (!occupied) {
            boolean strictDirection = BlockInteractionUtils.isStrictDirectionEnabled();
            Direction placementFace = BlockInteractionUtils.findSupportDirection(position, strictDirection);
            if (placementFace == null) {
               Setting<Boolean> placementAirPlaceSettingSnapshot = this.placementAirPlaceSetting;
               if (!placementAirPlaceSettingSnapshot.getValue()) {
                  boolean strictSupportDirection = BlockInteractionUtils.isStrictDirectionEnabled();
                  BlockPos supportPosition = BlockInteractionUtils.findSupportPosition(position, 2, true, strictSupportDirection);
                  if (supportPosition != null) {
                     placements.add(supportPosition);
                  }
               }
            }

            placements.add(position);
         }
      }

      return placements;
   }

   @Override
   protected void sortPlacementPositions(List positions) {
      positions.sort(this.getPlacementComparator());
   }

   private List<Vec3> getRelativePlacementOffsets(int offsetY) {
      List<Vec3> offsets = new ArrayList<>(
         List.of(new Vec3(-1.0, offsetY, 0.0), new Vec3(1.0, offsetY, 0.0), new Vec3(0.0, offsetY, 1.0), new Vec3(0.0, offsetY, -1.0))
      );
      if (this.nearbyCrystalCount > 3 && this.smartFaceSetting.getValue()) {
         Stream<Vec3> offsetStream = offsets.stream();
         Function<Vec3, Vec3> raiseOffset = offset -> offset.add(0.0, 1.0, 0.0);
         Stream<Vec3> raisedOffsetStream = offsetStream.map(raiseOffset);
         List<Vec3> raisedOffsets = raisedOffsetStream.toList();
         offsets.addAll(raisedOffsets);
      }

      if (this.flattenSetting.getValue()) {
         Vec3 flattenOffset = new Vec3(0.0, offsetY - 1, 0.0);
         offsets.add(flattenOffset);
      }

      if (this.coverSetting.getValue()) {
         Vec3 coverOffset = new Vec3(0.0, offsetY + this.getPlayerHeightInBlocks(), 0.0);
         offsets.add(coverOffset);
      }

      return offsets;
   }

   private float getPlacementPriority(float yaw, BlockPos position) {
      int positionY = position.getY();
      BlockPos playerBlockPosition = PlayerStateUtils.getLocalRoundedBlockPosition();
      if (positionY == playerBlockPosition.getY()) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         if (this.hasNontrivialSurroundCollision(player)) {
            BlockPos distanceOrigin = PlayerStateUtils.getLocalRoundedBlockPosition();
            double targetX = position.getX();
            double targetY = position.getY();
            double targetZ = position.getZ();
            return (float)distanceOrigin.distToLowCornerSqr(targetX, targetY, targetZ);
         }
      }

      float capturedYaw = yaw;
      return this.getYawPlacementCost(capturedYaw, position);
   }

   @EventHandler
   private void runSequentialPlacement(BlockUpdateAppliedEvent event) {
      if (this.sequentialSetting.getValue() != FeetPlaceModule.SequentialMode.NONE
         && (this.sequentialSetting.getValue() == FeetPlaceModule.SequentialMode.STRONG || !this.sequentialPlacementComplete)) {
         this.runPlacementCycle();
         this.sequentialPlacementComplete = this.hasPlacedThisCycle();
      }
   }

   @EventHandler
   private void onLocalPlayerPreTick(LocalPlayerPreTickEvent event) {
      this.sequentialPlacementComplete = false;
      if (!this.centered && this.centerSetting.getValue() && !ClientAccess.argusclientMinecraft.player.isPassenger()) {
         this.centered = centerPlayer();
      }
   }

   @Override
   public List getPlacementPositions() {
      Boolean fireworkValue = this.fireworkSetting.getValue();
      Set<BlockPos> trackedPositions;
      if (fireworkValue) {
         trackedPositions = EntityUtils.getFireworkPositions();
      } else {
         trackedPositions = Collections.emptySet();
      }

      Set<BlockPos> fireworkPositions = trackedPositions;
      List<Integer> visitedHashes = new ArrayList<>();
      BlockPos placementOrigin = this.getPlacementOrigin();
      List<BlockPos> positions = this.collectPlacementPositions(visitedHashes, placementOrigin, 0);
      Set<BlockPos> enemyPositions = this.blockIgnoreSetting.getValue() != FeetPlaceModule.BlockIgnoreMode.ENEMY
            && this.blockIgnoreSetting.getValue() != FeetPlaceModule.BlockIgnoreMode.BOTH
         ? Collections.emptySet()
         : this.collectEnemyPositions();
      Predicate<BlockPos> discardPosition = position -> !this.isIgnoredBlockPosition(position, enemyPositions)
         ? this.fireworkSetting.getValue() && this.fireworkSupport.isTrackedFireworkPosition(fireworkPositions, position)
         : true;
      positions.removeIf(discardPosition);
      return positions;
   }

   @EventHandler
   private void observeSurroundPackets(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket positionPacket) {
         Vec3 playerPosition = ClientAccess.argusclientMinecraft.player.position();
         PositionMoveRotation packetChange = positionPacket.change();
         Vec3 packetPosition = packetChange.position();
         double distanceSquared = playerPosition.distanceToSqr(packetPosition);
         if (distanceSquared > 9.0) {
            PositionMoveRotation freshPacketChange = positionPacket.change();
            this.placementBaselineY = freshPacketChange.position().y;
            ElapsedTimer correctionTimer = ClientContext.serverStateManager.getTeleportAckTimer();
            correctionTimer.reset();
         }
      }

      if (event.getPacket() instanceof ClientboundAddEntityPacket entityPacket
         && entityPacket.getType() == EntityType.END_CRYSTAL
         && this.smartFaceSetting.getValue()) {
         double spawnX = entityPacket.getX();
         double spawnY = entityPacket.getY();
         double spawnZ = entityPacket.getZ();
         BlockPos crystalPosition = BlockPos.containing(spawnX, spawnY, spawnZ);
         Minecraft client = ClientAccess.argusclientMinecraft;
         Runnable countSpawnedCrystal = () -> {
            Set<BlockPos> spawnSurroundPositions = PlayerStateUtils.getSurroundPositions(ClientAccess.argusclientMinecraft.player);
            BlockPos crystalBase = crystalPosition.below();
            if (spawnSurroundPositions.contains(crystalBase)) {
               this.nearbyCrystalCount++;
            }
         };
         client.execute(countSpawnedCrystal);
      }

      if (this.fireworkSetting.getValue()) {
         this.fireworkSupport.observeSpawnPacket(event);
      }
   }

   private float getYawPlacementCost(float yaw, BlockPos position) {
      Vec3 targetCenter = position.getCenter();
      float targetYaw = RotationManager.getRotationsToPosition(targetCenter)[0];
      float yawDifference = Mth.degreesDifferenceAbs(yaw, targetYaw);
      float height = position.getY();
      return yawDifference + height * 0.001F;
   }

   private boolean hasNontrivialSurroundCollision(LivingEntity entity) {
      for (BlockPos position : PlayerStateUtils.getOccupiedBlockPositions(entity)) {
         BlockState blockState = ClientAccess.argusclientMinecraft.level.getBlockState(position);
         ClientLevel collisionLevel = ClientAccess.argusclientMinecraft.level;
         BlockPos collisionOrigin = BlockPos.ZERO;
         VoxelShape shape = blockState.getCollisionShape(collisionLevel, collisionOrigin);
         if (!shape.isEmpty()) {
            AABB bounds = shape.bounds();
            if (!BoundingBoxUtils.hasUnitDimensions(bounds)) {
               return true;
            }
         }
      }

      return false;
   }

   @Override
   protected boolean shouldHandleCrystalSpawn() {
      return RotationManager.isMovementSyncEnabled() ? this.instantSetting.getValue() : ClientContext.crystalInteractionManager.isInstantAttackEnabled();
   }

   public static void bindReferencedModules(ModuleRegistry modules) {
      antiCheat = (AntiCheatModule)modules.getModule(AntiCheatModule.class);
      speedMine = (SpeedMineModule)modules.getModule(SpeedMineModule.class);
   }

   public FeetPlaceModule() {
      super("FeetPlace", "Surrounds your feet with obby.", ModuleCategory.COMBAT, "surround");
      this.setDisableOnRespawn(true);
   }

   private enum BlockIgnoreMode implements NamedEntry {
      NONE("None"),
      MINING("Mining"),
      ENEMY("Enemy"),
      BOTH("Both");

      private final String displayName;

      BlockIgnoreMode(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }

   private enum SequentialMode implements NamedEntry {
      NONE("None"),
      STRICT("Strict"),
      STRONG("Strong");

      private final String displayName;

      SequentialMode(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }
}
