package dev.argus.client.modules.combat;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.managers.InventoryStateManager;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.managers.WorldStateManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.exploit.MultiTaskModule;
import dev.argus.client.modules.player.SpeedMineModule;
import dev.argus.client.render.FeatureBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.entity.SwingAction;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.inventory.ItemUtils;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class AntiFeetPlaceModule extends Module {
   private static SpeedMineModule speedMine = ClientContext.getModule(SpeedMineModule.class);
   private static MultiTaskModule multiTask = ClientContext.getModule(MultiTaskModule.class);
   private final Setting<AntiFeetPlaceModule.ModeChoice> modeSetting = this.add(new EnumSetting<>("Mode", AntiFeetPlaceModule.ModeChoice.GLASS));
   private final Setting<Integer> delaySetting = this.add(new NumberSetting<>("Delay", 0, 0, 500));
   private final Setting<Boolean> airPlaceSetting = this.add(new BooleanSetting("AirPlace", false));
   private final Setting<Boolean> raytraceSetting = this.add(new BooleanSetting("Raytrace", false));
   private final Setting<Boolean> rotateSetting = this.add(new BooleanSetting("Rotate", false));
   final Setting<Boolean> renderSetting = this.add(new BooleanSetting("Render", false).markVisibilityDependency());
   private final Setting<Color> fillSetting;
   private final Setting<Color> outlineSetting;
   private final Setting<Float> lineWidthSetting;
   private final Setting<Boolean> fadeSetting;
   private final Setting<Float> fadeTimeSetting;
   private final ElapsedTimer placementTimer;

   private boolean shouldPauseReplacement() {
      SpeedMineModule positionCheckSource = speedMine;
      BlockPos miningPosition;
      if (positionCheckSource.getMiningPos() != null) {
         miningPosition = speedMine.getMiningPos();
      } else {
         miningPosition = speedMine.getRebreakPos();
      }

      if (speedMine.isToggled() && miningPosition != null) {
         double miningProgress = speedMine.getRenderProgress();
         if (miningProgress < 0.95 && !WorldUtils.isAir(miningPosition)) {
            return true;
         } else if (multiTask.shouldPauseModule(this)) {
            return true;
         } else {
            ElapsedTimer timerSnapshot = this.placementTimer;
            long delayMillis = this.delaySetting.getValue().intValue();
            if (!timerSnapshot.hasElapsed(delayMillis)) {
               return true;
            } else {
               return BlockInteractionUtils.canPlayerInteract() && !PlayerStateUtils.isFallFlying()
                  ? OffhandModule.isRecentSwapWindowActive() || SpeedMineModule.isSwapPending()
                  : true;
            }
         }
      } else {
         return true;
      }
   }

   private void replaceMinedBlock() {
      if (!this.shouldPauseReplacement()) {
         SpeedMineModule positionCheckSource = speedMine;
         BlockPos miningPosition;
         if (positionCheckSource.getMiningPos() != null) {
            miningPosition = speedMine.getMiningPos();
         } else {
            miningPosition = speedMine.getRebreakPos();
         }

         AABB placementBounds = new AABB(miningPosition);
         if (!this.hasNoEligibleEnemy(miningPosition)
            && !BlockInteractionUtils.isOutsidePlacementBounds(miningPosition)
            && !BlockInteractionUtils.hasBlockingEntity(placementBounds, true, true)) {
            BlockPos belowPositionSnapshot = miningPosition.below();
            if (!WorldUtils.isBedrockOrObsidian(belowPositionSnapshot)) {
               return;
            }

            boolean strictDirectionEnabled = BlockInteractionUtils.isStrictDirectionEnabled();
            boolean raytraceFlag = this.raytraceSetting.getValue();
            boolean supportStrictFlag = strictDirectionEnabled;
            Direction supportDirection = BlockInteractionUtils.findSupportDirection(miningPosition, supportStrictFlag, raytraceFlag);
            if (supportDirection == null && !this.airPlaceSetting.getValue()) {
               return;
            }

            int previousSlot = InventoryUtils.getSelectedSlot();
            InventoryUtils.SwapSlot selectedItem = this.selectPlacementItem();
            Runnable placementCallback = () -> {
               InteractionHand interactionHand = selectedItem.hand();
               Direction callbackDirection = supportDirection;
               this.placeReplacement(miningPosition, callbackDirection, interactionHand);
            };
            InventoryUtils.runWithSwap(previousSlot, selectedItem, placementCallback);
            return;
         }
      }
   }

   private void placeReplacement(BlockPos position, Direction supportDirection, InteractionHand hand) {
      this.placementTimer.reset();
      boolean airPlaceEnabled = this.airPlaceSetting.getValue();
      BlockInteractionUtils.sendPlacement(position, null, supportDirection, airPlaceEnabled, hand);
      SwingAction swingKind = SwingAction.INTERACT;
      InteractionHand swingHand = InteractionHand.MAIN_HAND;
      ClientContext.swingManager.swing(swingHand, swingKind);
      if (this.renderSetting.getValue()) {
         ClientContext.featureBoxRenderer.addBox(this, position);
      }

      if (this.rotateSetting.getValue()) {
         ClientContext.rotationManager.requestRotation(RotationManager.getRotationsToBlock(position), 350);
      }
   }

   private boolean hasNoEligibleEnemy(BlockPos position) {
      for (AbstractClientPlayer target : ClientAccess.argusclientMinecraft.level.players()) {
         if (target != ClientAccess.argusclientMinecraft.player
            && !ClientContext.socialRegistry.isFriendPlayer(target)
            && !target.isDeadOrDying()
            && target.isAlive()) {
            double xSnapshot = position.getX();
            double ySnapshot = position.getY();
            double z = position.getZ();
            double distanceSquared = target.distanceToSqr(xSnapshot, ySnapshot, z);
            if (!(distanceSquared > 36.0) && PlayerStateUtils.hasArmor(target)) {
               Set candidatePositions = PlayerStateUtils.getSurroundPositions(target);
               if (candidatePositions.contains(position)) {
                  boolean hasNoAdjacentAir = true;

                  for (BlockPos feetPosition : PlayerStateUtils.getOccupiedBlockPositions(target)) {
                     BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(feetPosition);
                     if (state.isAir() && feetPosition.distManhattan(position) == 1) {
                        hasNoAdjacentAir = false;
                        break;
                     }
                  }

                  if (!hasNoAdjacentAir) {
                     return false;
                  }
               }
            }
         }
      }

      return true;
   }

   private InventoryUtils.SwapSlot selectPlacementItem() {
      AntiFeetPlaceModule.ModeChoice mode = this.modeSetting.getValue();
      int hotbarSlot = InventoryUtils.findHotbarSlot(mode.itemPredicate);
      if (this.isHoldingPlacementItemOffhand()) {
         return new InventoryUtils.SwapSlot(InventoryUtils.getSelectedSlot(), true, InteractionHand.OFF_HAND);
      } else {
         return hotbarSlot == -1 && !InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 3)
            ? new InventoryUtils.SwapSlot(InventoryUtils.findInventorySlot(mode.itemPredicate, true), false, InteractionHand.MAIN_HAND)
            : new InventoryUtils.SwapSlot(hotbarSlot, true, InteractionHand.MAIN_HAND);
      }
   }

   private boolean isHoldingPlacementItemOffhand() {
      return this.modeSetting.getValue().itemPredicate.test(ClientAccess.argusclientMinecraft.player.getOffhandItem());
   }

   @EventHandler(priority = -100)
   public void onPlayerUpdate(RotationRequestEvent event) {
      this.replaceMinedBlock();
   }

   public static void bindModules(ModuleRegistry modules) {
      speedMine = (SpeedMineModule)modules.getModule(SpeedMineModule.class);
      multiTask = (MultiTaskModule)modules.getModule(MultiTaskModule.class);
   }

   private static String createDescription() {
      String warningColor = String.valueOf(ChatFormatting.RED);
      return "Tries to replace the enemy's feet block with a faster-breaking one.\n" + warningColor + "Requires SpeedMine module enabled.";
   }

   public AntiFeetPlaceModule() {
      super("AntiFeetPlace", createDescription(), ModuleCategory.COMBAT);
      Predicate fillVisibility = unused -> this.renderSetting.isExpanded();
      this.fillSetting = this.add(new ColorSetting("Fill", new Color(146643455, true), fillVisibility));
      Predicate outlineVisibility = unused -> this.renderSetting.isExpanded();
      this.outlineSetting = this.add(new ColorSetting("Outline", new Color(1539152383, true), outlineVisibility));
      this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 3.0F).dependsOn(this.renderSetting));
      this.fadeSetting = this.add(new BooleanSetting("Fade", true).dependsOn(this.renderSetting));
      this.fadeTimeSetting = this.add(new NumberSetting<>("FadeTime", 1.0F, 0.1F, 1.0F).withUnit("s").dependsOn(this.renderSetting, this.fadeSetting));
      this.placementTimer = new ElapsedTimer();
      FeatureBoxRenderer rendererSnapshot = ClientContext.featureBoxRenderer;
      FeatureBoxRenderer.RenderConfiguration renderConfiguration = new FeatureBoxRenderer.RenderConfiguration(
         this, this.fillSetting, this.outlineSetting, this.lineWidthSetting, this.fadeTimeSetting, () -> true, this.fadeSetting, 450
      );
      rendererSnapshot.register(renderConfiguration);
   }

   private enum ModeChoice implements NamedEntry {
      GLASS("Glass", stack -> {
         Item itemSnapshot = stack.getItem();
         return ItemUtils.isGlass(itemSnapshot);
      }),
      ICE("Ice", stack -> {
         Item packedIce = Items.PACKED_ICE;
         if (!stack.is(packedIce)) {
            WorldStateManager trackerSnapshot = ClientContext.worldStateManager;
            DimensionType dimension = trackerSnapshot.getDimension();
            if (!dimension.isNether()) {
               Item ice = Items.ICE;
               if (stack.is(ice)) {
                  return true;
               }
            }

            return false;
         } else {
            return true;
         }
      }),
      ANVIL("Anvil", stack -> {
         Item anvil = Items.ANVIL;
         return stack.is(anvil);
      });

      private final String displayName;
      private final Predicate<net.minecraft.world.item.ItemStack> itemPredicate;

      @Override
      public String getName() {
         return this.displayName;
      }

      ModeChoice(String displayName, Predicate<net.minecraft.world.item.ItemStack> itemPredicate) {
         this.displayName = displayName;
         this.itemPredicate = itemPredicate;
      }
   }
}
