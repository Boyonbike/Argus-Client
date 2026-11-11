package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.managers.MovementPredictionManager;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.FeatureBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.entity.PlayerStateTracker;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.network.PacketSender;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class BedAuraModule extends Module {
   private final Setting<Boolean> placeSetting = this.add(new BooleanSetting("Place", true).markVisibilityDependency());
   private final Setting<Integer> placeDelaySetting = this.add(new NumberSetting<>("Delay", 1, 1, 20).dependsOn(this.placeSetting));
   final Setting<Float> placeRangeSetting = this.add(new NumberSetting<>("Range", 4.0F, 0.1F, 6.0F).dependsOn(this.placeSetting));
   final Setting<Boolean> rotateSetting = this.add(new BooleanSetting("Rotate", true).dependsOn(this.placeSetting));
   final Setting<Boolean> liquidPlaceSetting = this.add(new BooleanSetting("LiquidPlace", false).dependsOn(this.placeSetting));
   final Setting<Boolean> airPlaceSetting = this.add(new BooleanSetting("AirPlace", false).dependsOn(this.placeSetting));
   final Setting<Boolean> breakSetting = this.add(new BooleanSetting("Break", true).markVisibilityDependency());
   private final Setting<Integer> breakDelaySetting = this.add(new NumberSetting<>("Delay", 1, 1, 20).dependsOn(this.breakSetting));
   final Setting<Float> breakRangeSetting = this.add(new NumberSetting<>("Range", 4.0F, 0.1F, 6.0F).dependsOn(this.breakSetting));
   final Setting<Boolean> tpsSyncSetting = this.add(new BooleanSetting("TPSSync", false).dependsOn(this.breakSetting));
   final Setting<Boolean> instantSetting = this.add(new BooleanSetting("Instant", true).dependsOn(this.breakSetting));
   private final Setting<Boolean> extrapolationSetting = this.add(new BooleanSetting("Extrapolation", false).markVisibilityDependency());
   private final Setting<Integer> ticksSetting = this.add(new NumberSetting<>("Ticks", 3, 1, 8).dependsOn(this.extrapolationSetting));
   private final Setting<Boolean> refillSetting = this.add(new BooleanSetting("Refill", false).markVisibilityDependency());
   private final Setting<Integer> slotSetting = this.add(new NumberSetting<>("Slot", 1, 1, 9).dependsOn(this.refillSetting));
   private final Setting<Boolean> renderSetting = this.add(new BooleanSetting("Render", true).markVisibilityDependency());
   private final Setting<Color> fillSetting = this.add(new ColorSetting("Fill", new Color(1136499199, true)).dependsOn(this.renderSetting));
   private final Setting<Color> outlineSetting = this.add(new ColorSetting("Outline", new Color(-910321153, true)).dependsOn(this.renderSetting));
   private final Setting<Boolean> fadeSetting = this.add(new BooleanSetting("Fade", true).markVisibilityDependency().dependsOn(this.renderSetting));
   private final Setting<Float> fadeTimeSetting = this.add(
      new NumberSetting<>("FadeTime", 0.5F, 0.1F, 1.0F).withUnit("s").dependsOn(this.renderSetting, this.fadeSetting)
   );
   private final Setting<Boolean> targetingSetting = this.add(new BooleanSetting("Targeting", false).markAsGroup().markVisibilityDependency());
   final Setting<Float> minDamageSetting = this.add(new NumberSetting<>("MinDamage", 6.0F, 0.0F, 12.0F).dependsOn(this.targetingSetting));
   final Setting<Float> maxSelfDamageSetting = this.add(new NumberSetting<>("MaxSelfDamage", 10.0F, 0.0F, 20.0F).dependsOn(this.targetingSetting));
   final Setting<Boolean> antiSuicideSetting = this.add(new BooleanSetting("AntiSucide", true).dependsOn(this.targetingSetting));
   private final BedPlacementScanner placementScanner = new BedPlacementScanner(this);
   private final BedBreakScanner breakScanner = new BedBreakScanner(this);
   private final ElapsedTimer placeTimer = new ElapsedTimer();
   private final ElapsedTimer breakTimer = new ElapsedTimer();
   private Player target;
   private BedPlacementCandidate placement;
   private BlockPos breakPosition;

   private void refillBeds() {
      if (this.refillSetting.getValue()) {
         int refillSlot = this.slotSetting.getValue() - 1;
         ItemStack currentStack = ClientAccess.argusclientMinecraft.player.getInventory().getItem(refillSlot);
         if (!(currentStack.is(ItemTags.BEDS))) {
            Predicate<ItemStack> bedPredicate = candidateStack -> candidateStack.is(ItemTags.BEDS);
            int bedSlot = InventoryUtils.findInventorySlot(bedPredicate, true);
            if (bedSlot != -1) {
               InventoryUtils.swapWithHotbarSlot(bedSlot, refillSlot);
            }
         }
      }
   }

   private void breakBed(BlockPos position) {
      if (position != null) {
         Direction fallbackSnapshot = Direction.UP;
         Direction direction = selectInteractionDirection(position, fallbackSnapshot);
         BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(position), direction, position, false);
         PlayerStateTracker interactionScheduler = ClientContext.playerStateTracker;
         Runnable interactionCallback = () -> {
            MultiPlayerGameMode gameMode = ClientAccess.argusclientMinecraft.gameMode;
            LocalPlayer interactionPlayer = ClientAccess.argusclientMinecraft.player;
            InteractionHand interactionHand = InteractionHand.MAIN_HAND;
            gameMode.useItemOn(interactionPlayer, interactionHand, hit);
            LocalPlayer swingPlayer = ClientAccess.argusclientMinecraft.player;
            InteractionHand swingHand = InteractionHand.MAIN_HAND;
            swingPlayer.swing(swingHand, swingPlayer.getItemInHand(swingHand).getAttackAnimation(), false);
         };
         interactionScheduler.runWithoutSneaking(interactionCallback);

         for (Direction adjacentDirection : Plane.HORIZONTAL) {
            BlockPos adjacentPosition = position.relative(adjacentDirection);
            BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(adjacentPosition);
            if (state.getBlock() instanceof BedBlock) {
               ClientContext.worldStateManager.destroyBlockPredicted(adjacentPosition);
               break;
            }
         }

         ClientContext.worldStateManager.destroyBlockPredicted(position);
         this.breakTimer.reset();
      }
   }

   @Override
   public String getInfo() {
      return this.target == null ? null : this.target.getGameProfile().name();
   }

   private boolean hasActionDelayElapsed(ElapsedTimer timer, Setting<Integer> delaySettingSnapshot) {
      Boolean synchronize = this.tpsSyncSetting.getValue();
      float factorSnapshot;
      if (synchronize) {
         factorSnapshot = ClientContext.serverStateManager.getTickRateScale();
      } else {
         factorSnapshot = 1.0F;
      }

      Integer delayTicks = delaySettingSnapshot.getValue();
      return timer.hasElapsed((long)((float)(50L * delayTicks.intValue()) * factorSnapshot));
   }

   private void placeWithBedSlot(BedPlacementCandidate placement) {
      Predicate<ItemStack> bedPredicate = candidateStack -> candidateStack.is(ItemTags.BEDS);
      int bedSlot = InventoryUtils.findHotbarSlot(bedPredicate);
      int originalSlot = ClientAccess.argusclientMinecraft.player.getInventory().getSelectedSlot();
      if (bedSlot != -1) {
         InventoryUtils.selectHotbarSlot(bedSlot);
         this.placeBed(placement);
         InventoryUtils.selectHotbarSlot(originalSlot);
      }
   }

   protected boolean isBedPart(BlockPos position, BedPart part) {
      BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(position);
      if (state.getBlock() instanceof BedBlock) {
         EnumProperty partProperty = BedBlock.PART;
         if (state.getValue(partProperty) == part) {
            return true;
         }
      }

      return false;
   }

   private Player selectTarget() {
      Player closestTarget = null;
      float closestDistance = 99999.0F;

      for (Player candidate : ClientAccess.argusclientMinecraft.level.players()) {
         if (candidate != ClientAccess.argusclientMinecraft.player && !ClientContext.socialRegistry.isFriendPlayer(candidate)) {
            double distanceLimit = closestDistance;
            Vec3 comparisonEyeSnapshot = ClientAccess.argusclientMinecraft.player.getEyePosition();
            Vec3 comparisonPosition = candidate.position();
            if (distanceLimit > comparisonEyeSnapshot.distanceTo(comparisonPosition)) {
               closestTarget = candidate;
               closestDistance = (float)ClientAccess.argusclientMinecraft.player.getEyePosition().distanceTo(candidate.position());
            }
         }
      }

      return closestTarget;
   }

   public AABB getDamageBounds(LivingEntity entity) {
      if (entity instanceof Player player && this.extrapolationSetting.getValue()) {
         MovementPredictionManager extrapolatorSnapshot = ClientContext.movementPredictionManager;
         int extrapolationTicks = this.ticksSetting.getValue();
         return extrapolatorSnapshot.getPredictedBoundingBox(player, extrapolationTicks);
      } else {
         return entity.getBoundingBox();
      }
   }

   private static Direction selectInteractionDirection(BlockPos position, Direction fallbackDirection) {
      BlockInteractionUtils.getStrictDirections(position);
      return fallbackDirection;
   }

   @EventHandler
   private void onPlayerUpdate(RotationRequestEvent event) {
      DimensionType dimension = WorldUtils.getDimension();
      if (!dimension.isOverworld()) {
         this.target = this.selectTarget();
         if (this.target == null) {
            this.placement = null;
         } else {
            BedPlacementScanner placementScannerSnapshot = this.placementScanner;
            Player placementTargetSnapshot = this.target;
            this.placement = placementScannerSnapshot.scanPlacement(placementTargetSnapshot);
            BedBreakScanner breakScannerSnapshot = this.breakScanner;
            Player breakTargetSnapshot = this.target;
            this.breakPosition = breakScannerSnapshot.scanBreakPosition(breakTargetSnapshot);
         }

         if (this.placement != null) {
            this.refillBeds();
            if (this.breakSetting.getValue()) {
               Setting<Integer> breakDelaySetting = this.breakDelaySetting;
               ElapsedTimer breakDelayTimer = this.breakTimer;
               if (this.hasActionDelayElapsed(breakDelayTimer, breakDelaySetting)) {
                  BlockPos breakPositionSnapshot = this.breakPosition;
                  this.breakBed(breakPositionSnapshot);
               }
            }

            if (this.placeSetting.getValue()) {
               Setting<Integer> placeDelaySetting = this.placeDelaySetting;
               ElapsedTimer placeDelayTimer = this.placeTimer;
               if (this.hasActionDelayElapsed(placeDelayTimer, placeDelaySetting)) {
                  BedPlacementCandidate placementSnapshot = this.placement;
                  this.placeWithBedSlot(placementSnapshot);
               }
            }
         }
      }
   }

   private void placeBed(BedPlacementCandidate placement) {
      if (placement != null) {
         BlockPos footSnapshot = placement.foot();
         if (BlockInteractionUtils.canPlaceAt(footSnapshot, true)) {
            BlockHitResult placementHit = placement.hitResult();
            Vec3 hitPoint = placementHit.getLocation();
            if (hitPoint == null) {
               BlockPos fallbackFoot = placement.foot();
               Vec3.atCenterOf(fallbackFoot);
            }

            Boolean rotate = this.rotateSetting.getValue();
            float[] angles;
            if (rotate && hitPoint != null) {
               angles = RotationManager.getRotationsToPosition(hitPoint);
            } else {
               BlockPos directionFoot = placement.foot();
               Vec3 footCenterSnapshot = Vec3.atCenterOf(directionFoot);
               BlockPos directionHead = placement.head();
               Vec3 headCenterSnapshot = Vec3.atCenterOf(directionHead);
               angles = RotationManager.calculateRotations(footCenterSnapshot, headCenterSnapshot);
            }

            ClientContext.rotationManager.requestRotation(angles, 250);
            BlockHitResult hit = placement.hitResult();
            PlayerStateTracker placementScheduler = ClientContext.playerStateTracker;
            Runnable placementCallback = () -> {
               InteractionHand interactionHand = InteractionHand.MAIN_HAND;
               PacketSender.sendUseItemOn(interactionHand, hit);
            };
            placementScheduler.runSneaking(placementCallback);
            LocalPlayer swingPlayer = ClientAccess.argusclientMinecraft.player;
            InteractionHand swingHand = InteractionHand.MAIN_HAND;
            swingPlayer.swing(swingHand, swingPlayer.getItemInHand(swingHand).getAttackAnimation(), false);
            footSnapshot = placement.foot();
            BlockPos boundsHead = placement.head();
            AABB renderBounds = AABB.encapsulatingFullBlocks(footSnapshot, boundsHead);
            double minimumY = renderBounds.minY;
            double maximumY = minimumY + 0.5625;
            renderBounds = renderBounds.setMaxY(maximumY);
            renderBounds = renderBounds.inflate(0.002F);
            if (this.renderSetting.getValue()) {
               ClientContext.featureBoxRenderer.addBox(this, renderBounds);
            }

            if (this.breakSetting.getValue() && this.instantSetting.getValue()) {
               PlayerStateTracker breakScheduler = ClientContext.playerStateTracker;
               Runnable breakCallback = () -> {
                  BlockPos instantFootSnapshot = placement.foot();
                  Direction instantFallbackSnapshot = Direction.UP;
                  Direction instantDirection = selectInteractionDirection(instantFootSnapshot, instantFallbackSnapshot);
                  BlockHitResult instantHit = new BlockHitResult(Vec3.atCenterOf(placement.foot()), instantDirection, placement.foot(), false);
                  InteractionHand instantInteractionHand = InteractionHand.MAIN_HAND;
                  PacketSender.sendUseItemOn(instantInteractionHand, instantHit);
                  LocalPlayer instantSwingPlayer = ClientAccess.argusclientMinecraft.player;
                  InteractionHand instantSwingHand = InteractionHand.MAIN_HAND;
                  instantSwingPlayer.swing(instantSwingHand, instantSwingPlayer.getItemInHand(instantSwingHand).getAttackAnimation(), false);
               };
               breakScheduler.runWithoutSneaking(breakCallback);
            }

            this.placeTimer.reset();
         }
      }
   }

   private static String createDescription() {
      String warningColor = String.valueOf(ChatFormatting.RED);
      return "Blows your enemies up using beds. \n" + warningColor + "Doesn't work in the overworld.";
   }

   public BedAuraModule() {
      super("BedAura", createDescription(), ModuleCategory.COMBAT);
      FeatureBoxRenderer renderer = ClientContext.featureBoxRenderer;
      FeatureBoxRenderer.RenderConfiguration renderConfiguration = new FeatureBoxRenderer.RenderConfiguration(
         this, this.fillSetting, this.outlineSetting, () -> 1.0F, this.fadeTimeSetting, () -> false, this.fadeSetting, 1000
      );
      renderer.register(renderConfiguration);
      this.placeRangeSetting.setConfigName("PlaceRange");
      this.breakRangeSetting.setConfigName("BreakRange");
      this.placeDelaySetting.setConfigName("PlaceDelay");
      this.breakDelaySetting.setConfigName("BreakDelay");
   }
}
