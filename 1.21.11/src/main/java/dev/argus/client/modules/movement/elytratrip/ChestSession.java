package dev.argus.client.modules.movement.elytratrip;

import dev.argus.client.ClientContext;
import dev.argus.client.integrations.baritone.BaritoneBridge;
import dev.argus.client.modules.movement.elytratrip.ChestSessionLogic.Action;
import dev.argus.client.modules.movement.elytratrip.ChestSessionLogic.Result;
import dev.argus.client.modules.movement.elytratrip.ChestSessionLogic.SlotView;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.entity.SwingAction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One visit to one station chest: walk within reach, open it with a realistic click on its nearest face, then move items
 * in or out by shift-click under {@link ChestSessionLogic}'s rules. Call {@link #tick()} once per client tick.
 */
public final class ChestSession {
   public static final double REACH = 4.5;
   public static final long SLOT_WAIT_MS = 1_500;
   public static final long STALL_MS = 15_000;
   /** After a session gives up on a chest, windows that open this soon after are that chest's, arriving late. */
   private static final long LATE_WINDOW_MS = 5_000;
   private static long abandonedAt = Long.MIN_VALUE;

   private enum Mode { OUT, IN }

   private final Minecraft mc = Minecraft.getInstance();
   private final BlockPos chest;
   private final ChestSessionLogic logic;
   private Mode mode = Mode.OUT;
   private Predicate<ItemStack> filter = stack -> false;
   private Comparator<ItemStack> best = (a, b) -> 0;
   private IntSupplier wanted = () -> 0;
   private boolean approaching;
   private int attempt;
   private int windowAttempt = -1;
   private Integer windowId;
   private boolean finished;

   public ChestSession(BlockPos chest, long openTimeoutMs) {
      this.chest = chest.immutable();
      this.logic = new ChestSessionLogic(openTimeoutMs, SLOT_WAIT_MS, STALL_MS);
   }

   public BlockPos chest() {
      return chest;
   }

   /** Put every player-inventory stack matching the filter into the chest. */
   public ChestSession moveOut(Predicate<ItemStack> filter) {
      this.mode = Mode.OUT;
      this.filter = filter;
      return this;
   }

   /** Take matching stacks from the chest, best first, until {@code wanted} (items still needed) reaches 0. */
   public ChestSession takeIn(Predicate<ItemStack> filter, Comparator<ItemStack> best, IntSupplier wanted) {
      this.mode = Mode.IN;
      this.filter = filter;
      this.best = best;
      this.wanted = wanted;
      return this;
   }

   /** Whether the chest still had matching items when the session ended. Only meaningful after a DONE in IN mode. */
   public boolean chestEmptyOfMatches() {
      return lastChestMatches == 0;
   }

   private int lastChestMatches = -1;

   public Result tick() {
      LocalPlayer player = mc.player;
      if (player == null || mc.level == null || mc.gameMode == null) return Result.RUNNING;
      long now = System.currentTimeMillis();
      Result result = logic.result();
      if (result != Result.RUNNING) {
         finish(result);
         return result;
      }
      if (now - abandonedAt < LATE_WINDOW_MS) {
         // A late window from the chest the last session gave up on: close it, don't mistake it for this one.
         if (openWindow(player) != null) player.closeContainer();
         return Result.RUNNING;
      }
      if (!withinReach(player)) {
         approach(player);
         return Result.RUNNING;
      }
      if (approaching) {
         approaching = false;
         baritone().cancelPathing();
      }
      Integer open = openWindow(player);
      if (open != null && windowId == null) {
         windowId = open;
         windowAttempt = attempt;
      }
      if (open != null && !logic.acceptWindow(windowAttempt)) {
         player.closeContainer();
         windowId = null;
         open = null;
      }
      if (open == null) windowId = null;
      Action action = logic.tick(now, open, open == null ? List.of() : views(player));
      switch (action) {
         case Action.Click click -> click(player);
         case Action.Reopen reopen -> click(player);
         case Action.Plan plan -> logic.wantMoves(plan(player));
         case Action.ShiftClick shift -> InventoryUtils.clickSlot(open, shift.slot(), 0, ClickType.QUICK_MOVE);
         case Action.Wait wait -> {
         }
      }
      result = logic.result();
      if (result != Result.RUNNING) finish(result);
      return result;
   }

   /** Stops at once and closes the chest; a window that opens later is treated as late. */
   public void abort() {
      if (!finished) {
         abandonedAt = System.currentTimeMillis();
         finished = true;
      }
      if (approaching) baritone().cancelPathing();
      LocalPlayer player = mc.player;
      if (player != null && openWindow(player) != null) player.closeContainer();
   }

   private void finish(Result result) {
      if (finished) return;
      finished = true;
      if (result == Result.OPEN_TIMEOUT || result == Result.REOPEN_FAILED) abandonedAt = System.currentTimeMillis();
      LocalPlayer player = mc.player;
      if (player != null && openWindow(player) != null) player.closeContainer();
   }

   private Integer openWindow(LocalPlayer player) {
      return player.containerMenu instanceof ChestMenu menu && player.containerMenu != player.inventoryMenu ? menu.containerId : null;
   }

   private int chestSlots(LocalPlayer player) {
      return ((ChestMenu)player.containerMenu).getRowCount() * 9;
   }

   private List<SlotView> views(LocalPlayer player) {
      List<SlotView> views = new ArrayList<>();
      for (var slot : player.containerMenu.slots) {
         ItemStack stack = slot.getItem();
         views.add(new SlotView(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), stack.getDamageValue()));
      }
      return views;
   }

   private List<Integer> plan(LocalPlayer player) {
      var slots = player.containerMenu.slots;
      int chestSize = chestSlots(player);
      List<Integer> moves = new ArrayList<>();
      if (mode == Mode.OUT) {
         boolean chestHasRoom = false;
         for (int i = 0; i < chestSize; i++) {
            if (slots.get(i).getItem().isEmpty()) chestHasRoom = true;
         }
         for (int i = chestSize; i < slots.size(); i++) {
            if (filter.test(slots.get(i).getItem())) moves.add(i);
         }
         if (!moves.isEmpty() && !chestHasRoom) logic.markFull();
         return moves;
      }
      List<Integer> candidates = new ArrayList<>();
      for (int i = 0; i < chestSize; i++) {
         if (filter.test(slots.get(i).getItem())) candidates.add(i);
      }
      lastChestMatches = candidates.size();
      candidates.sort((a, b) -> best.compare(slots.get(a).getItem(), slots.get(b).getItem()));
      int need = wanted.getAsInt();
      for (int slot : candidates) {
         if (need <= 0) break;
         moves.add(slot);
         need -= slots.get(slot).getItem().getCount();
      }
      return moves;
   }

   private boolean withinReach(LocalPlayer player) {
      return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(chest)) <= REACH * REACH;
   }

   private void approach(LocalPlayer player) {
      if (approaching && baritone().isPathingOrCalculating()) return;
      BlockPos stand = standingSpot();
      if (stand != null) {
         baritone().pathTo(stand);
         approaching = true;
      }
   }

   /** An open block beside the chest with floor under it, nearest the player. */
   private BlockPos standingSpot() {
      BlockPos bestSpot = null;
      double bestDistance = Double.MAX_VALUE;
      for (Direction direction : Direction.Plane.HORIZONTAL) {
         for (int dy = -1; dy <= 1; dy++) {
            BlockPos spot = chest.relative(direction).above(dy);
            if (!mc.level.getBlockState(spot).getCollisionShape(mc.level, spot).isEmpty()) continue;
            if (!mc.level.getBlockState(spot.above()).getCollisionShape(mc.level, spot.above()).isEmpty()) continue;
            if (mc.level.getBlockState(spot.below()).getCollisionShape(mc.level, spot.below()).isEmpty()) continue;
            double distance = mc.player.position().distanceToSqr(Vec3.atBottomCenterOf(spot));
            if (distance < bestDistance) {
               bestDistance = distance;
               bestSpot = spot;
            }
         }
      }
      return bestSpot;
   }

   /** Looks at and clicks a realistic point on the chest face nearest the player's eyes, the way a player would. */
   private void click(LocalPlayer player) {
      attempt++;
      logic.attemptStarted(attempt);
      Vec3 eyes = player.getEyePosition();
      Direction face = Direction.UP;
      double nearest = Double.MAX_VALUE;
      for (Direction direction : Direction.values()) {
         BlockPos neighbour = chest.relative(direction);
         if (!mc.level.getBlockState(neighbour).getCollisionShape(mc.level, neighbour).isEmpty()) continue;
         double distance = eyes.distanceToSqr(Vec3.atCenterOf(chest).add(Vec3.atLowerCornerOf(direction.getUnitVec3i()).scale(0.5)));
         if (distance < nearest) {
            nearest = distance;
            face = direction;
         }
      }
      ThreadLocalRandom random = ThreadLocalRandom.current();
      Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
      Vec3 point = Vec3.atCenterOf(chest).add(normal.scale(0.5));
      double a = random.nextDouble(-0.3, 0.3);
      double b = random.nextDouble(-0.3, 0.3);
      point = switch (face.getAxis()) {
         case X -> point.add(0, a, b);
         case Y -> point.add(a, 0, b);
         case Z -> point.add(a, b, 0);
      };
      Vec3 look = point.subtract(eyes);
      player.setYRot((float)(Math.toDegrees(Math.atan2(look.z, look.x)) - 90.0));
      player.setXRot((float)-Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z))));
      mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(point, face, chest, false));
      ClientContext.swingManager.swing(InteractionHand.MAIN_HAND, SwingAction.INTERACT);
   }

   private static BaritoneBridge baritone() {
      return ClientContext.baritoneBridge;
   }
}
