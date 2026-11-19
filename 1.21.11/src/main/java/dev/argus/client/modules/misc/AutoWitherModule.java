package dev.argus.client.modules.misc;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.FeatureBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.settings.options.AutoWitherPriorityChoice;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.world.BlockInteractionUtils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Builds withers from soul sand and wither skeleton skulls in the hotbar, ported from Meteor's AutoWither.
 * The T faces the way you are looking: four soul sand blocks first, then the three skulls on top.
 */
public final class AutoWitherModule extends Module {
   private static final Predicate<ItemStack> BODY = stack -> stack.is(Items.SOUL_SAND) || stack.is(Items.SOUL_SOIL);
   private static final Predicate<ItemStack> HEAD = stack -> stack.is(Items.WITHER_SKELETON_SKULL);

   private final Setting<Integer> horizontalRadiusSetting;
   private final Setting<Integer> verticalRadiusSetting;
   private final Setting<AutoWitherPriorityChoice> prioritySetting;
   private final Setting<Integer> witherDelaySetting;
   private final Setting<Integer> blockDelaySetting;
   private final Setting<Boolean> rotateSetting;
   private final Setting<Boolean> turnOffSetting;
   private final Setting<Boolean> renderSetting;
   private final Setting<Color> fillSetting;
   private final Setting<Color> outlineSetting;
   private final Setting<Float> lineWidthSetting;

   private final ElapsedTimer witherTimer = new ElapsedTimer();
   private final ElapsedTimer blockTimer = new ElapsedTimer();
   private Build build;
   private ClientLevel world;
   private String status;

   /** One wither: soul sand at {@code foot}, arms along {@code arm}, and the next step to place. */
   private static final class Build {
      final List<Step> steps;
      int next;

      Build(List<Step> steps) {
         this.steps = steps;
      }
   }

   /** A block to place and the direction from it to the block it is placed against. */
   private record Step(BlockPos pos, Direction support, boolean head) {
   }

   public AutoWitherModule() {
      super("AutoWither", "Builds withers from soul sand and wither skeleton skulls in your hotbar.", ModuleCategory.MISC, "WitherBuilder");
      this.horizontalRadiusSetting = this.add(new NumberSetting<>("HorizontalRadius", 4, 1, 6).withUnit("m"));
      this.verticalRadiusSetting = this.add(new NumberSetting<>("VerticalRadius", 3, 1, 6).withUnit("m"));
      this.prioritySetting = this.add(new EnumSetting<>("Priority", AutoWitherPriorityChoice.CLOSEST));
      this.witherDelaySetting = this.add(new NumberSetting<>("WitherDelay", 50, 0, 5000).withUnit("ms"));
      this.blockDelaySetting = this.add(new NumberSetting<>("BlockDelay", 0, 0, 1000).withUnit("ms"));
      this.rotateSetting = this.add(new BooleanSetting("Rotate", false));
      this.turnOffSetting = this.add(new BooleanSetting("TurnOff", true));
      this.renderSetting = this.add(new BooleanSetting("Render", true).markVisibilityDependency());
      this.fillSetting = this.add(new ColorSetting("Fill", new Color(0x30C77DFF, true)).dependsOn(this.renderSetting));
      this.outlineSetting = this.add(new ColorSetting("Outline", new Color(0xA0C77DFF, true)).dependsOn(this.renderSetting));
      this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 3.0F).dependsOn(this.renderSetting));
      FeatureBoxRenderer.RenderConfiguration boxConfiguration = new FeatureBoxRenderer.Builder(this, this.fillSetting, this.outlineSetting)
         .withLineWidth(this.lineWidthSetting)
         .build();
      ClientContext.featureBoxRenderer.register(boxConfiguration);
   }

   @Override
   public String getInfo() {
      return this.status;
   }

   @Override
   public void onEnable() {
      this.reset();
   }

   @Override
   public void onDisable() {
      this.reset();
   }

   private void reset() {
      this.build = null;
      this.world = null;
      this.status = null;
      // The first wither starts straight away.
      this.witherTimer.setTime(0L);
   }

   @EventHandler
   private void onRotationRequest(RotationRequestEvent event) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      if (player == null || level == null || ClientAccess.argusclientMinecraft.gameMode == null) {
         return;
      }

      if (this.world != level) {
         this.reset();
         this.world = level;
      }

      if (!player.isAlive()) {
         return;
      }

      if (this.build == null) {
         if (!this.witherTimer.hasElapsed(this.witherDelaySetting.getValue().longValue())) {
            return;
         }

         if (count(player.getInventory(), BODY) < 4 || count(player.getInventory(), HEAD) < 3) {
            this.status = "No materials";
            if (this.turnOffSetting.getValue()) {
               this.disable();
            }

            return;
         }

         this.build = this.findBuild(player, level);
         if (this.build == null) {
            this.status = "No space";
            return;
         }

         this.blockTimer.setTime(0L);
      }

      this.status = "Building";
      this.placeSteps(player, level);
      if (this.build != null && this.build.next >= this.build.steps.size()) {
         this.build = null;
         this.witherTimer.reset();
         this.status = "Built";
         if (this.turnOffSetting.getValue()) {
            this.disable();
         }
      }
   }

   /** Places as many steps as the block delay allows this tick; a delay of 0 places the whole wither at once. */
   private void placeSteps(LocalPlayer player, ClientLevel level) {
      long blockDelay = this.blockDelaySetting.getValue().longValue();
      while (this.build.next < this.build.steps.size()) {
         if (!this.blockTimer.hasElapsed(blockDelay)) {
            return;
         }

         Step step = this.build.steps.get(this.build.next);
         BlockState current = level.getBlockState(step.pos());
         if (step.head() ? current.is(Blocks.WITHER_SKELETON_SKULL) : current.is(Blocks.SOUL_SAND) || current.is(Blocks.SOUL_SOIL)) {
            this.build.next++;
            continue;
         }

         InventoryUtils.SwapSlot slot = InventoryUtils.findSwapSlot(step.head() ? HEAD : BODY, false);
         if (!slot.isFound()) {
            this.abandon("No materials");
            return;
         }

         if (!this.place(player, step, slot)) {
            this.abandon("Blocked");
            return;
         }

         this.build.next++;
         this.blockTimer.reset();
         if (blockDelay > 0L) {
            return;
         }
      }
   }

   private void abandon(String reason) {
      this.build = null;
      this.status = reason;
      this.witherTimer.reset();
   }

   private boolean place(LocalPlayer player, Step step, InventoryUtils.SwapSlot slot) {
      int previousSlot = player.getInventory().getSelectedSlot();
      InteractionHand hand = slot.hand();
      if (this.rotateSetting.getValue()) {
         float[] rotations = RotationManager.getRotationsToFace(Vec3.atCenterOf(step.pos()), step.support());
         ClientContext.rotationManager.requestRotation(rotations, 100);
      }

      InventoryUtils.swapTo(slot);
      boolean placed;
      try {
         placed = BlockInteractionUtils.placeBlock(step.pos(), null, step.support(), false, hand);
      } finally {
         InventoryUtils.swapBack(previousSlot, slot);
      }

      if (placed && this.renderSetting.getValue()) {
         ClientContext.featureBoxRenderer.addBox(this, step.pos());
      }

      return placed;
   }

   private Build findBuild(LocalPlayer player, ClientLevel level) {
      Direction facing = player.getDirection();
      Direction arm = facing.getClockWise();
      int horizontal = this.horizontalRadiusSetting.getValue();
      int vertical = this.verticalRadiusSetting.getValue();
      BlockPos origin = player.blockPosition();
      Vec3 eye = player.getEyePosition();
      List<BlockPos> candidates = new ArrayList<>();
      for (BlockPos mutable : BlockPos.betweenClosed(origin.offset(-horizontal, -vertical, -horizontal), origin.offset(horizontal, vertical, horizontal))) {
         if (this.validSpawn(player, level, mutable, arm)) {
            candidates.add(mutable.immutable());
         }
      }

      if (candidates.isEmpty()) {
         return null;
      }

      switch (this.prioritySetting.getValue()) {
         case FURTHEST -> candidates.sort(Comparator.comparingDouble((BlockPos pos) -> Vec3.atCenterOf(pos).distanceToSqr(eye)).reversed());
         case RANDOM -> Collections.shuffle(candidates);
         default -> candidates.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(eye)));
      }

      for (BlockPos foot : candidates) {
         Direction footSupport = BlockInteractionUtils.findSupportDirection(foot, BlockInteractionUtils.isStrictDirectionEnabled(), false);
         if (footSupport != null && footSupport != Direction.UP) {
            return new Build(steps(foot, footSupport, arm));
         }
      }

      return null;
   }

   /** Soul sand T first (foot, body, both arms), then the three skulls, each placed against a block already in the plan. */
   static List<Step> steps(BlockPos foot, Direction footSupport, Direction arm) {
      BlockPos body = foot.above();
      BlockPos head = body.above();
      return List.of(
         new Step(foot, footSupport, false),
         new Step(body, Direction.DOWN, false),
         new Step(body.relative(arm), arm.getOpposite(), false),
         new Step(body.relative(arm.getOpposite()), arm, false),
         new Step(head, Direction.DOWN, true),
         new Step(head.relative(arm), Direction.DOWN, true),
         new Step(head.relative(arm.getOpposite()), Direction.DOWN, true)
      );
   }

   /** The whole 3x3 frame must be clear: the build spots replaceable and the two bottom corners air, as vanilla's pattern requires. */
   private boolean validSpawn(LocalPlayer player, ClientLevel level, BlockPos foot, Direction arm) {
      if (level.isOutsideBuildHeight(foot) || level.isOutsideBuildHeight(foot.above(2))) {
         return false;
      }

      AABB playerBox = player.getBoundingBox();
      for (int up = 0; up <= 2; up++) {
         for (int side = -1; side <= 1; side++) {
            BlockPos pos = foot.above(up).relative(arm, side);
            if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) || !level.getWorldBorder().isWithinBounds(pos)) {
               return false;
            }

            BlockState state = level.getBlockState(pos);
            boolean corner = up == 0 && side != 0;
            if (corner ? !state.isAir() : !state.canBeReplaced() || !state.getFluidState().isEmpty()) {
               return false;
            }

            if (!corner && (new AABB(pos).intersects(playerBox) || !BlockInteractionUtils.canPlaceBlock(pos, Blocks.SOUL_SAND, false))) {
               return false;
            }
         }
      }

      return true;
   }

   private static int count(Inventory inventory, Predicate<ItemStack> predicate) {
      int total = predicate.test(inventory.getItem(Inventory.SLOT_OFFHAND)) ? inventory.getItem(Inventory.SLOT_OFFHAND).getCount() : 0;
      for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (predicate.test(stack)) {
            total += stack.getCount();
         }
      }

      return total;
   }
}
