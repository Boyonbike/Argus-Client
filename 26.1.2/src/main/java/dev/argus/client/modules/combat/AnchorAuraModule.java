package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.ServerStateManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.modules.player.SpeedMineModule;
import dev.argus.client.render.FeatureBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.BlockSampleMode;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.combat.DamageUtils;
import dev.argus.client.utils.entity.EntityUtils;
import dev.argus.client.utils.entity.PlayerStateTracker;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.entity.SwingAction;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class AnchorAuraModule extends Module {
   private static AntiCheatModule antiCheat = ClientContext.getModule(AntiCheatModule.class);
   private static OffhandModule offhand = ClientContext.getModule(OffhandModule.class);
   private static AutoCrystalModule autoCrystal = ClientContext.getModule(AutoCrystalModule.class);
   private static SpeedMineModule speedMine = ClientContext.getModule(SpeedMineModule.class);
   private final Setting<Integer> delaySetting = this.add(new NumberSetting<>("Delay", 50, 0, 1000).withUnit("ms"));
   private final Setting<Float> rangeSetting = this.add(new NumberSetting<>("Range", 5.0F, 0.0F, 6.0F).withUnit("m"));
   private final Setting<Float> wallRangeSetting = this.add(new NumberSetting<>("WallRange", 5.0F, 0.0F, 6.0F).withUnit("m"));
   final Setting<Float> targetRangeSetting = this.add(new NumberSetting<>("TargetRange", 6.0F, 1.0F, 16.0F).withUnit("m"));
   final Setting<Float> minDamageSetting = this.add(new NumberSetting<>("MinDamage", 6.0F, 0.1F, 20.0F).withUnit("hp"));
   private final Setting<Float> maxSelfDamageSetting = this.add(new NumberSetting<>("MaxSelfDamage", 12.0F, 0.0F, 20.0F).withUnit("hp"));
   private final Setting<Boolean> antiSuicideSetting = this.add(new BooleanSetting("AntiSuicide", true));
   private final Setting<Boolean> rotateSetting = this.add(new BooleanSetting("Rotate", false));
   private final Setting<Boolean> airPlaceSetting = this.add(new BooleanSetting("AirPlace", false));
   private final Setting<Boolean> liquidPlaceSetting = this.add(new BooleanSetting("LiquidPlace", false));
   private final Setting<Boolean> inhibitSetting = this.add(new BooleanSetting("Inhibit", true));
   private final Setting<Boolean> sequentialSetting = this.add(new BooleanSetting("Sequential", true));
   private final Setting<Boolean> mineIgnoreSetting = this.add(new BooleanSetting("MineIgnore", false));
   final Setting<Boolean> renderSetting = this.add(new BooleanSetting("Render", true).markVisibilityDependency());
   private final Setting<Color> fillSetting = this.add(new ColorSetting("Fill", new Color(1136499199, true), unused -> this.renderSetting.isExpanded()));
   private final Setting<Color> outlineSetting = this.add(new ColorSetting("Outline", new Color(-910321153, true), unused -> this.renderSetting.isExpanded()));
   private final Setting<Boolean> fadeSetting = this.add(new BooleanSetting("Fade", true).markVisibilityDependency().dependsOn(this.renderSetting));
   private final Setting<Float> fadeTimeSetting = this.add(
      new NumberSetting<>("FadeTime", 1.0F, 0.1F, 1.0F).withUnit("s").dependsOn(this.renderSetting, this.fadeSetting)
   );
   private final Setting<Boolean> pauseSetting = this.add(new BooleanSetting("Pause", false).markAsGroup());
   private final Setting<Boolean> miningSetting = this.add(new BooleanSetting("Mining", false).dependsOn(this.pauseSetting));
   private final Setting<Boolean> eatingSetting = this.add(new BooleanSetting("Eating", false).dependsOn(this.pauseSetting));
   private final Setting<Float> healthSetting = this.add(new NumberSetting<>("Health", 4.0F, 0.0F, 36.0F).withUnit("hp").dependsOn(this.pauseSetting));
   private CompletableFuture<AnchorPlacementResult> calculationFuture;
   private final MutableBlockPos scanPosition = new MutableBlockPos();
   private final ElapsedTimer actionTimer = new ElapsedTimer();
   public final AtomicReference placementData = new AtomicReference();
   public AnchorTargetDamage targetDamage;

   private InteractionHand selectAnchorHand() {
      boolean offhandFlag = false;
      if (offhand.isToggled()) {
         offhandFlag = offhand.itemSetting.getValue().getItem(false) == Items.RESPAWN_ANCHOR;
      }

      Item anchorItemSnapshot = Items.RESPAWN_ANCHOR;
      InteractionHand heldAnchorHand = InventoryUtils.getHandForItem(anchorItemSnapshot);
      if (offhandFlag && heldAnchorHand == null) {
         return null;
      } else {
         return offhandFlag ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
      }
   }

   @Override
   public String getInfo() {
      if (this.targetDamage != null) {
         Object[] arguments = new Object[2];
         Player target = this.targetDamage.target();
         Component targetName = target.getName();
         arguments[0] = targetName.getString();
         double damage = this.targetDamage.damage();
         arguments[1] = damage;
         return "%s, %.1f".formatted(arguments);
      } else {
         return null;
      }
   }

   @Override
   public void onEnable() {
      this.targetDamage = null;
      if (this.calculationFuture != null) {
         try {
            this.calculationFuture.complete(null);
         } catch (CancellationException var2) {
         }
      }
   }

   @Override
   public void onToggle() {
      this.placementData.set(null);
   }

   private boolean shouldPause() {
      if (this.isWorldUnavailable()) {
         return true;
      }

      if (ClientAccess.argusclientMinecraft.player.isSleeping()) {
         return true;
      }

      if (ClientAccess.argusclientMinecraft.player.isUsingItem() && this.eatingSetting.getValue()) {
         return true;
      }

      if (ClientAccess.argusclientMinecraft.gameMode.isDestroying() && this.miningSetting.getValue()) {
         return true;
      }

      if (autoCrystal.isToggled() && autoCrystal.placementData.get() != null && this.targetDamage != null) {
         double crystalDamage = ((CrystalPlacementResult)autoCrystal.placementData.get()).damage();
         AnchorTargetDamage anchorDamageSnapshot = this.targetDamage;
         if (crystalDamage > anchorDamageSnapshot.damage()) {
            return true;
         }
      }

      float selfHealth = EntityUtils.getPlayerHealthWithAbsorption();
      Float minimumHealth = this.healthSetting.getValue();
      return selfHealth < minimumHealth;
   }

   boolean shouldIgnoreMiningBlock() {
      boolean miningThresholdReached = false;
      double miningDamage = speedMine.getMiningProgress();
      SpeedMineModule progressSnapshot = speedMine;
      double projectedDamage = miningDamage + progressSnapshot.getTickBreakProgress() * 2.0;
      Setting<Float> thresholdSetting = speedMine.damageSetting;
      Float threshold = thresholdSetting.getValue();
      if (projectedDamage >= threshold.floatValue() && speedMine.getMiningOrNearlyBrokenPos() != null) {
         BlockPos miningPositionSnapshot = speedMine.getMiningOrNearlyBrokenPos();
         if (WorldUtils.canMineBlock(miningPositionSnapshot)) {
            miningThresholdReached = true;
         }
      }

      if (this.mineIgnoreSetting.getValue()) {
         if (miningThresholdReached) {
            return true;
         }

         if (!speedMine.hasCrystalDelayElapsed(100L)) {
            return true;
         }
      }

      return false;
   }

   private void interactWithAnchor(BlockPos position, Direction direction, Vec3 hitPoint, InteractionHand hand) {
      PlayerStateTracker interactionScheduler = ClientContext.playerStateTracker;
      Runnable interactionCallback = () -> {
         BlockHitResult hit = new BlockHitResult(hitPoint, direction, position, false);
         PacketSender.sendUseItemOn(hand, hit);
         SwingAction swingAction = SwingAction.INTERACT;
         ClientContext.swingManager.swingPacket(hand, swingAction);
      };
      interactionScheduler.runWithoutSneaking(interactionCallback);
   }

   private void chargeAnchor(BlockPos position, Direction direction, int originalSlot) {
      Vec3 centerSnapshot = position.getCenter();
      double faceOffset = 0.5;
      Vec3 hitPoint = centerSnapshot.relative(direction, faceOffset);
      Item glowstoneSnapshot = Items.GLOWSTONE;
      InteractionHand glowstoneHand = InventoryUtils.getHandForItem(glowstoneSnapshot);
      if (this.rotateSetting.getValue()) {
         ClientContext.rotationManager.requestRotation(RotationManager.getRotationsToPosition(hitPoint), 100);
      }

      if (glowstoneHand != null) {
         InteractionHand interactionHand = InteractionHand.MAIN_HAND;
         this.interactWithAnchor(position, direction, hitPoint, interactionHand);
      } else {
         Item slotItemSnapshot = Items.GLOWSTONE;
         int glowstoneSlot = InventoryUtils.findHotbarSlot(slotItemSnapshot);
         if (glowstoneSlot != -1) {
            InventoryUtils.selectHotbarSlotForPlacement(glowstoneSlot);
            InteractionHand swappedHand = InteractionHand.MAIN_HAND;
            this.interactWithAnchor(position, direction, hitPoint, swappedHand);
            InventoryUtils.selectHotbarSlotForPlacement(originalSlot);
         }
      }

      this.actionTimer.reset();
   }

   private boolean shouldRejectDamage(BlockPos position, AnchorTargetDamage targetDamage) {
      if (targetDamage == null) {
         return true;
      }

      BlockPos miningBlockSnapshot;
      if (this.shouldIgnoreMiningBlock()) {
         miningBlockSnapshot = speedMine.getMiningPos();
      } else {
         miningBlockSnapshot = null;
      }

      boolean existingAnchor = this.isAnchorBlock(position);
      Vec3 selfCenter = Vec3.atCenterOf(position);
      LocalPlayer selfPlayerSnapshot = ClientAccess.argusclientMinecraft.player;
      AABB selfBoundsSnapshot = ClientAccess.argusclientMinecraft.player.getBoundingBox();
      BlockPos selfIgnoredSnapshot = this.isAnchorBlock(position) ? position : miningBlockSnapshot;
      double selfDamage = DamageUtils.getExplosionDamage(selfCenter, selfPlayerSnapshot, selfBoundsSnapshot, 5.0, true, selfIgnoredSnapshot, (BlockPos)null);
      double storedDamage = targetDamage.damage();
      Player lethalTargetPlayer = targetDamage.target();
      boolean lethalTarget = storedDamage >= EntityUtils.getHealthWithAbsorption(lethalTargetPlayer);
      Vec3 targetCenter = Vec3.atCenterOf(position);
      Player damageTargetSnapshot = targetDamage.target();
      Player boundsTarget = targetDamage.target();
      AABB targetBoundsSnapshot = boundsTarget.getBoundingBox();
      BlockPos targetIgnoredSnapshot = this.isAnchorBlock(position) ? position : miningBlockSnapshot;
      double recomputedTargetDamage = DamageUtils.getExplosionDamage(
         targetCenter, damageTargetSnapshot, targetBoundsSnapshot, 5.0, true, targetIgnoredSnapshot, (BlockPos)null
      );
      boolean selfRejectionFlag = false;
      if (!lethalTarget) {
         Float maximumSelfDamage = this.maxSelfDamageSetting.getValue();
         selfRejectionFlag = selfDamage >= maximumSelfDamage.floatValue();
      }

      if (!selfRejectionFlag && selfDamage >= EntityUtils.getPlayerHealthWithAbsorption()) {
         selfRejectionFlag = this.antiSuicideSetting.getValue();
      }

      return existingAnchor && !selfRejectionFlag
         ? false
         : this.minDamageSetting.getValue().floatValue() > recomputedTargetDamage || recomputedTargetDamage < 0.5 || selfRejectionFlag;
   }

   public boolean isManagedAnchor(BlockPos position) {
      if (this.isToggled() && !this.shouldPause()) {
         DimensionType dimension = ClientContext.worldStateManager.getDimension();
         if (!dimension.isNether()) {
            if (this.placementData.get() == null) {
               return false;
            }

            return this.isAnchorBlock(position);
         }
      }

      return false;
   }

   boolean isAnchorBlock(BlockPos position) {
      BlockState stateSnapshot = ClientAccess.argusclientMinecraft.level.getBlockState(position);
      Block anchorBlock = Blocks.RESPAWN_ANCHOR;
      return stateSnapshot.is(anchorBlock);
   }

   private Direction selectSupportDirection(BlockPos position) {
      List<Direction> directions = BlockInteractionUtils.getStrictDirections(position);
      Direction direction = BlockInteractionUtils.isStrictDirectionEnabled() ? null : Direction.DOWN;
      if (!directions.isEmpty()) {
         direction = directions.get(0);
      }

      return direction;
   }

   private Vec3 selectHitPoint(BlockPos position) {
      BlockSampleMode samplingMode = BlockSampleMode.X8;
      List<Vec3> samples = samplingMode.sampleBlock(position);
      Optional<Vec3> selectedSample = samples.stream().filter(sample -> {
         if (!WorldUtils.canSeeFromEyes(sample)) {
            double sampleDistance = ClientAccess.argusclientMinecraft.player.getEyePosition().distanceTo(sample);
            Float wallRange = this.wallRangeSetting.getValue();
            return sampleDistance <= wallRange.floatValue();
         } else {
            return true;
         }
      }).min(Comparator.comparing(sample -> ClientAccess.argusclientMinecraft.player.getEyePosition().distanceTo(sample)));
      Vec3 fallbackCenter = position.getCenter();
      return selectedSample.orElse(fallbackCenter);
   }

   private boolean isInvalidPlacement(BlockPos position) {
      BlockSampleMode samplingMode = BlockSampleMode.X8;
      List<Vec3> samples = samplingMode.sampleBlock(position);
      Float rangeSnapshot;
      if (!WorldUtils.canSeeAny(samples)) {
         rangeSnapshot = this.wallRangeSetting.getValue();
      } else {
         rangeSnapshot = this.rangeSetting.getValue();
      }

      double placementRange = rangeSnapshot.floatValue();
      AnchorTargetDamage targetDamage = AnchorTargetDamage.calculateForBase(this, position);
      if (this.shouldRejectDamage(position, targetDamage)) {
         return true;
      }

      Vec3 eyeSnapshot = ClientAccess.argusclientMinecraft.player.getEyePosition();
      Vec3 center = position.getCenter();
      if (eyeSnapshot.distanceTo(center) > placementRange) {
         return true;
      }

      BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(position);
      if (state.isAir()) {
         boolean hasSupport = false;
         if (this.selectSupportDirection(position) != null) {
            boolean directionModeSnapshot = BlockInteractionUtils.isStrictDirectionEnabled();
            boolean zeroWallRange = this.wallRangeSetting.getValue() <= 0.0F;
            hasSupport = BlockInteractionUtils.findSupportDirection(position, directionModeSnapshot, zeroWallRange) != null;
         }

         if (!hasSupport && !this.airPlaceSetting.getValue()) {
            return true;
         }
      }

      Block anchorBlock = Blocks.RESPAWN_ANCHOR;
      return !BlockInteractionUtils.canPlaceBlock(position, anchorBlock, true, false) && !this.isAnchorBlock(position);
   }

   private void placeAnchor(BlockPos position, Direction direction, InteractionHand hand) {
      if (!this.isAnchorBlock(position)) {
         int originalSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
         Item anchorItemSnapshot = Items.RESPAWN_ANCHOR;
         int anchorSlot = InventoryUtils.findHotbarSlot(anchorItemSnapshot);
         boolean offhandSwap = BlockInteractionUtils.findSupportDirection(position) == null && antiCheat.is2b2tEnabled() && this.airPlaceSetting.getValue();
         if (anchorSlot != -1 || hand != InteractionHand.MAIN_HAND) {
            if (this.rotateSetting.getValue()) {
               RotationManager rotationSnapshot = ClientContext.rotationManager;
               Vec3 center = position.getCenter();
               rotationSnapshot.requestRotation(RotationManager.getRotationsToFace(center, direction), 100);
            }

            ItemStack mainStackSnapshot = ClientAccess.argusclientMinecraft.player.getMainHandItem();
            Item anchorBlockItem = Items.RESPAWN_ANCHOR;
            boolean selectedSwap = !mainStackSnapshot.is(anchorBlockItem);
            if (selectedSwap) {
               InventoryUtils.selectHotbarSlotForPlacement(anchorSlot);
            }

            if (offhandSwap) {
               hand = PlayerStateUtils.oppositeHand(hand);
               PacketSender.sendSwapOffhand();
            }

            BlockPos relativeHitPosition = position.relative(direction);
            Vec3 hitPoint = this.selectHitPoint(relativeHitPosition);
            boolean airPlacement = this.airPlaceSetting.getValue();
            if (BlockInteractionUtils.sendPlacement(position, hitPoint, direction, airPlacement, hand) && this.renderSetting.getValue()) {
               ClientContext.featureBoxRenderer.addBox(this, position);
            }

            if (offhandSwap) {
               PacketSender.sendSwapOffhand();
            }

            if (selectedSwap) {
               InventoryUtils.selectHotbarSlotForPlacement(originalSlot);
            }
         }
      }
   }

   private AnchorPlacementResult calculatePlacement() {
      AnchorPlacementResult bestPlacement = null;

      for (double offsetX = -Math.floor(this.rangeSetting.getValue().floatValue()); offsetX < Math.ceil(this.rangeSetting.getValue().floatValue()); offsetX++) {
         for (double offsetY = -Math.floor(this.rangeSetting.getValue().floatValue());
            offsetY < Math.ceil(this.rangeSetting.getValue().floatValue());
            offsetY++
         ) {
            for (double offsetZ = -Math.floor(this.rangeSetting.getValue().floatValue());
               offsetZ < Math.ceil(this.rangeSetting.getValue().floatValue());
               offsetZ++
            ) {
               MutableBlockPos scanPositionSnapshot = this.scanPosition;
               double scanXSnapshot = ClientAccess.argusclientMinecraft.player.getX() + offsetX;
               double scanYSnapshot = ClientAccess.argusclientMinecraft.player.getEyeY() + offsetY;
               double scanZ = ClientAccess.argusclientMinecraft.player.getZ() + offsetZ;
               scanPositionSnapshot.set(scanXSnapshot, scanYSnapshot, scanZ);
               scanPositionSnapshot = this.scanPosition;
               if (!BlockInteractionUtils.isOutsidePlacementBounds(scanPositionSnapshot)) {
                  boolean miningBlockIgnored = false;
                  if (this.shouldIgnoreMiningBlock()) {
                     scanPositionSnapshot = this.scanPosition;
                     if (scanPositionSnapshot.equals(speedMine.getMiningPos())) {
                        miningBlockIgnored = true;
                     }
                  }

                  if (!WorldUtils.isPlacementOccupied(this.scanPosition) || miningBlockIgnored) {
                     AABB boundsSnapshot = new AABB(this.scanPosition);
                     if (BlockInteractionUtils.hasBlockingEntity(boundsSnapshot, true, true)) {
                        MutableBlockPos entityAnchorSnapshot = this.scanPosition;
                        if (!this.isAnchorBlock(entityAnchorSnapshot)) {
                           continue;
                        }
                     }

                     MutableBlockPos samplePosition = this.scanPosition;
                     BlockSampleMode samplingMode = BlockSampleMode.X8;
                     List samples = samplingMode.sampleBlock(samplePosition);
                     boolean visible = WorldUtils.canSeeAny(samples);
                     Vec3 eyeSnapshot = ClientAccess.argusclientMinecraft.player.getEyePosition();
                     Vec3 center = this.scanPosition.getCenter();
                     double distance = eyeSnapshot.distanceTo(center);
                     if (!visible) {
                        Float visibilityWallRange = this.wallRangeSetting.getValue();
                        if (distance > visibilityWallRange.floatValue()) {
                           continue;
                        }
                     }

                     Float placementRange = this.rangeSetting.getValue();
                     if (!(distance > placementRange.floatValue())) {
                        scanPositionSnapshot = this.scanPosition;
                        boolean directionModeSnapshot = BlockInteractionUtils.isStrictDirectionEnabled();
                        boolean zeroWallRange = this.wallRangeSetting.getValue() <= 0.0F;
                        Direction direction = BlockInteractionUtils.findSupportDirection(scanPositionSnapshot, directionModeSnapshot, zeroWallRange);
                        MutableBlockPos damagePositionSnapshot = this.scanPosition;
                        AnchorTargetDamage targetDamage = AnchorTargetDamage.calculateForBase(this, damagePositionSnapshot);
                        if (direction == null && this.airPlaceSetting.getValue()) {
                           direction = Direction.DOWN;
                        } else {
                           MutableBlockPos fallbackAnchorSnapshot = this.scanPosition;
                           if (this.isAnchorBlock(fallbackAnchorSnapshot)) {
                              direction = Direction.DOWN;
                           }
                        }

                        if (direction == null) {
                           MutableBlockPos fluidPosition = this.scanPosition;
                           FluidState fluid = ClientAccess.argusclientMinecraft.level.getFluidState(fluidPosition);
                           if (!fluid.isEmpty() && this.liquidPlaceSetting.getValue()) {
                              direction = Direction.DOWN;
                           }
                        }

                        if (direction != null && targetDamage != null) {
                           MutableBlockPos damagePosition = this.scanPosition;
                           if (!this.shouldRejectDamage(damagePosition, targetDamage)) {
                              AnchorPlacementResult candidatePlacement = new AnchorPlacementResult(this.scanPosition.immutable(), direction, targetDamage);
                              MutableBlockPos anchorSnapshot = this.scanPosition;
                              if (this.isAnchorBlock(anchorSnapshot)) {
                                 return candidatePlacement;
                              }

                              bestPlacement = candidatePlacement.selectHigherDamage(bestPlacement);
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return bestPlacement;
   }

   private void updateCalculation() {
      if (this.placementData.get() != null) {
         BlockPos positionSnapshot = ((AnchorPlacementResult)this.placementData.get()).base();
         if (this.isInvalidPlacement(positionSnapshot)) {
            this.placementData.set(null);
         }
      }

      if (this.calculationFuture != null && this.calculationFuture.isDone() && this.placementData.get() == null) {
         try {
            AnchorPlacementResult completedPlacement = this.calculationFuture.get();
            if (completedPlacement == null) {
               this.placementData.set(null);
            } else {
               this.placementData.set(completedPlacement);
               this.targetDamage = completedPlacement.targetDamage();
            }
         } catch (InterruptedException | ExecutionException failure) {
            failure.printStackTrace();
         }
      }

      if (this.calculationFuture == null || this.calculationFuture.isDone() || this.calculationFuture.isCancelled()) {
         if (this.targetDamage != null) {
            Player currentTarget = this.targetDamage.target();
            double validationRange = this.targetRangeSetting.getValue().floatValue();
            if (AnchorTargetDamage.isInvalidTarget(currentTarget, validationRange)) {
               this.targetDamage = null;
            }
         }

         Supplier<AnchorPlacementResult> calculation = this::calculatePlacement;
         Executor calculationExecutor = ClientAccess.executorService;
         CompletableFuture<AnchorPlacementResult> future = CompletableFuture.supplyAsync(calculation, calculationExecutor);
         Function<Throwable, AnchorPlacementResult> failureHandler = failure -> null;
         this.calculationFuture = future.exceptionally(failureHandler);
      }
   }

   @EventHandler
   private void onPacketReceived(PacketEvent.Receive event) {
      if (this.sequentialSetting.getValue() && this.placementData.get() != null) {
         if (event.getPacket() instanceof ClientboundBlockUpdatePacket packet && this.targetDamage != null) {
            BlockState state = packet.getBlockState();
            if (state.isAir()) {
               BlockPos updatedPosition = packet.getPos();
               AnchorPlacementResult placementSnapshot = (AnchorPlacementResult)this.placementData.get();
               if (updatedPosition.equals(placementSnapshot.base())) {
                  placementSnapshot = (AnchorPlacementResult)this.placementData.get();
                  BlockPos baseSnapshot = placementSnapshot.base();
                  Direction directionSnapshot = ((AnchorPlacementResult)this.placementData.get()).direction();
                  InteractionHand placeHand = this.selectAnchorHand();
                  this.placeAnchor(baseSnapshot, directionSnapshot, placeHand);
               }
            }
         }
      }
   }

   @EventHandler
   private void onPlayerUpdate(RotationRequestEvent event) {
      DimensionType dimension = WorldUtils.getDimension();
      if (dimension.isNether()) {
         Component warningComponent = Component.nullToEmpty("Doesn't work in the nether!");
         MessageSignature warningSignature = ChatUtils.createSignature(-1);
         ChatUtils.MessagePriority warningPriority = ChatUtils.MessagePriority.HIGH;
         ChatUtils.printChat(warningComponent, warningSignature, warningPriority);
         this.disable();
      } else {
         this.updateCalculation();
         if (!this.shouldPause()) {
            AnchorPlacementResult placement = (AnchorPlacementResult)this.placementData.get();
            if (placement != null && this.selectAnchorHand() != null) {
               BlockPos position = placement.base();
               BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(position);
               int originalSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
               Block anchorBlock = Blocks.RESPAWN_ANCHOR;
               if (state.is(anchorBlock)) {
                  Direction direction = this.selectSupportDirection(position);
                  if (direction == null) {
                     return;
                  }

                  ElapsedTimer timerSnapshot = this.actionTimer;
                  int configuredDelay = this.delaySetting.getValue();
                  ServerStateManager tickManagerSnapshot = ClientContext.serverStateManager;
                  long delayMillis = configuredDelay + tickManagerSnapshot.getLatencyMillis();
                  if (!timerSnapshot.hasElapsed(delayMillis)) {
                     return;
                  }

                  IntegerProperty chargeProperty = RespawnAnchorBlock.CHARGE;
                  Integer charges = state.getValue(chargeProperty);
                  if (charges == 0) {
                     this.chargeAnchor(position, direction, originalSlot);
                  }

                  if (this.rotateSetting.getValue()) {
                     RotationManager rotationSnapshot = ClientContext.rotationManager;
                     Vec3 center = position.getCenter();
                     rotationSnapshot.requestRotation(RotationManager.getRotationsToPosition(center), 100);
                  }

                  Vec3 detonationCenter = Vec3.atCenterOf(position);
                  InteractionHand detonationHand = InteractionHand.MAIN_HAND;
                  this.interactWithAnchor(position, direction, detonationCenter, detonationHand);
                  if (this.inhibitSetting.getValue()) {
                     ClientLevel levelSnapshot = ClientAccess.argusclientMinecraft.level;
                     Block airBlock = Blocks.AIR;
                     BlockState airState = airBlock.defaultBlockState();
                     levelSnapshot.setBlockAndUpdate(position, airState);
                  }
               }

               Direction directionSnapshot = placement.direction();
               InteractionHand placementHand = this.selectAnchorHand();
               this.placeAnchor(position, directionSnapshot, placementHand);
            }
         }
      }
   }

   public static void bindReferencedModules(ModuleRegistry registry) {
      antiCheat = (AntiCheatModule)registry.getModule(AntiCheatModule.class);
      offhand = (OffhandModule)registry.getModule(OffhandModule.class);
      autoCrystal = (AutoCrystalModule)registry.getModule(AutoCrystalModule.class);
      speedMine = (SpeedMineModule)registry.getModule(SpeedMineModule.class);
   }

   private static String createDescription() {
      String warningColor = String.valueOf(ChatFormatting.RED);
      return "Explodes respawn anchors to kill players. \n" + warningColor + "Doesn't work in the nether.";
   }

   public AnchorAuraModule() {
      super("AnchorAura", createDescription(), ModuleCategory.COMBAT);
      FeatureBoxRenderer renderer = ClientContext.featureBoxRenderer;
      FeatureBoxRenderer.RenderConfiguration renderConfiguration = new FeatureBoxRenderer.RenderConfiguration(
         this, this.fillSetting, this.outlineSetting, () -> 1.0F, this.fadeTimeSetting, () -> false, this.fadeSetting, 450
      );
      renderer.register(renderConfiguration);
   }
}
