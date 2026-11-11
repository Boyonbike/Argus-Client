package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.FeatureBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.BlockSampleMode;
import dev.argus.client.settings.options.ListFilterMode;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.world.BlockInteractionUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

public final class SourceFillModule extends Module {
   private final Setting<ListFilterMode> selectionSetting;
   private final Setting<Set<Block>> blocksSetting;
   private final Setting<Float> rangeSetting;
   private final Setting<Float> wallRangeSetting;
   private final Setting<Integer> delaySetting;
   private final Setting<Integer> blocksPerTickSetting;
   private final Setting<Boolean> airPlaceSetting;
   private final Setting<Boolean> rotateSetting;
   private final Setting<Boolean> sourcesSetting;
   private final Setting<Boolean> waterSetting;
   private final Setting<Boolean> lavaSetting;
   private final Setting<Boolean> renderSetting;
   private final Setting<Color> fillSetting;
   private final Setting<Color> outlineSetting;
   private final Setting<Float> lineWidthSetting;
   private final Map<BlockPos, Long> spongeExpiryByPosition;
   private final ElapsedTimer placementTimer;

   private boolean isOutsideSpongeRadius(BlockPos position) {
      Iterator<BlockPos> spongeIterator = this.spongeExpiryByPosition.keySet().iterator();

      while (spongeIterator.hasNext()) {
         Vec3 spongeCenter = spongeIterator.next().getCenter();
         Vec3 targetCenter = position.getCenter();
         if (spongeCenter.distanceTo(targetCenter) <= 4.0) {
            return false;
         }
      }

      return true;
   }

   private boolean isWithinPlacementReach(BlockPos position) {
      BlockSampleMode sampleMode = BlockSampleMode.X4;
      if (WorldUtils.canSeeAny(sampleMode.sampleBlock(position))) {
         return true;
      }

      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Vec3 eyePosition = player.getEyePosition();
      Vec3 blockCenter = position.getCenter();
      double distance = eyePosition.distanceTo(blockCenter);
      Float wallRangeValue = this.wallRangeSetting.getValue();
      return distance <= wallRangeValue.floatValue();
   }

   private List<BlockPos> collectTargetPositions() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Vec3 eyePosition = player.getEyePosition();
      float radius = this.rangeSetting.getValue();
      return WorldUtils.streamSphereBlocks(eyePosition, radius)
         .filter(fluidPosition -> ClientAccess.argusclientMinecraft.level.isFluidAtPosition(fluidPosition, this::isSelectedFluid))
         .filter(position -> BlockInteractionUtils.canPlaceAt(position, true))
         .filter(this::isWithinPlacementReach)
         .toList();
   }

   private boolean isSelectedFluid(FluidState fluidState) {
      FlowingFluid lava = Fluids.LAVA;
      if (fluidState.is(lava) && this.lavaSetting.getValue()) {
         return true;
      }

      FlowingFluid water = Fluids.WATER;
      return fluidState.is(water) && this.waterSetting.getValue();
   }

   private boolean isSelectedBlockStack(ItemStack stack) {
      if (stack.getItem() instanceof BlockItem blockItem) {
         ListFilterMode selectionMode = this.selectionSetting.getValue();
         Block block = blockItem.getBlock();
         Setting<Set<Block>> blocks = this.blocksSetting;
         return ListFilterMode.matches(selectionMode, block, blocks);
      } else {
         return false;
      }
   }

   private boolean placeAt(BlockPos position, InventoryUtils.SwapSlot slot) {
      Direction supportDirection = BlockInteractionUtils.findSupportDirection(position);
      Boolean airPlaceEnabled = this.airPlaceSetting.getValue();
      if (!airPlaceEnabled && supportDirection == null) {
         return false;
      }

      InteractionHand hand = slot.hand();
      ItemStack placementStack = slot.getStack();
      Item sponge = Items.SPONGE;
      if (placementStack.is(sponge)) {
         Map<BlockPos, Long> spongeExpiries = this.spongeExpiryByPosition;
         long expiresAt = System.currentTimeMillis() + 2500L;
         spongeExpiries.put(position, expiresAt);
      }

      boolean airPlaceFlag = this.airPlaceSetting.getValue();
      BlockInteractionUtils.placeBlockIgnoringCrystals(position, supportDirection, airPlaceFlag, hand);
      if (this.rotateSetting.getValue()) {
         Vec3 rotationCenter = position.getCenter();
         float[] rotations = RotationManager.getRotationsToFace(rotationCenter, supportDirection);
         RotationManager rotationManager = ClientContext.rotationManager;
         rotationManager.requestRotation(rotations, 100);
      }

      if (this.renderSetting.getValue()) {
         ClientContext.featureBoxRenderer.addBox(this, position);
      }

      return true;
   }

   @EventHandler
   private void onRotationRequest(RotationRequestEvent event) {
      ElapsedTimer timer = this.placementTimer;
      long delayMillis = this.delaySetting.getValue().intValue();
      if (timer.hasElapsed(delayMillis)) {
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         int previousSlot = player.getInventory().getSelectedSlot();
         Predicate<ItemStack> placementPredicate = this::isSelectedBlockStack;
         InventoryUtils.SwapSlot placementSlot = InventoryUtils.findSwapSlot(placementPredicate, false);
         if (placementSlot.isFound()) {
            this.spongeExpiryByPosition.entrySet().removeIf(spongeEntry -> {
               long currentTime = System.currentTimeMillis();
               Long expiresAt = spongeEntry.getValue();
               return currentTime > expiresAt;
            });
            List<BlockPos> targets = this.collectTargetPositions();
            if (!targets.isEmpty()) {
               int placedCount = 0;
               InventoryUtils.swapTo(placementSlot);

               for (BlockPos targetPosition : targets) {
                  Integer placementLimit = this.blocksPerTickSetting.getValue();
                  if (placedCount >= placementLimit) {
                     break;
                  }

                  if (!this.isOutsideSpongeRadius(targetPosition)) {
                     ItemStack placementStack = placementSlot.getStack();
                     Item sponge = Items.SPONGE;
                     if (placementStack.is(sponge)) {
                        continue;
                     }
                  }

                  if (this.placeAt(targetPosition, placementSlot)) {
                     timer = this.placementTimer;
                     timer.reset();
                     placedCount++;
                  }
               }

               InventoryUtils.swapBack(previousSlot, placementSlot);
            }
         }
      }
   }

   public static void bindModuleReferences(ModuleRegistry registry) {
   }

   public SourceFillModule() {
      super("SourceFill", "Replaces liquid blocks with solid blocks.", ModuleCategory.PLAYER);
      ListFilterMode defaultSelection = ListFilterMode.ANY;
      this.selectionSetting = this.add(new EnumSetting<>("Selection", defaultSelection));
      DefaultedRegistry blockRegistry = BuiltInRegistries.BLOCK;
      Block[] defaultBlocks = new Block[3];
      Block netherPortal = Blocks.NETHER_PORTAL;
      defaultBlocks[0] = netherPortal;
      Block enderChest = Blocks.ENDER_CHEST;
      defaultBlocks[1] = enderChest;
      Block endPortal = Blocks.END_PORTAL;
      defaultBlocks[2] = endPortal;
      RegistrySetSetting<Block> blocksOption = new RegistrySetSetting<>("Blocks", blockRegistry, defaultBlocks);
      this.blocksSetting = this.add(blocksOption);
      this.rangeSetting = this.add(new NumberSetting<>("Range", 4.5F, 1.0F, 6.0F).withUnit("m"));
      this.wallRangeSetting = this.add(new NumberSetting<>("WallRange", 4.5F, 0.0F, 6.0F).withUnit("m"));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 50, 0, 250).withUnit("ms"));
      this.blocksPerTickSetting = this.add(new NumberSetting<>("BPT", 8, 1, 8));
      this.airPlaceSetting = this.add(new BooleanSetting("AirPlace", false));
      this.rotateSetting = this.add(new BooleanSetting("Rotate", true));
      this.sourcesSetting = this.add(new BooleanSetting("Sources", false).markAsGroup().markVisibilityDependency());
      this.waterSetting = this.add(new BooleanSetting("Water", true).dependsOn(this.sourcesSetting));
      this.lavaSetting = this.add(new BooleanSetting("Lava", true).dependsOn(this.sourcesSetting));
      this.renderSetting = this.add(new BooleanSetting("Render", true).markVisibilityDependency());
      Color defaultFill = new Color(146643455, true);
      this.fillSetting = this.add(new ColorSetting("Fill", defaultFill).dependsOn(this.renderSetting));
      Color defaultOutline = new Color(1539152383, true);
      this.outlineSetting = this.add(new ColorSetting("Outline", defaultOutline).dependsOn(this.renderSetting));
      this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 3.0F).dependsOn(this.renderSetting));
      this.spongeExpiryByPosition = new HashMap<>();
      this.placementTimer = new ElapsedTimer();
      FeatureBoxRenderer boxRenderer = ClientContext.featureBoxRenderer;
      FeatureBoxRenderer.Builder boxBuilder = new FeatureBoxRenderer.Builder(this, this.fillSetting, this.outlineSetting).withLineWidth(this.lineWidthSetting);
      FeatureBoxRenderer.RenderConfiguration boxConfiguration = boxBuilder.build();
      boxRenderer.register(boxConfiguration);
      Setting<Set<Block>> blocksVisibilitySetting = this.blocksSetting;
      Predicate<Object> blocksVisibilityPredicate = selectedBlocks -> this.selectionSetting.getValue().isFiltering();
      blocksVisibilitySetting.addVisibilityPredicate(blocksVisibilityPredicate);
   }
}
