/*
 * Adapted from Trouser-Streak's RedstoneNuker by etianl and Trouser-Streak contributors,
 * which is itself a modified copy of Meteor Client's Nuker, Copyright (c) Meteor Development.
 * Upstream: https://github.com/etianl/Trouser-Streak
 * Revision: b2455f6c23cdc0cb3f375584c3d150b5e775d457
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Ported to Argus Client by Boyonabike on 2026-11-12: Argus settings, tick and
 * world-render events, box renderer and hotbar selection replace their Meteor
 * counterparts; the Cube shape is laid out from the player's facing; the tool
 * pick runs before each of the module's own breaks instead of on every manual
 * break, and switch-back happens once nothing is left to break. It leaves
 * Block Coat's coating block and hotbar slot alone while Block Coat is on.
 * Distributed under GNU GPL v3; see licenses/GPL-3.0.txt and THIRD_PARTY_NOTICES.md.
 * This program is distributed WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package dev.argus.client.modules.player;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.FontRenderer;
import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.inventory.EnchantmentUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Breaks redstone components around the player, so TNT you place can't be set off by a trap.
 */
public class RedstoneNukerModule extends Module {
   private static final int FADE_TICKS = 8;

   private final Setting<Shape> shapeSetting = this.add(new EnumSetting<>("Shape", Shape.SPHERE));
   private final Setting<Mode> modeSetting = this.add(new EnumSetting<>("Mode", Mode.ALL));
   private final Setting<Float> rangeSetting;
   private final Setting<Integer> upSetting;
   private final Setting<Integer> downSetting;
   private final Setting<Integer> leftSetting;
   private final Setting<Integer> rightSetting;
   private final Setting<Integer> forwardSetting;
   private final Setting<Integer> backSetting;
   private final Setting<Integer> delaySetting = this.add(new NumberSetting<>("Delay", 0, 0, 20).withUnit("t"));
   private final Setting<Integer> bptSetting = this.add(new NumberSetting<>("BPT", 1, 1, 6));
   private final Setting<Sort> sortSetting = this.add(new EnumSetting<>("Sort", Sort.CLOSEST));
   private final Setting<Boolean> swingSetting = this.add(new BooleanSetting("Swing", true));
   private final Setting<Set<Block>> blocksSetting = this.add(new RegistrySetSetting<>("Blocks", BuiltInRegistries.BLOCK, defaultBlocks()));

   private final Setting<Boolean> renderBoundsSetting = this.add(new BooleanSetting("RenderBounds", true));
   private final Setting<RenderMode> boundsModeSetting;
   private final Setting<Color> boundsSideSetting;
   private final Setting<Color> boundsLineSetting;
   private final Setting<Boolean> renderBrokenSetting = this.add(new BooleanSetting("RenderBroken", true));
   private final Setting<RenderMode> brokenModeSetting;
   private final Setting<Color> brokenSideSetting;
   private final Setting<Color> brokenLineSetting;

   private final Setting<Boolean> autoToolSetting = this.add(new BooleanSetting("AutoTool", true));
   private final Setting<Prefer> preferSetting;
   private final Setting<Boolean> antiBreakSetting;
   private final Setting<Integer> antiBreakPercentSetting;
   private final Setting<Boolean> switchBackSetting;

   private final List<BlockPos> targets = new ArrayList<>();
   private final List<BrokenBlock> brokenBlocks = new ArrayList<>();
   private BlockPos lastBlockPos;
   private boolean firstBlock;
   private int timer;
   private int noBlockTimer;
   private int previousSlot = -1;
   private int toolSlot = -1;
   private BlockCoatModule blockCoat;
   private AABB cubeBounds;

   public RedstoneNukerModule() {
      super("RedstoneNuker", "Breaks redstone around you so TNT you place can't be set off by a trap.", ModuleCategory.PLAYER);
      Predicate<Object> notCube = ignored -> this.shapeSetting.getValue() != Shape.CUBE;
      Predicate<Object> cube = ignored -> this.shapeSetting.getValue() == Shape.CUBE;
      this.rangeSetting = this.add(new NumberSetting<>("Range", 4.0F, 0.0F, 6.0F, notCube).withUnit("m"), this.modeSetting);
      this.upSetting = this.add(new NumberSetting<>("Up", 1, 0, 6, cube), this.rangeSetting);
      this.downSetting = this.add(new NumberSetting<>("Down", 1, 0, 6, cube), this.upSetting);
      this.leftSetting = this.add(new NumberSetting<>("Left", 1, 0, 6, cube), this.downSetting);
      this.rightSetting = this.add(new NumberSetting<>("Right", 1, 0, 6, cube), this.leftSetting);
      this.forwardSetting = this.add(new NumberSetting<>("Forward", 1, 0, 6, cube), this.rightSetting);
      this.backSetting = this.add(new NumberSetting<>("Back", 1, 0, 6, cube), this.forwardSetting);

      Predicate<Object> bounds = ignored -> this.renderBoundsSetting.getValue();
      this.boundsModeSetting = this.add(new EnumSetting<>("BoundsMode", RenderMode.LINES, bounds), this.renderBoundsSetting);
      this.boundsSideSetting = this.add(new ColorSetting("BoundsSide", new Color(16, 106, 144, 100), bounds), this.boundsModeSetting);
      this.boundsLineSetting = this.add(new ColorSetting("BoundsLine", new Color(16, 106, 144, 255), bounds), this.boundsSideSetting);
      Predicate<Object> broken = ignored -> this.renderBrokenSetting.getValue();
      this.brokenModeSetting = this.add(new EnumSetting<>("BrokenMode", RenderMode.BOTH, broken), this.renderBrokenSetting);
      this.brokenSideSetting = this.add(new ColorSetting("BrokenSide", new Color(255, 0, 0, 10), broken), this.brokenModeSetting);
      this.brokenLineSetting = this.add(new ColorSetting("BrokenLine", new Color(255, 0, 0, 255), broken), this.brokenSideSetting);

      Predicate<Object> autoTool = ignored -> this.autoToolSetting.getValue();
      this.preferSetting = this.add(new EnumSetting<>("Prefer", Prefer.NONE, autoTool));
      this.antiBreakSetting = this.add(new BooleanSetting("AntiBreak", false, autoTool));
      this.antiBreakPercentSetting = this.add(
         new NumberSetting<>("AntiBreakPercent", 10, 1, 100, ignored -> this.autoToolSetting.getValue() && this.antiBreakSetting.getValue()).withUnit("%")
      );
      this.switchBackSetting = this.add(new BooleanSetting("SwitchBack", false, autoTool));
   }

   private static Block[] defaultBlocks() {
      Stream<Block> fixed = Stream.of(
         Blocks.REDSTONE_BLOCK, Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH, Blocks.DAYLIGHT_DETECTOR, Blocks.REDSTONE_WIRE,
         Blocks.COMPARATOR, Blocks.REPEATER, Blocks.DETECTOR_RAIL, Blocks.LEVER, Blocks.SCULK_SENSOR, Blocks.FIRE, Blocks.OBSERVER,
         Blocks.TRIPWIRE_HOOK, Blocks.TRIPWIRE
      );
      Stream<Block> plates = BuiltInRegistries.BLOCK.stream().filter(block -> block instanceof BasePressurePlateBlock);
      return Stream.concat(fixed, plates).distinct().toArray(Block[]::new);
   }

   @Override
   public String getInfo() {
      return FontRenderer.capitalize(this.shapeSetting.getValue());
   }

   @Override
   public void onEnable() {
      this.firstBlock = true;
      this.brokenBlocks.clear();
      this.targets.clear();
      this.lastBlockPos = null;
      this.timer = 0;
      this.noBlockTimer = 0;
      this.previousSlot = -1;
      this.toolSlot = -1;
   }

   @Override
   public void onDisable() {
      this.brokenBlocks.clear();
      this.targets.clear();
      this.restoreSlot();
   }

   @EventHandler
   private void onTick(LocalPlayerPreTickEvent event) {
      if (this.isWorldUnavailable()) {
         return;
      }

      this.brokenBlocks.removeIf(BrokenBlock::tick);
      if (this.timer > 0) {
         this.timer--;
         return;
      }

      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      Vec3 feet = player.position();
      this.collectTargets(player, feet);
      if (this.targets.isEmpty()) {
         if (this.noBlockTimer++ >= this.delaySetting.getValue()) {
            this.firstBlock = true;
         }

         this.restoreSlot();
         return;
      }

      this.noBlockTimer = 0;
      BlockPos first = this.targets.getFirst();
      if (!this.firstBlock && !first.equals(this.lastBlockPos)) {
         this.timer = this.delaySetting.getValue();
         this.lastBlockPos = first;
         if (this.timer > 0) {
            return;
         }
      }

      int broken = 0;
      boolean coatHoldsHotbar = this.blockCoatHoldsHotbar();
      for (BlockPos pos : this.targets) {
         if (broken >= this.bptSetting.getValue()) {
            break;
         }

         // While Block Coat holds the hotbar, a block that takes more than one hit would lose its progress the
         // next time Block Coat switches slots, so only break what the held item breaks at once.
         if (coatHoldsHotbar && !this.breaksInstantly(player, pos)) {
            continue;
         }

         if (!this.breakBlock(player, pos, !coatHoldsHotbar)) {
            break;
         }

         this.brokenBlocks.add(new BrokenBlock(pos));
         this.lastBlockPos = pos;
         broken++;
         if (!this.breaksInstantly(player, pos)) {
            break;
         }
      }

      this.firstBlock = false;
      this.targets.clear();
   }

   private void collectTargets(LocalPlayer player, Vec3 feet) {
      this.targets.clear();
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      Shape shape = this.shapeSetting.getValue();
      double range = this.rangeSetting.getValue();
      int radius = (int) Math.ceil(range) + 1;
      BlockPos feetBlock = player.blockPosition();
      int minX = feetBlock.getX() - radius;
      int maxX = feetBlock.getX() + radius;
      int minY = feetBlock.getY() - radius;
      int maxY = feetBlock.getY() + radius;
      int minZ = feetBlock.getZ() - radius;
      int maxZ = feetBlock.getZ() + radius;
      if (shape == Shape.CUBE) {
         this.cubeBounds = this.cubeBounds(player, feetBlock);
         minX = (int) this.cubeBounds.minX;
         maxX = (int) this.cubeBounds.maxX - 1;
         minY = (int) this.cubeBounds.minY;
         maxY = (int) this.cubeBounds.maxY - 1;
         minZ = (int) this.cubeBounds.minZ;
         maxZ = (int) this.cubeBounds.maxZ - 1;
      }

      int uniformReach = (int) Math.round(range) - 1;
      Mode mode = this.modeSetting.getValue();
      Set<Block> whitelist = this.blocksSetting.getValue();
      Block coating = this.blockCoat().coatingBlock();
      BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               cursor.set(x, y, z);
               boolean inShape = switch (shape) {
                  case SPHERE -> Vec3.atCenterOf(cursor).distanceToSqr(feet) <= range * range;
                  case UNIFORM_CUBE -> Math.abs(x - feetBlock.getX()) <= uniformReach
                     && Math.abs(y - feetBlock.getY()) <= uniformReach
                     && Math.abs(z - feetBlock.getZ()) <= uniformReach;
                  case CUBE -> true;
               };
               if (!inShape) {
                  continue;
               }

               BlockState state = level.getBlockState(cursor);
               // Never break what Block Coat is placing, or the two undo each other.
               if (!whitelist.contains(state.getBlock()) || state.is(coating) || !WorldUtils.canMineBlock(cursor)) {
                  continue;
               }

               if (mode == Mode.FLATTEN && y < feetBlock.getY()) {
                  continue;
               }

               if (mode == Mode.SMASH && state.getDestroySpeed(level, cursor) != 0.0F) {
                  continue;
               }

               this.targets.add(cursor.immutable());
            }
         }
      }

      switch (this.sortSetting.getValue()) {
         case CLOSEST -> this.targets.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(feet)));
         case FURTHEST -> this.targets.sort(Comparator.comparingDouble(pos -> -Vec3.atCenterOf(pos).distanceToSqr(feet)));
         case TOP_DOWN -> this.targets.sort(Comparator.comparingInt(pos -> -pos.getY()));
         case NONE -> {
         }
      }
   }

   /** The Cube shape's block bounds, laid out from the way the player is facing. */
   private AABB cubeBounds(LocalPlayer player, BlockPos feetBlock) {
      Direction forward = player.getDirection();
      Direction right = forward.getClockWise();
      int fwd = this.forwardSetting.getValue();
      int back = this.backSetting.getValue();
      int rgt = this.rightSetting.getValue();
      int lft = this.leftSetting.getValue();
      BlockPos a = feetBlock.relative(forward, fwd).relative(right, rgt).above(this.upSetting.getValue());
      BlockPos b = feetBlock.relative(forward, -back).relative(right, -lft).below(this.downSetting.getValue());
      int minY = this.modeSetting.getValue() == Mode.FLATTEN ? feetBlock.getY() : Math.min(a.getY(), b.getY());
      return new AABB(
         Math.min(a.getX(), b.getX()), minY, Math.min(a.getZ(), b.getZ()),
         Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1
      );
   }

   private BlockCoatModule blockCoat() {
      if (this.blockCoat == null) {
         this.blockCoat = ClientContext.getModule(BlockCoatModule.class);
      }

      return this.blockCoat;
   }

   private boolean blockCoatHoldsHotbar() {
      return this.blockCoat().holdsHotbar();
   }

   private boolean breaksInstantly(LocalPlayer player, BlockPos pos) {
      BlockState state = ClientAccess.argusclientMinecraft.level.getBlockState(pos);
      return player.isCreative() || WorldUtils.getBreakProgress(player.getMainHandItem(), state, player.onGround()) >= 1.0;
   }

   /** Picks a tool if wanted and allowed, and hits the block; false when Anti Break stops it. */
   private boolean breakBlock(LocalPlayer player, BlockPos pos, boolean maySwitch) {
      if (this.autoToolSetting.getValue()) {
         if (maySwitch) {
            this.selectTool(player, ClientAccess.argusclientMinecraft.level.getBlockState(pos));
         }

         ItemStack held = player.getMainHandItem();
         if (isTool(held) && this.isWornOut(held)) {
            return false;
         }
      }

      if (!ClientAccess.argusclientMinecraft.gameMode.continueDestroyBlock(pos, Direction.UP)) {
         return false;
      }

      if (this.swingSetting.getValue()) {
         player.swing(InteractionHand.MAIN_HAND);
      }

      return true;
   }

   private void selectTool(LocalPlayer player, BlockState state) {
      ItemStack current = player.getMainHandItem();
      int bestSlot = -1;
      double bestScore = -1.0;
      for (int slot = 0; slot < 9; slot++) {
         double score = this.score(player.getInventory().getItem(slot), state);
         if (score > bestScore) {
            bestScore = score;
            bestSlot = slot;
         }
      }

      if (bestSlot == -1 || bestSlot == InventoryUtils.getSelectedSlot()) {
         return;
      }

      if (bestScore > this.score(current, state) || this.isWornOut(current) || !isTool(current)) {
         if (this.previousSlot == -1) {
            this.previousSlot = InventoryUtils.getSelectedSlot();
         }

         InventoryUtils.selectHotbarSlotForPlacement(bestSlot);
         this.toolSlot = bestSlot;
      }
   }

   private double score(ItemStack stack, BlockState state) {
      if (!isTool(stack) || this.isWornOut(stack)) {
         return -1.0;
      }

      double score = stack.getDestroySpeed(state) * 1000.0;
      score += EnchantmentUtils.getLevel(Enchantments.UNBREAKING, stack);
      score += EnchantmentUtils.getLevel(Enchantments.EFFICIENCY, stack);
      score += EnchantmentUtils.getLevel(Enchantments.MENDING, stack);
      switch (this.preferSetting.getValue()) {
         case FORTUNE -> score += EnchantmentUtils.getLevel(Enchantments.FORTUNE, stack);
         case SILK_TOUCH -> score += EnchantmentUtils.getLevel(Enchantments.SILK_TOUCH, stack);
         case NONE -> {
         }
      }

      return score;
   }

   private boolean isWornOut(ItemStack stack) {
      return this.antiBreakSetting.getValue()
         && stack.isDamageableItem()
         && stack.getMaxDamage() - stack.getDamageValue() < stack.getMaxDamage() * this.antiBreakPercentSetting.getValue() / 100;
   }

   private static boolean isTool(ItemStack stack) {
      return stack.is(ItemTags.AXES) || stack.is(ItemTags.HOES) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS)
         || stack.getItem() instanceof ShearsItem;
   }

   /** Switches back only if the tool this module picked is still selected, so it never undoes another module's switch. */
   private void restoreSlot() {
      if (this.previousSlot != -1 && this.switchBackSetting.getValue() && !this.isWorldUnavailable()
         && InventoryUtils.getSelectedSlot() == this.toolSlot) {
         InventoryUtils.selectHotbarSlotForPlacement(this.previousSlot);
      }

      this.previousSlot = -1;
      this.toolSlot = -1;
   }

   @EventHandler
   private void onRender(WorldRenderEvent.Main event) {
      if (this.isWorldUnavailable()) {
         return;
      }

      PoseStack poses = event.getPoseStack();
      if (this.renderBrokenSetting.getValue()) {
         RenderMode mode = this.brokenModeSetting.getValue();
         for (BrokenBlock block : this.brokenBlocks) {
            float fade = (float) block.ticks / FADE_TICKS;
            draw(poses, new AABB(block.pos), fade(this.brokenSideSetting.getValue(), fade), fade(this.brokenLineSetting.getValue(), fade), mode);
         }
      }

      if (this.renderBoundsSetting.getValue() && this.shapeSetting.getValue() == Shape.CUBE && this.cubeBounds != null) {
         draw(poses, this.cubeBounds, this.boundsSideSetting.getValue(), this.boundsLineSetting.getValue(), this.boundsModeSetting.getValue());
      }
   }

   private static void draw(PoseStack poses, AABB box, Color side, Color line, RenderMode mode) {
      if (mode != RenderMode.LINES) {
         WorldBoxRenderer.fillBox(poses, box, side);
      }

      if (mode != RenderMode.SIDES) {
         WorldBoxRenderer.outlineBox(poses, box, line, 1.0F);
      }
   }

   private static Color fade(Color color, float factor) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * factor));
   }

   private static final class BrokenBlock {
      final BlockPos pos;
      int ticks = FADE_TICKS;

      BrokenBlock(BlockPos pos) {
         this.pos = pos;
      }

      /** Counts down a tick; true once the fade has finished. */
      boolean tick() {
         return --this.ticks <= 0;
      }
   }

   enum Shape implements NamedEntry {
      CUBE("Cube"),
      UNIFORM_CUBE("UniformCube"),
      SPHERE("Sphere");

      private final String displayName;

      Shape(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }

   enum Mode implements NamedEntry {
      ALL("All"),
      FLATTEN("Flatten"),
      SMASH("Smash");

      private final String displayName;

      Mode(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }

   enum Sort implements NamedEntry {
      NONE("None"),
      CLOSEST("Closest"),
      FURTHEST("Furthest"),
      TOP_DOWN("TopDown");

      private final String displayName;

      Sort(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }

   enum RenderMode implements NamedEntry {
      LINES("Lines"),
      SIDES("Sides"),
      BOTH("Both");

      private final String displayName;

      RenderMode(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }

   enum Prefer implements NamedEntry {
      NONE("None"),
      FORTUNE("Fortune"),
      SILK_TOUCH("SilkTouch");

      private final String displayName;

      Prefer(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }
}
