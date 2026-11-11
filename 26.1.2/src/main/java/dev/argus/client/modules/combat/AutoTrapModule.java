package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.commands.FakeplayerCommand;
import dev.argus.client.events.BlockUpdateAppliedEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.PlacementModule;
import dev.argus.client.modules.render.LogoutSpotsModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.combat.EndCrystalState;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.entity.SidePlayer;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.client.utils.movement.WallsVelocityHandler;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class AutoTrapModule extends PlacementModule {
   private static LogoutSpotsModule logoutSpotsModule = ClientContext.getModule(LogoutSpotsModule.class);
   protected final Setting<Float> rangeSetting;
   private final Setting<Boolean> sequentialSetting;
   protected final Setting<Boolean> targetsSetting;
   protected final Setting<Boolean> headSetting;
   protected final Setting<Boolean> faceSetting;
   protected final Setting<Boolean> legsSetting;
   protected final Setting<Boolean> belowSetting;
   protected final Setting<Boolean> antiStepSetting;
   protected final Setting<Boolean> onlyPhasedSetting;
   protected final Setting<Boolean> ignoreNakedsSetting;
   protected final Setting<Boolean> ignoreCrawlSetting;
   protected final Setting<Boolean> logoutsSetting;
   private final Setting<Boolean> onlyObbySetting;
   private final Setting<Boolean> completeSetting;
   private final TrapMiningSupport miningSupport;
   private Player targetPlayer;
   private boolean sequentialHandled;
   private boolean miningSupportMode;
   boolean miningSupportAllowed;

   protected boolean isInsideTargetTopBounds(BlockPos blockPos) {
      if (this.targetPlayer == null) {
         return false;
      }

      Player targetSnapshot = this.targetPlayer;
      AABB boundsSnapshot = this.getTargetTopBounds(targetSnapshot);
      Vec3 blockCenter = blockPos.getCenter();
      return boundsSnapshot.contains(blockCenter);
   }

   protected Vec3 getTargetPosition(Player player) {
      Vec3 currentPosition = player.position();
      if (!(player instanceof FakeplayerCommand.SimulatedPlayer)
         && !(player instanceof SidePlayer)
         && player != ClientAccess.argusclientMinecraft.player
         && RotationManager.isMovementSyncEnabled()) {
         int latencyTicks = ClientContext.serverStateManager.getLatencyTicks();
         int predictionTicks = Mth.clamp(latencyTicks, 0, 5);
         AABB predictedBounds = ClientContext.movementPredictionManager.getPredictedBoundingBox(player, predictionTicks);
         return BoundingBoxUtils.getBottomCenter(predictedBounds);
      } else {
         return currentPosition;
      }
   }

   private double getSupportSortKey(BlockPos blockPos) {
      Setting<Boolean> raytraceSetting = this.placementRaytraceSetting;
      if (raytraceSetting.getValue()) {
         Vec3 positionSnapshot = ClientAccess.argusclientMinecraft.player.position();
         Vec3 blockCenter = blockPos.getCenter();
         return -positionSnapshot.distanceToSqr(blockCenter);
      } else {
         return blockPos.getY();
      }
   }

   @Override
   protected int getPlacementRotationPriority() {
      return 150;
   }

   @Override
   protected InventoryUtils.SwapSlot findPlacementSwapSlot() {
      if (!this.onlyObbySetting.getValue() && !this.miningSupportMode) {
         return super.findPlacementSwapSlot();
      }

      Item obsidianSnapshot = Items.OBSIDIAN;
      return InventoryUtils.findSwapSlot(obsidianSnapshot, false);
   }

   private Setting<Boolean> createCompleteSetting() {
      return this.add(new BooleanSetting("Complete", false).dependsOn(this.placementAutoDisableSetting), this.placementHeightSetting);
   }

   protected boolean shouldDisableWhenComplete() {
      return this.completeSetting.getValue();
   }

   public AutoTrapModule(String name, String description, ModuleCategory category) {
      super(name, description, category);
      NumberSetting<Float> rangeDefinition = new NumberSetting<>("Range", 5.0F, 1.0F, 6.5F);
      NumberSetting<Float> rangeWithUnit = rangeDefinition.withUnit("m");
      this.rangeSetting = this.add(rangeWithUnit);
      this.sequentialSetting = this.add(new BooleanSetting("Sequential", false));
      this.targetsSetting = this.add(new BooleanSetting("Targets", true).markAsGroup());
      this.headSetting = this.add(new BooleanSetting("Head", true).dependsOn(this.targetsSetting));
      this.faceSetting = this.add(new BooleanSetting("Face", true).dependsOn(this.targetsSetting));
      this.legsSetting = this.add(new BooleanSetting("Legs", false).dependsOn(this.targetsSetting));
      this.belowSetting = this.add(new BooleanSetting("Below", false).dependsOn(this.targetsSetting));
      this.antiStepSetting = this.add(new BooleanSetting("AntiStep", false));
      this.onlyPhasedSetting = this.add(new BooleanSetting("OnlyPhased", false));
      this.ignoreNakedsSetting = this.add(new BooleanSetting("IgnoreNakeds", false));
      this.ignoreCrawlSetting = this.add(new BooleanSetting("IgnoreCrawl", false));
      this.logoutsSetting = this.add(new BooleanSetting("Logouts", false));
      BooleanSetting onlyObbyDefinition = new BooleanSetting("OnlyObby", false);
      Setting<Boolean> raytraceDependency = this.placementRaytraceSetting;
      this.onlyObbySetting = this.add(onlyObbyDefinition, raytraceDependency);
      this.completeSetting = this.createCompleteSetting();
      this.miningSupport = new TrapMiningSupport(this);
      this.miningSupportAllowed = false;
   }

   public AutoTrapModule() {
      super("AutoTrap", "Traps your enemies with obby.", ModuleCategory.COMBAT);
      this.rangeSetting = this.add(new NumberSetting<>("Range", 5.0F, 1.0F, 6.5F).withUnit("m"));
      this.sequentialSetting = this.add(new BooleanSetting("Sequential", false));
      this.targetsSetting = this.add(new BooleanSetting("Targets", true).markAsGroup());
      this.headSetting = this.add(new BooleanSetting("Head", true).dependsOn(this.targetsSetting));
      this.faceSetting = this.add(new BooleanSetting("Face", true).dependsOn(this.targetsSetting));
      this.legsSetting = this.add(new BooleanSetting("Legs", false).dependsOn(this.targetsSetting));
      this.belowSetting = this.add(new BooleanSetting("Below", false).dependsOn(this.targetsSetting));
      this.antiStepSetting = this.add(new BooleanSetting("AntiStep", false));
      this.onlyPhasedSetting = this.add(new BooleanSetting("OnlyPhased", false));
      this.ignoreNakedsSetting = this.add(new BooleanSetting("IgnoreNakeds", false));
      this.ignoreCrawlSetting = this.add(new BooleanSetting("IgnoreCrawl", false));
      this.logoutsSetting = this.add(new BooleanSetting("Logouts", false));
      this.onlyObbySetting = this.add(new BooleanSetting("OnlyObby", false));
      this.completeSetting = this.createCompleteSetting();
      this.miningSupport = new TrapMiningSupport(this);
      this.miningSupportAllowed = true;
      this.setDisableOnRespawn(true);
      this.placementJumpSetting.setDefaultValue(false);
   }

   @Override
   public String getInfo() {
      if (this.miningSupportMode) {
         return "Single";
      } else {
         return this.targetPlayer != null ? ClientContext.aliasRegistry.getPlayerAlias(this.targetPlayer) : null;
      }
   }

   protected boolean shouldPlaceBelow() {
      return this.belowSetting.getValue();
   }

   protected boolean hasLogoutAbovePosition(BlockPos blockPos) {
      for (Entity entity : ClientAccess.argusclientMinecraft.level.entitiesForRendering()) {
         if (entity instanceof EndCrystalState logoutEntity && !logoutEntity.isClientAttacked() && entity.blockPosition().equals(blockPos.above())) {
            return true;
         }
      }

      return false;
   }

   protected boolean shouldSkipPlacement(BlockPos blockPos) {
      if (this.targetPlayer == null) {
         return true;
      }

      boolean selfTrap = this instanceof SelfTrapModule;
      if (!selfTrap && this.hasLogoutAbovePosition(blockPos)) {
         return true;
      }

      Vec3 eyeSnapshot = ClientAccess.argusclientMinecraft.player.getEyePosition();
      Vec3 blockCenter = blockPos.getCenter();
      double distance = eyeSnapshot.distanceTo(blockCenter);
      Float configuredRange = this.rangeSetting.getValue();
      if (distance > configuredRange.floatValue()) {
         return true;
      }

      if (!this.legsSetting.getValue()) {
         float targetHeight = this.targetPlayer.getBbHeight();
         if (targetHeight > 1.0F) {
            int blockY = blockPos.getY();
            BlockPos targetBlockPos = this.targetPlayer.blockPosition();
            if (blockY == targetBlockPos.getY()) {
               BlockPos abovePos = blockPos.above();
               if (WorldUtils.isPlacementOccupied(abovePos)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   protected boolean isValidTarget(Player player) {
      if (player.isDeadOrDying() || !player.isAlive() || player == ClientAccess.argusclientMinecraft.player) {
         return false;
      }

      if (ClientContext.socialRegistry.isFriendPlayer(player)) {
         return false;
      }

      if (this.ignoreNakedsSetting.getValue() && !PlayerStateUtils.hasArmor(player)) {
         return false;
      }

      if (this.onlyPhasedSetting.getValue() && !this.isPhasedPlayer(player)) {
         return false;
      }

      if (this.ignoreCrawlSetting.getValue() && PlayerStateUtils.isLowProfile(player) && !player.isFallFlying()) {
         return false;
      }

      if (!RotationManager.isMovementSyncEnabled() && !(player instanceof SidePlayer)) {
         double movementAmount = MovementPhysics.getHorizontalSpeedKmh(player);
         if (movementAmount > 20.0) {
            return false;
         }

         if (!EntityUtils.hasCollisionBelow(player)) {
            return false;
         }

         double previousY = player.yo;
         double verticalDifference = previousY - player.getY();
         double verticalMovement = Math.abs(verticalDifference);
         if (verticalMovement > 0.6) {
            return false;
         }
      }

      float rangeValue = this.rangeSetting.getValue();
      float maximumRange = rangeValue + 2.0F;
      if (player.isFallFlying()) {
         maximumRange = 14.0F;
      }

      return this.getTargetPosition(player).distanceTo(ClientAccess.argusclientMinecraft.player.getEyePosition()) <= maximumRange;
   }

   protected boolean includeLowerSideOffsets() {
      return true;
   }

   protected boolean isHeadOnly() {
      return this.headSetting.getValue() && !this.faceSetting.getValue();
   }

   private BlockPos findSupportNeighbor(BlockPos blockPos, List<BlockPos> plannedPositions) {
      Direction[] directions = Direction.values();
      Stream<Direction> directionStream = Arrays.stream(directions);
      Optional<BlockPos> supportCandidate = directionStream.map(blockPos::relative)
         .sorted(Comparator.comparing(this::getSupportSortKey))
         .filter(neighborPos -> {
            if (this.findSupportDirection(neighborPos, BlockInteractionUtils.isStrictDirectionEnabled(), plannedPositions) != null) {
               Setting<Boolean> attackSettingRef = this.placementAttackSetting;
               boolean attackFlag = attackSettingRef.getValue();
               BlockPos positionForCheck = neighborPos;
               if (BlockInteractionUtils.canPlaceAt(positionForCheck, true, attackFlag)) {
                  return true;
               }
            }

            return false;
         })
         .findFirst();
      return supportCandidate.orElse(null);
   }

   protected List<Vec3> collectTargetOffsets(BlockPos targetPos, int baseYOffset, float targetHeight) {
      ArrayList<Vec3> offsets = new ArrayList<>();
      offsets.add(Vec3.atLowerCornerOf(targetPos));
      if (this.isHeadOnly()) {
         offsets.add(new Vec3(0.0, getHeadOnlyOffsetY(baseYOffset, targetHeight), 0.0));
         Iterator headOffsetIterator = this.collectBaseOffsets(baseYOffset, targetHeight).iterator();
         if (headOffsetIterator.hasNext()) {
            Vec3 headOffset = (Vec3)headOffsetIterator.next();
            Setting<Boolean> airPlaceSettingRef = this.placementAirPlaceSetting;
            if (!airPlaceSettingRef.getValue()) {
               BlockPos headRelativePos = BlockPos.containing(headOffset);
               BlockPos headNeighborPos = headRelativePos.offset(targetPos);
               if (!WorldUtils.isPlacementOccupied(headNeighborPos)) {
                  boolean strictDirectionSnapshot = BlockInteractionUtils.isStrictDirectionEnabled();
                  Setting<Boolean> raytraceSettingRef = this.placementRaytraceSetting;
                  boolean raytrace = raytraceSettingRef.getValue();
                  if (BlockInteractionUtils.findSupportDirection(headNeighborPos, strictDirectionSnapshot, raytrace) == null) {
                     if (headOffset.y() > baseYOffset) {
                        offsets.add(new Vec3(-1.0, baseYOffset + 1, 0.0));
                     } else {
                        offsets.add(new Vec3(-1.0, baseYOffset, 0.0));
                     }
                  }
               }
            }
         }
      } else if (this.headSetting.getValue()) {
         offsets.add(new Vec3(0.0, getHeadOffsetY(baseYOffset, targetHeight), 0.0));
      }

      for (Vec3 sideOffset : this.collectBaseOffsets(baseYOffset, targetHeight)) {
         if (!(sideOffset.y() > targetHeight)
            && (this.faceSetting.getValue() || !(sideOffset.y() > baseYOffset))
            && (this.includeLowerSideOffsets() || sideOffset.y() != baseYOffset)) {
            offsets.add(sideOffset);
         }
      }

      if (this.shouldPlaceBelow()) {
         offsets.add(new Vec3(0.0, baseYOffset - 1, 0.0));
      }

      if (this.antiStepSetting.getValue()) {
         offsets.add(new Vec3(0.0, getAntiStepOffsetY(baseYOffset, targetHeight), 0.0));
      }

      return offsets;
   }

   private static double getHeadOnlyOffsetY(int baseYOffset, float targetHeight) {
      float topY = baseYOffset + targetHeight;
      return topY + 1.0F;
   }

   private static double getHeadOffsetY(int baseYOffset, float targetHeight) {
      float topY = baseYOffset + targetHeight;
      return topY + 1.0F;
   }

   private static double getAntiStepOffsetY(int baseYOffset, float targetHeight) {
      float topY = baseYOffset + targetHeight;
      return topY + 2.0F;
   }

   private static double getBaseHeadOffsetY(int baseYOffset, float targetHeight) {
      float topY = baseYOffset + targetHeight;
      return topY + 1.0F;
   }

   protected List<Vec3> collectBaseOffsets(int baseYOffset, float targetHeight) {
      Vec3[] offsets = new Vec3[9];
      offsets[0] = new Vec3(0.0, getBaseHeadOffsetY(baseYOffset, targetHeight), 0.0);
      offsets[1] = new Vec3(1.0, baseYOffset, 0.0);
      double southY = baseYOffset;
      offsets[2] = new Vec3(0.0, southY, 1.0);
      double northY = baseYOffset;
      offsets[3] = new Vec3(0.0, northY, -1.0);
      offsets[4] = new Vec3(-1.0, baseYOffset, 0.0);
      offsets[5] = new Vec3(1.0, baseYOffset + 1, 0.0);
      double upperSouthY = baseYOffset + 1;
      offsets[6] = new Vec3(0.0, upperSouthY, 1.0);
      double upperNorthY = baseYOffset + 1;
      offsets[7] = new Vec3(0.0, upperNorthY, -1.0);
      offsets[8] = new Vec3(-1.0, baseYOffset + 1, 0.0);
      return Arrays.asList(offsets);
   }

   private List<BlockPos> collectTrapPositions(List<Integer> visitedHashes, BlockPos origin, int baseYOffset) {
      ArrayList<BlockPos> positions = new ArrayList<>();
      int originHash = origin.hashCode();
      visitedHashes.add(originHash);
      float targetHeight = this.targetPlayer.getBbHeight();

      for (Vec3 offset : this.collectTargetOffsets(origin, baseYOffset, targetHeight)) {
         int offsetXValue = (int)offset.x;
         int offsetYValue = (int)offset.y;
         int offsetZ = (int)offset.z;
         BlockPos candidatePos = origin.offset(offsetXValue, offsetYValue, offsetZ);
         boolean solidCandidate = WorldUtils.isPlacementOccupied(candidatePos);
         if (solidCandidate) {
            AABB targetBoundsSnapshot = this.targetPlayer.getBoundingBox();
            AABB candidateBounds = new AABB(candidatePos);
            if (!BoundingBoxUtils.hasVolumeOverlap(targetBoundsSnapshot, candidateBounds) || offset.y != 0.0) {
               continue;
            }
         }

         if (!this.shouldSkipAirPlacement(candidatePos, offset)) {
            ClientLevel collisionLevel = ClientAccess.argusclientMinecraft.level;
            Class<Player> collisionEntityType = Player.class;
            AABB collisionBounds = new AABB(candidatePos);
            Predicate<Player> anyPlayer = unused -> true;
            if (!collisionLevel.getEntitiesOfClass(collisionEntityType, collisionBounds, anyPlayer).isEmpty()) {
               int candidateHash = candidatePos.hashCode();
               if (!visitedHashes.contains(candidateHash) && offset.y == 0.0) {
                  positions.addAll(this.collectTrapPositions(visitedHashes, candidatePos, baseYOffset));
                  continue;
               }
            }

            if (this.findSupportDirection(candidatePos, BlockInteractionUtils.isStrictDirectionEnabled(), positions) == null) {
               Setting<Boolean> airPlaceSettingRef = this.placementAirPlaceSetting;
               if (!airPlaceSettingRef.getValue()) {
                  BlockPos supportNeighbor = this.findSupportNeighbor(candidatePos, positions);
                  if (supportNeighbor != null) {
                     positions.add(supportNeighbor);
                  }
               }
            }

            positions.add(candidatePos);
         }
      }

      if (!this.isHeadOnly() && !this.legsSetting.getValue()) {
         float heightValue = this.targetPlayer.getBbHeight();
         if (heightValue > 1.0F) {
            positions.removeIf(plannedPos -> {
               BlockPos positionForHeight = plannedPos;
               double plannedY = positionForHeight.getY();
               Player targetForY = this.targetPlayer;
               return plannedY == targetForY.getY();
            });
         }
      }

      positions.remove(origin);
      return positions;
   }

   private Direction findSupportDirection(BlockPos blockPos, boolean strictDirection, List<BlockPos> plannedPositions) {
      for (Direction direction : Direction.values()) {
         BlockPos neighborPos = blockPos.relative(direction);
         if ((WorldUtils.isPlacementOccupied(neighborPos) || plannedPositions.contains(neighborPos))
            && (!strictDirection || isStrictSupport(neighborPos, direction))) {
            return direction;
         }
      }

      return null;
   }

   private static boolean isStrictSupport(BlockPos position, Direction direction) {
      List<?> availableFaces = BlockInteractionUtils.getStrictDirections(position);
      return availableFaces.contains(direction.getOpposite());
   }

   @EventHandler
   private void onSequentialPlacement(BlockUpdateAppliedEvent event) {
      if (this.sequentialSetting.getValue() && !this.sequentialHandled) {
         this.runPlacementCycle();
         this.sequentialHandled = this.hasPlacedThisCycle();
      }
   }

   @EventHandler
   private void onPlayerTick(LocalPlayerPreTickEvent event) {
      TrapMiningSupport supportSnapshot = this.miningSupport;
      this.miningSupportMode = supportSnapshot.shouldUseMiningSupport();
      this.sequentialHandled = false;
   }

   @Override
   protected void sortPlacementPositions(List<BlockPos> positions) {
      if (RotationManager.isMovementSyncEnabled() && this.targetPlayer != null) {
         float targetYaw = this.targetPlayer.getYRot();
         if (this.targetPlayer.isFallFlying()) {
            Vec3 movement = new Vec3(
               this.targetPlayer.getX() - this.targetPlayer.xo,
               this.targetPlayer.getY() - this.targetPlayer.yo,
               this.targetPlayer.getZ() - this.targetPlayer.zo
            );
            Vec3 movementDirection = movement.normalize();
            Vec3 positionSnapshot = this.targetPlayer.position();
            Vec3 predictionOffset = movementDirection.scale(8.0);
            Vec3 predictedPosition = positionSnapshot.add(predictionOffset);
            positions.sort(Comparator.comparingDouble(sortPosition -> sortPosition.distToCenterSqr(predictedPosition)));
         } else {
            positions.sort(Comparator.comparingDouble(sortPosition -> {
               BlockPos positionForCenter = sortPosition;
               Vec3 centerSnapshot = positionForCenter.getCenter();
               Vec3 centerArgument = centerSnapshot;
               float angleYaw = RotationManager.getRotationsToPosition(centerArgument)[0];
               return Mth.degreesDifferenceAbs(targetYaw, angleYaw);
            }));
         }
      } else {
         positions.sort(Comparator.comparingInt(sortPos -> {
            BlockPos sortPosition = sortPos;
            return -sortPosition.getY();
         }));
         Setting<Boolean> raytraceSettingRef = this.placementRaytraceSetting;
         if (raytraceSettingRef.getValue()) {
            positions.sort(Comparator.comparingDouble(sortPos -> {
               LocalPlayer sortPosition = ClientAccess.argusclientMinecraft.player;
               Vec3 positionValue = sortPosition.position();
               BlockPos positionForCenter = sortPos;
               Vec3 positionCenter = positionForCenter.getCenter();
               Vec3 centerArgument = positionValue;
               return -centerArgument.distanceTo(positionCenter);
            }));
         }
      }
   }

   protected Player selectTarget() {
      ObjectArrayList<Player> players = new ObjectArrayList(this.getWorldPlayers());
      if (logoutSpotsModule.isToggled() && this.logoutsSetting.getValue()) {
         players.addAll(logoutSpotsModule.getSpotPlayers());
      }

      Optional<Player> targetOptional = players.stream().filter(this::isValidTarget).min(Comparator.comparing(candidate -> {
         float yawSnapshot = ClientAccess.argusclientMinecraft.player.getYRot();
         float targetYaw = RotationManager.getRotationsToEntity(candidate)[0];
         float yawDifference = Mth.degreesDifferenceAbs(yawSnapshot, targetYaw);
         return yawDifference;
      }));
      return targetOptional.orElse(null);
   }

   private List<? extends Player> getWorldPlayers() {
      return ClientAccess.argusclientMinecraft.level.players();
   }

   @Override
   protected void placePositions(List<BlockPos> positions) {
      if (positions.isEmpty() && this.shouldDisableWhenComplete()) {
         this.disable();
      }

      super.placePositions(positions);
   }

   protected boolean shouldSkipAirPlacement(BlockPos blockPos, Vec3 offset) {
      Setting<Boolean> airPlaceSettingRef = this.placementAirPlaceSetting;
      return airPlaceSettingRef.getValue() && ClientContext.worldStateManager.hasPendingPlacement(blockPos) && offset.y() > 0.0;
   }

   @Override
   public List getPlacementPositions() {
      if (this.miningSupportMode) {
         return this.miningSupport.collectMiningPositions();
      }

      this.targetPlayer = this.selectTarget();
      if (this.targetPlayer == null) {
         return Collections.emptyList();
      }

      Player targetSnapshot = this.targetPlayer;
      Vec3 targetPosition = this.getTargetPosition(targetSnapshot);

      List<BlockPos> positions;
      try {
         positions = this.collectTrapPositions(new ArrayList<>(), BlockPos.containing(targetPosition), 0);
      } catch (Throwable failure) {
         return Collections.emptyList();
      }

      positions.removeIf(this::shouldSkipPlacement);
      positions.stream().filter(this::isInsideTargetTopBounds).findFirst().ifPresent(priorityPos -> {
         positions.remove(priorityPos);
         positions.addFirst(priorityPos);
      });
      return positions;
   }

   private AABB getTargetTopBounds(Entity entity) {
      double topY = entity.getY();
      double entityTop = topY + entity.getBbHeight();
      topY = Math.ceil(entityTop);
      double minimumY = topY + 1.0;
      AABB raisedBounds = entity.getBoundingBox().setMinY(minimumY);
      double maximumY = minimumY + 1.0;
      return raisedBounds.setMaxY(maximumY);
   }

   private boolean isPhasedPlayer(Player player) {
      if (player.isFallFlying()) {
         return false;
      }

      double playerY = player.getY();
      double checkY = playerY + 0.1;
      return WallsVelocityHandler.hasCollisionAtHeight(player, checkY);
   }

   public static void bindReferencedModules(ModuleRegistry modules) {
      logoutSpotsModule = (LogoutSpotsModule)modules.getModule(LogoutSpotsModule.class);
      TrapMiningSupport.bindReferencedModules(modules);
   }
}
