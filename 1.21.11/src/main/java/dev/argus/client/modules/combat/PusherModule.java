package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.managers.InventoryStateManager;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.exploit.MultiTaskModule;
import dev.argus.client.render.FeatureBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.ValueProvider;
import dev.argus.client.settings.options.BlockSampleMode;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;

public class PusherModule extends Module {
   private static MultiTaskModule multiTask = ClientContext.getModule(MultiTaskModule.class);
   private final Setting<Float> rangeSetting;
   private final Setting<Boolean> alternativeSetting;
   private final Setting<Integer> delaySetting;
   private final Setting<Boolean> rotateSetting;
   private final Setting<Boolean> raytraceSetting;
   private final Setting<Boolean> autoDisableSetting;
   private final Setting<Boolean> targetingSetting;
   public final Setting<Boolean> ignoreNakedSetting;
   public final Setting<Boolean> onlySafeSetting;
   public final Setting<Boolean> onlyHoleSetting;
   final Setting<Boolean> renderSetting;
   private final Setting<Color> fillSetting;
   private final Setting<Color> outlineSetting;
   protected final Setting<Float> lineWidthSetting;
   private final Setting<Boolean> fadeSetting;
   private final Setting<Float> fadeTimeSetting;
   private final ElapsedTimer actionTimer;
   private Player target;
   private boolean powerPlacementFound;
   private final PusherFacingResolver facingResolver;
   private final PusherTargetSelector targetSelector;

   private boolean isAlternativeAllowed() {
      return this.alternativeSetting.getValue() && !InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 2);
   }

   private void placePower() {
      Item powerItemSnapshot = Items.REDSTONE_BLOCK;
      Item redstoneBlock = powerItemSnapshot;
      InventoryUtils.SwapSlot powerSlot = InventoryUtils.findSwapSlot(redstoneBlock, this.isAlternativeAllowed());
      boolean useTorch = false;
      if (!powerSlot.isFound()) {
         powerItemSnapshot = Items.REDSTONE_TORCH;
         Item redstoneTorch = powerItemSnapshot;
         powerSlot = InventoryUtils.findSwapSlot(redstoneTorch, this.isAlternativeAllowed());
         useTorch = true;
      }

      int originalSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
      if (powerSlot.isFound()) {
         PusherModule.PowerPlacement placement = null;

         for (Direction targetDirection : Plane.HORIZONTAL) {
            BlockPos headSnapshot = this.target.blockPosition().above();
            BlockPos pistonPosition = headSnapshot.relative(targetDirection);
            if (WorldUtils.getBlock(pistonPosition) instanceof PistonBaseBlock) {
               Player policyTarget = this.target;
               if (!this.isInvalidPistonPosition(policyTarget, pistonPosition, targetDirection)) {
                  boolean torchFlag = useTorch;
                  placement = this.evaluatePowerPlacement(torchFlag, pistonPosition);
               }
            }
         }

         if (placement != null) {
            this.powerPlacementFound = true;
            if (this.rotateSetting.getValue()) {
               RotationManager rotationSnapshot = ClientContext.rotationManager;
               BlockPos anglePosition = placement.position();
               Vec3 angleCenterSnapshot = anglePosition.getCenter();
               Direction angleDirection = placement.direction();
               rotationSnapshot.requestRotation(RotationManager.getRotationsToFace(angleCenterSnapshot, angleDirection), 500);
            }

            if (!powerSlot.hotbar()
               && powerSlot.hand() == InteractionHand.MAIN_HAND
               && ClientAccess.argusclientMinecraft.player.containerMenu != ClientAccess.argusclientMinecraft.player.inventoryMenu) {
               return;
            }

            InventoryUtils.swapTo(powerSlot);

            try {
               BlockPos placePositionSnapshot = placement.position();
               Direction placeDirectionSnapshot = placement.direction();
               InteractionHand placeHand = InteractionHand.MAIN_HAND;
               BlockInteractionUtils.placeBlockIgnoringCrystals(placePositionSnapshot, placeDirectionSnapshot, false, placeHand);
               InteractionHand swingHand = InteractionHand.MAIN_HAND;
               PacketSender.sendSwing(swingHand);
            } finally {
               InventoryUtils.swapBack(originalSlot, powerSlot);
            }

            if (this.renderSetting.getValue()) {
               FeatureBoxRenderer rendererSnapshot = ClientContext.featureBoxRenderer;
               BlockPos renderPosition = placement.position();
               rendererSnapshot.addBox(this, renderPosition);
            }
         }
      }
   }

   public float getRange() {
      return this.rangeSetting.getValue();
   }

   private void placePiston() {
      if (!this.powerPlacementFound) {
         InventoryUtils.SwapSlot pistonSlot = InventoryUtils.findSwapSlot(
            stack -> stack.is(Items.PISTON) || stack.is(Items.STICKY_PISTON), this.isAlternativeAllowed()
         );
         int originalSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
         if (pistonSlot.isFound()) {
            PusherModule.PistonPlacement placement = null;

            for (Direction targetDirection : Plane.HORIZONTAL) {
               BlockPos headSnapshot = this.target.blockPosition().above();
               BlockPos pistonPosition = headSnapshot.relative(targetDirection);
               Player policyTarget = this.target;
               if (!this.isInvalidPistonPosition(policyTarget, pistonPosition, targetDirection)) {
                  if (this.getPistonFacing(pistonPosition) == targetDirection.getOpposite()) {
                     placement = null;
                     break;
                  }

                  if (BlockInteractionUtils.canPlaceAt(pistonPosition, true)) {
                     Direction candidateFacingSnapshot = targetDirection.getOpposite();
                     PusherModule.PistonPlacement candidate = this.evaluatePistonPlacement(pistonPosition, candidateFacingSnapshot);
                     if (candidate != null) {
                        placement = candidate;
                     }
                  }
               }
            }

            if (placement != null) {
               RotationManager rotationSnapshot = ClientContext.rotationManager;
               Vec3 hitPointSnapshot = placement.hitPoint();
               rotationSnapshot.requestRotation(RotationManager.getRotationsToPosition(hitPointSnapshot), 500);
               if (!pistonSlot.hotbar()
                  && pistonSlot.hand() == InteractionHand.MAIN_HAND
                  && ClientAccess.argusclientMinecraft.player.containerMenu != ClientAccess.argusclientMinecraft.player.inventoryMenu) {
                  return;
               }

               InventoryUtils.swapTo(pistonSlot);

               try {
                  BlockPos placePositionSnapshot = placement.position();
                  Direction placeDirectionSnapshot = placement.direction();
                  InteractionHand placeHand = InteractionHand.MAIN_HAND;
                  BlockInteractionUtils.placeBlockIgnoringCrystals(placePositionSnapshot, placeDirectionSnapshot, false, placeHand);
                  InteractionHand swingHand = InteractionHand.MAIN_HAND;
                  PacketSender.sendSwing(swingHand);
               } finally {
                  InventoryUtils.swapBack(originalSlot, pistonSlot);
               }

               if (this.renderSetting.getValue()) {
                  FeatureBoxRenderer rendererSnapshot = ClientContext.featureBoxRenderer;
                  BlockPos renderPosition = placement.position();
                  rendererSnapshot.addBox(this, renderPosition);
               }
            }
         }
      }
   }

   @Override
   public String getInfo() {
      return this.target != null ? this.target.getGameProfile().name() : super.getInfo();
   }

   private Direction selectPowerDirection(boolean useTorch, BlockPos position, Direction excludedDirection) {
      if (!useTorch) {
         boolean placementMode = BlockInteractionUtils.isStrictDirectionEnabled();
         return BlockInteractionUtils.findSupportDirection(position, placementMode);
      }

      Direction[] directions = Direction.values();

      for (Direction direction : directions) {
         BlockPos adjacentPosition = position.relative(direction);
         if (excludedDirection != direction && ClientContext.worldStateManager.isPlacementOccupied(adjacentPosition)) {
            if (!BlockInteractionUtils.isStrictDirectionEnabled()) {
               return direction;
            }

            List supportDirections = BlockInteractionUtils.getStrictDirections(adjacentPosition);
            if (supportDirections.contains(direction.getOpposite())) {
               return direction;
            }
         }
      }

      return null;
   }

   private boolean isInvalidPistonPosition(LivingEntity target, BlockPos position, Direction direction) {
      Vec3 eyeSnapshot = ClientAccess.argusclientMinecraft.player.getEyePosition();
      Vec3 center = position.getCenter();
      double distance = eyeSnapshot.distanceTo(center);
      if (distance > this.getRange()) {
         return true;
      }

      Direction oppositeSnapshot = direction.getOpposite();
      BlockPos destination = position.relative(oppositeSnapshot, 2);
      int replaceableCount = 0;

      for (BlockPos occupiedPosition : PlayerStateUtils.getSurroundPositions(target)) {
         if (ClientContext.worldStateManager.isPlacementOccupied(occupiedPosition)) {
            replaceableCount++;
         }
      }

      ClientLevel belowLevelSnapshot = ClientAccess.argusclientMinecraft.level;
      BlockPos belowPosition = destination.below();
      BlockState belowState = belowLevelSnapshot.getBlockState(belowPosition);
      boolean replaceableBelow = belowState.canBeReplaced();
      if (replaceableCount > 2 && replaceableBelow) {
         return true;
      }

      BlockState destinationState = ClientAccess.argusclientMinecraft.level.getBlockState(destination);
      return !destinationState.canBeReplaced();
   }

   private PusherModule.PistonPlacement evaluatePistonPlacement(BlockPos position, Direction facing) {
      for (Direction direction : Direction.values()) {
         BlockPos adjacentPosition = position.relative(direction);
         if (ClientContext.worldStateManager.isPlacementOccupied(adjacentPosition)) {
            if (BlockInteractionUtils.isStrictDirectionEnabled()) {
               List supportDirections = BlockInteractionUtils.getStrictDirections(adjacentPosition);
               if (!supportDirections.contains(direction.getOpposite())) {
                  continue;
               }
            }

            if (this.raytraceSetting.getValue()) {
               BlockSampleMode samplingMode = BlockSampleMode.X8;
               if (!WorldUtils.canSeeAny(samplingMode.sampleBlock(adjacentPosition))) {
                  continue;
               }
            }

            if (!this.rotateSetting.getValue()) {
               Vec3 eyeSnapshot = ClientAccess.argusclientMinecraft.player.getEyePosition();
               Direction fallbackDirectionSnapshot = facing.getOpposite();
               Vec3 fallbackPoint = eyeSnapshot.relative(fallbackDirectionSnapshot, 1.0);
               return new PusherModule.PistonPlacement(direction, position, fallbackPoint);
            }

            BlockSampleMode samplerSnapshot = BlockSampleMode.X8;
            Direction faceDirection = direction.getOpposite();

            for (Vec3 sample : samplerSnapshot.sampleFace(adjacentPosition, faceDirection)) {
               if (this.facingResolver.resolveFacing(RotationManager.getRotationsToPosition(sample)) == facing.getOpposite()) {
                  return new PusherModule.PistonPlacement(direction, position, sample);
               }
            }
         }
      }

      return null;
   }

   private PusherModule.PowerPlacement evaluatePowerPlacement(boolean useTorch, BlockPos pistonPosition) {
      PusherModule.PowerPlacement placement = null;

      for (Direction direction : Direction.values()) {
         BlockPos position = pistonPosition.relative(direction);
         BlockState stateSnapshot = ClientAccess.argusclientMinecraft.level.getBlockState(position);
         Block redstoneBlock = Blocks.REDSTONE_BLOCK;
         if (stateSnapshot.is(redstoneBlock)) {
            return null;
         }

         if (BlockInteractionUtils.canPlaceAt(position, true)) {
            Direction excludedDirection = direction.getOpposite();
            boolean torchFlag = useTorch;
            Direction supportDirection = this.selectPowerDirection(torchFlag, position, excludedDirection);
            if (supportDirection != null) {
               placement = new PusherModule.PowerPlacement(supportDirection, position);
            }
         }
      }

      return placement;
   }

   private Direction getPistonFacing(BlockPos position) {
      BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(position);
      if (state.getBlock() instanceof PistonBaseBlock) {
         EnumProperty<Direction> facingProperty = DirectionalBlock.FACING;
         return state.getValue(facingProperty);
      } else {
         return null;
      }
   }

   @EventHandler
   private void onPlayerUpdate(RotationRequestEvent event) {
      if (!multiTask.shouldPauseModule(this)) {
         PusherTargetSelector selectorSnapshot = this.targetSelector;
         this.target = selectorSnapshot.selectTarget();
         if (this.target != null) {
            if (this.autoDisableSetting.getValue()) {
               boolean pistonOverlap = BlockPos.betweenClosedStream(this.target.getBoundingBox()).anyMatch(position -> {
                  BlockState stateSnapshot = ClientAccess.argusclientMinecraft.level.getBlockState(position);
                  Block pistonHead = Blocks.PISTON_HEAD;
                  if (!stateSnapshot.is(pistonHead)) {
                     stateSnapshot = ClientAccess.argusclientMinecraft.level.getBlockState(position);
                     Block pistonBlock = Blocks.PISTON;
                     if (!stateSnapshot.is(pistonBlock)) {
                        return false;
                     }
                  }

                  return true;
               });
               if (pistonOverlap) {
                  this.disable();
                  return;
               }
            }

            ElapsedTimer timerSnapshot = this.actionTimer;
            long delayMillis = this.delaySetting.getValue().intValue();
            if (timerSnapshot.hasElapsed(delayMillis)) {
               this.powerPlacementFound = false;
               timerSnapshot = this.actionTimer;
               timerSnapshot.reset();
               this.placePower();
               this.placePiston();
            }
         }
      }
   }

   public static void bindModules(ModuleRegistry modules) {
      multiTask = (MultiTaskModule)modules.getModule(MultiTaskModule.class);
   }

   public PusherModule() {
      super("Pusher", "Pushes your enemies out of their holes.", ModuleCategory.COMBAT);
      String rangeLabel = "Range";
      this.alternativeSetting = this.add(new BooleanSetting("Alternative", false));
      this.rangeSetting = this.add(new NumberSetting<>(rangeLabel, 5.0F, 1.0F, 6.0F).withUnit("m"));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 200, 0, 500).withUnit("ms"));
      this.rotateSetting = this.add(new BooleanSetting("Rotate", false));
      this.raytraceSetting = this.add(new BooleanSetting("Raytrace", false));
      this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", false));
      this.targetingSetting = this.add(new BooleanSetting("Targeting", false).markAsGroup().markVisibilityDependency());
      this.ignoreNakedSetting = this.add(new BooleanSetting("IgnoreNaked", false).dependsOn(this.targetingSetting));
      this.onlySafeSetting = this.add(new BooleanSetting("OnlySafe", false).dependsOn(this.targetingSetting));
      this.onlyHoleSetting = this.add(new BooleanSetting("OnlyHole", false).dependsOn(this.targetingSetting));
      this.renderSetting = this.add(new BooleanSetting("Render", true).markVisibilityDependency());
      String fillLabel = "Fill";
      Predicate fillPredicate = unused -> this.renderSetting.isExpanded();
      this.fillSetting = this.add(new ColorSetting(fillLabel, new Color(1136499199, true), fillPredicate));
      String outlineLabel = "Outline";
      Predicate outlinePredicate = unused -> this.renderSetting.isExpanded();
      this.outlineSetting = this.add(new ColorSetting(outlineLabel, new Color(-910321153, true), outlinePredicate));
      this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 3.0F).dependsOn(this.renderSetting));
      this.fadeSetting = this.add(new BooleanSetting("Fade", true).markVisibilityDependency().dependsOn(this.renderSetting));
      this.fadeTimeSetting = this.add(new NumberSetting<>("FadeTime", 1.0F, 0.1F, 1.0F).withUnit("s").dependsOn(this.renderSetting, this.fadeSetting));
      this.actionTimer = new ElapsedTimer();
      this.facingResolver = new PusherFacingResolver();
      this.targetSelector = new PusherTargetSelector(this);
      FeatureBoxRenderer rendererSnapshot = ClientContext.featureBoxRenderer;
      Setting<Color> fillSettingSnapshot = this.fillSetting;
      Setting<Color> outlineSettingSnapshot = this.outlineSetting;
      Setting<Float> lineWidthSettingSnapshot = this.lineWidthSetting;
      Setting<Float> fadeTimeSettingSnapshot = this.fadeTimeSetting;
      ValueProvider<Boolean> flagSupplier = () -> true;
      Setting<Boolean> fadeSettingSnapshot = this.fadeSetting;
      FeatureBoxRenderer.RenderConfiguration renderConfiguration = new FeatureBoxRenderer.RenderConfiguration(
         this, fillSettingSnapshot, outlineSettingSnapshot, lineWidthSettingSnapshot, fadeTimeSettingSnapshot, flagSupplier, fadeSettingSnapshot, 450
      );
      rendererSnapshot.register(renderConfiguration);
   }

   record PistonPlacement(Direction direction, BlockPos position, Vec3 hitPoint) {
   }

   record PowerPlacement(Direction direction, BlockPos position) {
   }
}
