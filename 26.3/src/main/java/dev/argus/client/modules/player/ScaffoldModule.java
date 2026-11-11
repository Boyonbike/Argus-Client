package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.PlayerIsStayingOnGroundSurfaceEvent;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.managers.CrystalInteractionManager;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.TickRateController;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.render.FeatureBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.ListFilterMode;
import dev.argus.client.settings.options.ScaffoldSwapChoice;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.BlockRaycastContext;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.combat.RaycastUtils;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.mixin.BlockBehaviourAccessor;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ScaffoldModule extends Module {
   private static AntiCheatModule antiCheatModule = ClientContext.getModule(AntiCheatModule.class);
   private final Setting<ScaffoldModule.ScaffoldMode> modeSetting;
   private final Setting<Integer> sizeSetting;
   private final Setting<Integer> frequencySetting;
   private final Setting<Set<Block>> blocksSetting = this.add(new RegistrySetSetting<>("Blocks", BuiltInRegistries.BLOCK));
   private final Setting<ListFilterMode> selectionSetting = this.add(new EnumSetting<>("Selection", ListFilterMode.ANY));
   private final Setting<ScaffoldSwapChoice> swapSetting = this.add(new EnumSetting<>("Swap", ScaffoldSwapChoice.NONE));
   private final Setting<Integer> delaySetting;
   final Setting<Float> offsetSetting;
   public final Setting<ScaffoldModule.TowerChoice> towerSetting;
   public final Setting<Boolean> safeWalkSetting;
   final Setting<Boolean> airPlaceSetting;
   final Setting<Boolean> tellySetting;
   private final Setting<Boolean> rotateSetting;
   public final Setting<Boolean> attackSetting;
   final Setting<Boolean> renderSetting;
   private final Setting<Float> lineWidthSetting;
   private final Setting<Color> fillSetting;
   private final Setting<Color> outlineSetting;
   final Setting<Boolean> fadeSetting;
   private final Setting<Float> fadeTimeSetting;
   private final ElapsedTimer targetTimeoutTimer;
   private final ElapsedTimer rotationTimeoutTimer;
   private final ElapsedTimer placementDelayTimer;
   private final ElapsedTimer inventoryClickTimer;
   private final ElapsedTimer attackTimer;
   private final List<ScaffoldPlacementTarget> placementTargets;
   private float[] cachedRotations;
   private int lastTowerBlockY;
   int blockSlot;
   double tellyBaseY;
   private float savedJumpStrength;

   @Override
   public void onEnable() {
      if (!this.isWorldUnavailable()) {
         this.tellyBaseY = ClientAccess.argusclientMinecraft.player.getY();
      }
   }

   @Override
   public void onToggle() {
      ElapsedTimer timer = this.targetTimeoutTimer;
      timer.reset();
      timer = this.rotationTimeoutTimer;
      timer.reset();
      this.placementTargets.clear();
      this.cachedRotations = null;
      this.lastTowerBlockY = 0;
      this.blockSlot = -1;
   }

   private float getJumpStrength() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Holder jumpAttribute = Attributes.JUMP_STRENGTH;
      return (float)player.getAttribute(jumpAttribute).getBaseValue();
   }

   private void tickPlacement() {
      ClientContext.playerTickController.releaseMultiplier(this);
      if (this.savedJumpStrength > 0.0F) {
         float savedStrength = this.savedJumpStrength;
         this.setJumpStrength(savedStrength);
         this.savedJumpStrength = -1.0F;
      }

      if (!ClientAccess.argusclientMinecraft.player.isHandsBusy()) {
         ElapsedTimer timer = this.inventoryClickTimer;
         if (timer.hasElapsed(50L) || !ClientAccess.argusclientMinecraft.player.onGround() || !antiCheatModule.movementSyncSetting.getValue()) {
            if (!MovementPhysics.isLocalPlayerMoving() || ClientAccess.argusclientMinecraft.player.horizontalCollision) {
               this.tellyBaseY = ClientAccess.argusclientMinecraft.player.getY();
            }

            this.selectBlockSlot();
            List<ScaffoldPlacementTarget> targets;
            if (this.modeSetting.getValue() == ScaffoldModule.ScaffoldMode.MULTI) {
               targets = ScaffoldPlacementTarget.findMultiFor(this, this.sizeSetting.getValue(), this.frequencySetting.getValue());
            } else {
               ScaffoldPlacementTarget target = ScaffoldPlacementTarget.findFor(this);
               targets = target == null ? List.of() : List.of(target);
            }

            this.placementTargets.clear();
            this.placementTargets.addAll(targets);
            boolean missingUsableBlock = true;
            if (this.blockSlot != -1) {
               Inventory blockInventory = ClientAccess.argusclientMinecraft.player.getInventory();
               int currentBlockSlot = this.blockSlot;
               missingUsableBlock = blockInventory.getItem(currentBlockSlot).isEmpty();
            }

            if (this.swapSetting.getValue() == ScaffoldSwapChoice.NONE) {
               ItemStack heldStack = ClientAccess.argusclientMinecraft.player.getMainHandItem();
               if (this.scoreBlockStack(heldStack) == 0) {
                  missingUsableBlock = true;
               }
            }

            if (missingUsableBlock) {
               this.placementTargets.clear();
            } else {
               for (ScaffoldPlacementTarget target : this.placementTargets) {
                  if (this.attackBlockingEntity(target)) {
                     break;
                  }
               }

               this.updateTower();
               timer = this.placementDelayTimer;
               long delayMillis = this.delaySetting.getValue().intValue();
               if (timer.hasElapsed(delayMillis)) {
                  for (ScaffoldPlacementTarget target : this.placementTargets) {
                     if (this.canPlaceAt(target.position(), this.blockSlot)) {
                        ScaffoldSwapChoice placementSwap = this.swapSetting.getValue();
                        int placementSlot = this.blockSlot;
                        placementSwap.runWithSlot(placementSlot, () -> this.placeCurrentTarget(target));
                     }
                  }
               }
            }

            this.updatePlacementRotation();
            timer = this.targetTimeoutTimer;
            if (timer.hasElapsed(150L)) {
               timer = this.targetTimeoutTimer;
               timer.reset();
               this.placementTargets.clear();
            }
         }
      }
   }

   private void placeCurrentTarget(ScaffoldPlacementTarget target) {
      if (this.renderSetting.getValue()) {
         FeatureBoxRenderer boxRenderer = ClientContext.featureBoxRenderer;
         BlockPos renderPosition = target.position();
         boxRenderer.addBox(this, renderPosition);
      }

      ScaffoldPlacementTarget placementTargetSnapshot = target;
      ScaffoldPlacementGeometry geometry = ScaffoldPlacementGeometry.fromTarget(placementTargetSnapshot, true);
      Vec3 aimPosition = geometry.aimPosition();
      double playerY = ClientAccess.argusclientMinecraft.player.getY();
      double fractionPlayerY = ClientAccess.argusclientMinecraft.player.getY();
      float supportHeight = (float)(playerY - Math.floor(fractionPlayerY));
      if (geometry.supportDirection() == Direction.DOWN) {
         Axis verticalAxis = Axis.Y;
         double targetY = target.position().getY();
         aimPosition = aimPosition.with(verticalAxis, targetY);
      } else {
         VoxelShape supportShape = geometry.supportShape();
         if (!supportShape.isEmpty()) {
            VoxelShape heightShape = geometry.supportShape();
            double playerFraction = supportHeight;
            double shapeTop = heightShape.bounds().maxY;
            double aimOffsetY = Math.max(shapeTop, playerFraction) - 0.05;
            aimPosition = aimPosition.add(0.0, aimOffsetY, 0.0);
         } else {
            double emptyShapeOffsetY = 0.5;
            aimPosition = aimPosition.add(0.0, emptyShapeOffsetY, 0.0);
         }
      }

      if (placementTargetSnapshot.airPlace()) {
         aimPosition = Vec3.atCenterOf(placementTargetSnapshot.position());
         geometry = new ScaffoldPlacementGeometry(geometry.supportDirection(), aimPosition, Shapes.empty());
      }

      InteractionHand hand = InteractionHand.MAIN_HAND;
      placementTargetSnapshot = target;
      BlockPos blockPosition = placementTargetSnapshot.position();
      Direction supportDirection = geometry.supportDirection();
      Boolean airPlaceEnabled = this.airPlaceSetting.getValue();
      BlockInteractionUtils.placeBlock(blockPosition, aimPosition, supportDirection, airPlaceEnabled, hand);
      if (RotationManager.isSilentRotationMode()) {
         float[] postRotations = this.computePostPlacementRotations(geometry, aimPosition);
         this.applyRotation(postRotations);
      }

      this.placementDelayTimer.reset();
      this.rotationTimeoutTimer.reset();
      this.targetTimeoutTimer.reset();
   }

   private boolean attackBlockingEntity(ScaffoldPlacementTarget target) {
      if (target == null) {
         return false;
      } else {
         Boolean attackEnabled = this.attackSetting.getValue();
         if (!attackEnabled) {
            return false;
         } else {
            ElapsedTimer timer = this.attackTimer;
            CrystalInteractionManager crystalManager = ClientContext.crystalInteractionManager;
            long attackDelayMillis = crystalManager.getAttackDelayMillis();
            if (!timer.hasElapsed(attackDelayMillis)) {
               return false;
            } else {
               BlockPos targetPosition = target.position();
               crystalManager = ClientContext.crystalInteractionManager;
               int entityCheckFlags = crystalManager.getMinimumCrystalAge();
               Entity blockingEntity = BlockInteractionUtils.attackCrystalAt(targetPosition, entityCheckFlags);
               if (blockingEntity != null) {
                  float[] attackRotations = RotationManager.getRotationsToEntity(blockingEntity);
                  this.applyRotation(attackRotations);
                  timer = this.attackTimer;
                  timer.reset();
                  return true;
               } else {
                  return false;
               }
            }
         }
      }
   }

   @EventHandler
   private void onLocalPlayerMoveEvent(LocalPlayerMoveEvent event) {
      Setting<Boolean> safeWalkSetting = this.safeWalkSetting;
      if (safeWalkSetting.getValue()) {
         applySafeWalk(event);
      }
   }

   private static void applySafeWalk(LocalPlayerMoveEvent event) {
      if (RotationManager.isMovementSyncEnabled()) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         if (player.onGround()) {
            if (!ClientAccess.argusclientMinecraft.player.isHandsBusy()) {
               AABB playerBounds = ClientAccess.argusclientMinecraft.player.getBoundingBox();
               double eventMovementX = event.getX();
               double movementZ = event.getZ();
               AABB movedBounds = playerBounds.move(eventMovementX, 0.0, movementZ);
               boolean wasSneaking = ClientAccess.argusclientMinecraft.player.isShiftKeyDown();
               ClientLevel level = ClientAccess.argusclientMinecraft.level;
               AABB expandedBounds = movedBounds.expandTowards(0.0, -1.0, 0.0);
               if (level.noCollision(expandedBounds)) {
                  if (!wasSneaking) {
                     ClientAccess.argusclientMinecraft.player.setShiftKeyDown(true);
                  }

                  event.setHorizontalVelocity(0.0, 0.0);
               } else if (!wasSneaking) {
                  ClientAccess.argusclientMinecraft.player.setShiftKeyDown(false);
               }
            }
         }
      }
   }

   @EventHandler
   private void onConnectionSendPacketEvent(PacketEvent.Send event) {
      if (!event.isCancelled()) {
         if (event.getPacket() instanceof ServerboundContainerClickPacket) {
            ElapsedTimer inventoryClickTimer = this.inventoryClickTimer;
            inventoryClickTimer.reset();
         }
      }
   }

   private float[] computePrePlacementRotations() {
      ScaffoldPlacementTarget target = this.placementTargets.getFirst();
      ScaffoldPlacementGeometry geometry = ScaffoldPlacementGeometry.fromTarget(target, true);
      Vec3 eyePosition = ClientAccess.argusclientMinecraft.player.getEyePosition();
      if (RotationManager.isMovementSyncEnabled()) {
         eyePosition = ClientContext.movementPredictionManager.getPredictedEyePosition();
      }

      Vec3 aimBasePosition = geometry.aimPosition();
      VoxelShape shape = geometry.supportShape();
      double aimHeight;
      if (shape.isEmpty()) {
         aimHeight = 0.5;
      } else {
         aimHeight = geometry.supportShape().bounds().maxY - 0.01;
      }

      Vec3 aimPosition = aimBasePosition.add(0.0, aimHeight, 0.0);
      float[] rotations = RotationManager.calculateRotations(eyePosition, aimPosition);
      if (MovementPhysics.isLocalPlayerMoving() && !RotationManager.isMovementSyncEnabled()) {
         float playerYawSnapshot = ClientAccess.argusclientMinecraft.player.getYRot();
         ClientInput movementInput = ClientAccess.argusclientMinecraft.player.input;
         double[] movementDirection = MovementPhysics.getHorizontalMotion(playerYawSnapshot, movementInput, 1.0);
         double directionZ = movementDirection[1];
         double movementDirectionX = movementDirection[0];
         double movementRadians = Math.atan2(directionZ, movementDirectionX);
         float movementYaw = (float)(Math.toDegrees(movementRadians) - 270.0);
         float placementYaw = rotations[0];
         if (Mth.degreesDifferenceAbs(movementYaw, placementYaw) < 60.0F) {
            rotations[0] = movementYaw;
         }
      }

      if (RotationManager.isMovementSyncEnabled() && !MovementPhysics.isLocalPlayerMoving()) {
         float stationaryYawBase = rotations[0];
         ThreadLocalRandom random = ThreadLocalRandom.current();
         rotations[0] = stationaryYawBase + random.nextInt(8) * AntiCheatModule.ROTATION_JITTER_STEP;
      }

      this.rotationTimeoutTimer.reset();
      return rotations;
   }

   private float[] computePostPlacementRotations(ScaffoldPlacementGeometry geometry, Vec3 postAimPosition) {
      VoxelShape postShape = geometry.supportShape();
      double postAimHeight;
      if (postShape.isEmpty()) {
         postAimHeight = 0.5;
      } else {
         postAimHeight = geometry.supportShape().bounds().maxY - 0.01;
      }

      postAimPosition = postAimPosition.add(0.0, postAimHeight, 0.0);
      return RotationManager.getRotationsToPosition(postAimPosition);
   }

   double findSupportHeight(float sampleY) {
      LocalPlayer boundsPlayer = ClientAccess.argusclientMinecraft.player;
      AABB bounds = boundsPlayer.getBoundingBox();
      LocalPlayer groundPlayer = ClientAccess.argusclientMinecraft.player;
      boolean onGround = groundPlayer.onGround();
      LocalPlayer passengerCheckPlayer = ClientAccess.argusclientMinecraft.player;
      if (passengerCheckPlayer.isPassenger()) {
         LocalPlayer boundsVehiclePlayer = ClientAccess.argusclientMinecraft.player;
         Entity boundsVehicle = boundsVehiclePlayer.getVehicle();
         bounds = boundsVehicle.getBoundingBox();
         LocalPlayer groundVehiclePlayer = ClientAccess.argusclientMinecraft.player;
         Entity groundVehicle = groundVehiclePlayer.getVehicle();
         onGround = groundVehicle.onGround();
      }

      double minimumX = bounds.minX;
      double minimumZ = bounds.minZ;
      double maximumX = bounds.maxX;
      double maximumZ = bounds.maxZ;
      List<Vec3> samplePoints = new ArrayList<>();
      Vec3 bottomCenter = BoundingBoxUtils.getBottomCenter(bounds);
      double sampleHeight = sampleY;
      Axis sampleAxis = Axis.Y;
      samplePoints.add(bottomCenter.with(sampleAxis, sampleHeight));
      if (onGround) {
         bottomCenter = new Vec3(minimumX, sampleY, minimumZ);
         Vec3 maximumXCorner = new Vec3(maximumX, sampleY, minimumZ);
         Vec3 maximumZCorner = new Vec3(minimumX, sampleY, maximumZ);
         Vec3 maximumCorner = new Vec3(maximumX, sampleY, maximumZ);
         samplePoints.addAll(List.of(bottomCenter, maximumXCorner, maximumZCorner, maximumCorner));
      }

      double supportHeight = -999.0;

      for (Vec3 sample : samplePoints) {
         BlockRaycastContext.Builder builder = new BlockRaycastContext.Builder(sample, sample.add(0.0, -1.0, 0.0));
         LocalPlayer raycastPassengerPlayer = ClientAccess.argusclientMinecraft.player;
         Entity raycastEntity;
         if (raycastPassengerPlayer.isPassenger()) {
            LocalPlayer raycastVehiclePlayer = ClientAccess.argusclientMinecraft.player;
            raycastEntity = raycastVehiclePlayer.getVehicle();
         } else {
            raycastEntity = ClientAccess.argusclientMinecraft.player;
         }

         BlockRaycastContext context = builder.withEntity(raycastEntity).build();
         BlockHitResult hit = RaycastUtils.raycast(context);
         double sampleHeightResult;
         if (hit.getType() == Type.MISS) {
            double sampleOriginY = sample.y();
            sampleHeightResult = sampleOriginY - 1.0;
         } else {
            Vec3 hitPosition = hit.getLocation();
            double hitY = hitPosition.y();
            sampleHeightResult = hitY - 0.01F;
         }

         double previousSupportHeight = supportHeight;
         supportHeight = Math.max(previousSupportHeight, sampleHeightResult);
      }

      return supportHeight;
   }

   private void setJumpStrength(float strength) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Holder jumpAttribute = Attributes.JUMP_STRENGTH;
      AttributeInstance attribute = player.getAttribute(jumpAttribute);
      double baseValue = strength;
      attribute.setBaseValue(baseValue);
   }

   @EventHandler
   private void onPlayerIsStayingOnGroundSurfaceEvent(PlayerIsStayingOnGroundSurfaceEvent event) {
      if (this.safeWalkSetting.getValue()) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         if (!player.isHandsBusy()) {
            event.cancel();
         }
      }
   }

   @EventHandler
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      Boolean rotateEnabled = this.rotateSetting.getValue();
      if (!rotateEnabled && event.getStage().isPre()) {
         this.tickPlacement();
      }
   }

   private int scoreBlockStack(ItemStack stack) {
      if (stack.getItem() instanceof BlockItem blockItem) {
         Block block = blockItem.getBlock();
         if (!((BlockBehaviourAccessor)block).isCollidable()) {
            return 0;
         }

         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         if (!player.isShiftKeyDown() && block == Blocks.POWDER_SNOW) {
            return 0;
         }

         if (!this.canUseFallingBlock(blockItem)) {
            return 0;
         }

         ListFilterMode filterMode = this.selectionSetting.getValue();
         Collection selectedBlocks = this.blocksSetting.getValue();
         if (!ListFilterMode.matches(filterMode, block, selectedBlocks)) {
            return 0;
         }

         BlockState blockState = block.defaultBlockState();
         ClientLevel blockLevel = ClientAccess.argusclientMinecraft.level;
         BlockPos shapePosition = BlockPos.ZERO;
         VoxelShape collisionShape = blockState.getCollisionShape(blockLevel, shapePosition);
         int score;
         if (BoundingBoxUtils.hasUnitDimensions(collisionShape)) {
            score = 2;
         } else {
            score = 1;
         }

         return score;
      } else {
         return 0;
      }
   }

   private void selectBlockSlot() {
      ItemStack stack = ClientAccess.argusclientMinecraft.player.getMainHandItem();
      if (this.scoreBlockStack(stack) > 0) {
         this.blockSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
      } else {
         int bestSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
         stack = ClientAccess.argusclientMinecraft.player.getInventory().getItem(bestSlot);
         int bestScore = this.scoreBlockStack(stack);

         for (int hotbarIndex = 0; hotbarIndex < 9; hotbarIndex++) {
            stack = ClientAccess.argusclientMinecraft.player.getInventory().getItem(hotbarIndex);
            int candidateScore = this.scoreBlockStack(stack);
            if (candidateScore > bestScore) {
               bestSlot = hotbarIndex;
               bestScore = candidateScore;
            }
         }

         if (bestScore == 0) {
            this.blockSlot = -1;
         } else {
            this.blockSlot = bestSlot;
         }
      }
   }

   private void updateTower() {
      boolean fastTower = this.towerSetting.getValue() == ScaffoldModule.TowerChoice.FAST;
      double playerY = ClientAccess.argusclientMinecraft.player.getY();
      int blockY = (int)Math.floor(playerY);
      if (this.lastTowerBlockY != blockY || RotationManager.isMovementSyncEnabled()) {
         ElapsedTimer setbackTimer = ClientContext.serverStateManager.getSetbackTimer();
         if (setbackTimer.hasElapsed(200L) || !fastTower) {
            this.lastTowerBlockY = blockY;
            if (this.towerSetting.getValue() != ScaffoldModule.TowerChoice.NONE) {
               Input input = ClientAccess.argusclientMinecraft.player.input.keyPresses;
               if (input.jump() && ClientAccess.argusclientMinecraft.player.zza == 0.0F && ClientAccess.argusclientMinecraft.player.xxa == 0.0F) {
                  if (this.towerSetting.getValue() == ScaffoldModule.TowerChoice.GRIM) {
                     if (ClientAccess.argusclientMinecraft.player.onGround()) {
                        this.savedJumpStrength = this.getJumpStrength();
                        float grimJumpStrength = 0.36813F;
                        this.setJumpStrength(grimJumpStrength);
                     }
                  } else {
                     if (fastTower) {
                        TickRateController.PlayerTicks playerTicks = ClientContext.playerTickController;
                        float towerMultiplier = 2.0F;
                        playerTicks.requestMultiplier(this, towerMultiplier);
                     }

                     ClientAccess.argusclientMinecraft.player.setDeltaMovement(0.0, 0.41999998688698, 0.0);
                  }
               }
            }
         }
      }
   }

   private void updatePlacementRotation() {
      int rotationTimeout = 500;
      if (RotationManager.isMovementSyncEnabled()) {
         if (antiCheatModule.is2b2tEnabled()) {
            rotationTimeout = 200;
         } else {
            rotationTimeout = -1;
         }
      }

      if (!RotationManager.isSilentRotationMode() && !this.placementTargets.isEmpty()) {
         float[] rotations = this.computePrePlacementRotations();
         this.applyRotation(rotations);
      }

      if (this.cachedRotations != null) {
         RotationManager rotationManager = ClientContext.rotationManager;
         float[] cachedRotation = this.cachedRotations;
         rotationManager.requestRotation(cachedRotation, 150);
      }

      long rotationTimeoutMillis = rotationTimeout;
      if (this.rotationTimeoutTimer.hasElapsed(rotationTimeoutMillis)) {
         this.cachedRotations = null;
      }
   }

   @EventHandler
   private void onRotationEvent(RotationRequestEvent event) {
      if (this.rotateSetting.getValue()) {
         this.tickPlacement();
      }
   }

   boolean canPlaceAt(BlockPos position, int slot) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Inventory inventory = player.getInventory();
      int placementSlot;
      if (slot == -1) {
         LocalPlayer selectionPlayer = ClientAccess.argusclientMinecraft.player;
         Inventory selectedInventory = selectionPlayer.getInventory();
         placementSlot = selectedInventory.getSelectedSlot();
      } else {
         placementSlot = slot;
      }

      ItemStack stack = inventory.getItem(placementSlot);
      if (stack.getItem() instanceof BlockItem blockItem) {
         Block collisionBlock = blockItem.getBlock();
         if (!((BlockBehaviourAccessor)collisionBlock).isCollidable()) {
            Block survivalBlock = blockItem.getBlock();
            BlockState placementState = survivalBlock.defaultBlockState();
            ClientLevel placementLevel = ClientAccess.argusclientMinecraft.level;
            if (!placementState.canSurvive(placementLevel, position)) {
               return false;
            }
         }

         Block block = blockItem.getBlock();
         Boolean attackEnabled = this.attackSetting.getValue();
         return BlockInteractionUtils.canPlaceBlock(position, block, true, attackEnabled);
      } else {
         Boolean fallbackAttackEnabled = this.attackSetting.getValue();
         return BlockInteractionUtils.canPlaceAt(position, true, fallbackAttackEnabled);
      }
   }

   boolean isSelectedBlockNonCollidable() {
      if (this.blockSlot == -1) {
         return false;
      } else {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         Inventory inventory = player.getInventory();
         int slot = this.blockSlot;
         ItemStack stack = inventory.getItem(slot);
         if (stack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            return !((BlockBehaviourAccessor)block).isCollidable();
         } else {
            return false;
         }
      }
   }

   private boolean canUseFallingBlock(BlockItem blockItem) {
      int groundDistance = RaycastUtils.getGroundDistance();
      return !(blockItem.getBlock() instanceof FallingBlock) || groundDistance <= 1;
   }

   private void applyRotation(float[] rotations) {
      if (this.rotateSetting.getValue()) {
         if (RotationManager.isMovementSyncEnabled()) {
            rotations = RotationManager.addYawJitter(rotations);
         }

         if (RotationManager.isSilentRotationMode()) {
            RotationManager rotationManager = ClientContext.rotationManager;
            rotationManager.setRenderRotations(rotations, 10);
            rotationManager = ClientContext.rotationManager;
            rotationManager.requestRotation(rotations, 150);
         } else {
            this.cachedRotations = rotations;
         }
      }
   }

   public static void bindReferencedModules(ModuleRegistry moduleRegistry) {
      antiCheatModule = (AntiCheatModule)moduleRegistry.getModule(AntiCheatModule.class);
   }

   public ScaffoldModule() {
      super("Scaffold", "Speedbridges for you automatically.", ModuleCategory.PLAYER);
      this.modeSetting = this.add(new EnumSetting<>("Mode", ScaffoldModule.ScaffoldMode.SINGLE));
      this.sizeSetting = this.add(new NumberSetting<>("Size", 1, 1, 4, ignored -> this.modeSetting.getValue() == ScaffoldModule.ScaffoldMode.MULTI));
      this.frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 9, ignored -> this.modeSetting.getValue() == ScaffoldModule.ScaffoldMode.MULTI));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 180, 0, 300).withUnit("ms"));
      this.offsetSetting = this.add(
         new NumberSetting<>("Offset", 0.0F, 0.0F, 3.0F, ignored -> this.modeSetting.getValue() == ScaffoldModule.ScaffoldMode.SINGLE)
      );
      this.towerSetting = this.add(new EnumSetting<>("Tower", ScaffoldModule.TowerChoice.NONE));
      this.safeWalkSetting = this.add(new BooleanSetting("SafeWalk", true));
      this.airPlaceSetting = this.add(new BooleanSetting("AirPlace", false));
      this.tellySetting = this.add(new BooleanSetting("Telly", false));
      this.rotateSetting = this.add(new BooleanSetting("Rotate", true));
      this.attackSetting = this.add(new BooleanSetting("Attack", true));
      this.renderSetting = this.add(new BooleanSetting("Render", true).markVisibilityDependency());
      this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.5F, 5.0F, ignored -> this.isRenderVisible()));
      this.fillSetting = this.add(new ColorSetting("Fill", new Color(146643455, true), ignored -> this.isRenderVisible()));
      this.outlineSetting = this.add(new ColorSetting("Outline", new Color(-4351489, true), ignored -> this.isRenderVisible()));
      this.fadeSetting = this.add(new BooleanSetting("Fade", true, ignored -> this.isRenderVisible()));
      this.fadeTimeSetting = this.add(
         new NumberSetting<>("FadeTime", 1.0F, 0.1F, 1.0F, ignored -> this.isRenderVisible() && this.fadeSetting.getValue()).withUnit("s")
      );
      this.targetTimeoutTimer = new ElapsedTimer();
      this.rotationTimeoutTimer = new ElapsedTimer();
      this.placementDelayTimer = new ElapsedTimer();
      this.inventoryClickTimer = new ElapsedTimer();
      this.attackTimer = new ElapsedTimer();
      this.placementTargets = new ArrayList<>();
      this.blockSlot = -1;
      this.savedJumpStrength = -1.0F;
      FeatureBoxRenderer renderer = ClientContext.featureBoxRenderer;
      FeatureBoxRenderer.RenderConfiguration renderConfiguration = new FeatureBoxRenderer.RenderConfiguration(
         this, this.fillSetting, this.outlineSetting, this.lineWidthSetting, this.fadeTimeSetting, () -> false, this.fadeSetting, getRenderFadeTimeScale()
      );
      renderer.register(renderConfiguration);
   }

   private boolean isRenderVisible() {
      Setting<Boolean> renderSetting = this.renderSetting;
      return renderSetting.isExpanded();
   }

   private static int getRenderFadeTimeScale() {
      return 450;
   }

   private enum ScaffoldMode implements NamedEntry {
      SINGLE("Single"),
      MULTI("Multi");

      private final String name;

      ScaffoldMode(String name) {
         this.name = name;
      }

      @Override
      public String getName() {
         return this.name;
      }
   }

   public enum TowerChoice implements NamedEntry {
      NONE("None"),
      PLAIN("Plain"),
      FAST("Fast"),
      GRIM("GrimV3");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      TowerChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
