package dev.argus.client.modules.movement.elytratrip;

import dev.argus.client.chat.ChatTeleportState;
import dev.argus.client.modules.movement.elytratrip.ChestSessionLogic.Result;
import dev.argus.client.modules.movement.elytratrip.RestockPlan.Outcome;
import dev.argus.client.modules.movement.elytratrip.TripSupplies.Supply;
import dev.argus.client.modules.movement.elytratrip.TripSupplies.SupplyCounts;
import dev.argus.client.utils.inventory.InventoryUtils;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * One station run: hold, teleport to the station, dump spent gear, restock each role, equip, teleport back. Driven a tick
 * at a time by the coordinator; ported from the mineflayer bot's six refill steps.
 */
public final class TripRefill {
   public enum Status { RUNNING, DONE, SHORT, FAILED, TELEPORT_GAVE_UP }

   private enum Step { HOLD, TRAVEL, DUMP, RESTOCK, EQUIP, RETURN, FINISHED }

   /** What the refill needs from the trip around it. */
   public interface Host {
      String serverKey();

      boolean dumpJunk();

      /** The inventory saved in setup mode, whose slots the refill puts items back in, or null. */
      default TripLoadout loadout() {
         return null;
      }

      /** Stop flying and hover or glide in place. */
      void hold();

      void sendCommand(String command);
   }

   public static final long SUPPLY_OPEN_MS = 30_000;
   public static final long DUMP_OPEN_MS = 10_000;
   public static final long HOPPER_WAIT_MS = 4_000;
   public static final int HOPPER_REOPENS = 20;
   /** The station counts as here when a tagged chest is this close. */
   private static final double STATION_NEAR_SQ = 16 * 16;
   private static final long SETTLE_MS = 1_500;
   private static final long CHECKPOINT_WAIT_MS = 15_000;
   private static final long RESEND_MS = 3_000;
   private static final long EQUIP_GAP_MS = 250;
   /** Armour, gapples, totems, then elytras; each is put on as soon as it is taken. Food comes after, then the fill pass. */
   private static final List<ChestRole> RESTOCK_ORDER = List.of(ChestRole.ARMOUR, ChestRole.GAPPLES, ChestRole.TOTEMS, ChestRole.ELYTRAS,
      ChestRole.FOOD);
   /** The order gear goes on in the equip step. */
   private static final List<ChestRole> EQUIP_ORDER = List.of(ChestRole.ARMOUR, ChestRole.TOTEMS, ChestRole.ELYTRAS);
   /** A guard on putting items back in their saved slots; {@link TripLoadout#nextMove} settles well within this. */
   private static final int MAX_ARRANGE_MOVES = 64;

   private final Minecraft mc = Minecraft.getInstance();
   private final StationStore stations;
   private final TripTeleports teleports;
   private final TripCheckpoint checkpoint;
   private final Supplier<SupplyLimits> limits;
   private final Supplier<String> stationHome;
   private final Supplier<String> checkpointHome;
   private final Host host;

   private Step step = Step.FINISHED;
   private Status status = Status.DONE;
   private boolean afterDeath;
   private boolean test;
   private EnumSet<Supply> reason = EnumSet.noneOf(Supply.class);
   private EnumSet<Supply> shortSupplies = EnumSet.noneOf(Supply.class);
   private String failure = "";
   private long stepStart;
   private long startedAt;
   private boolean sent;
   private long sendAfter;
   private long doneAt;
   private boolean checkpointAsked;
   private ChestSession session;
   private final Deque<ChestRole> roles = new ArrayDeque<>();
   private final Deque<BlockPos> roleChests = new ArrayDeque<>();
   private ChestRole role;
   /** The last pass: elytras into every slot still free, once every other supply is restocked. */
   private boolean filling;
   private boolean filled;
   private int hopperReopens;
   private long hopperUntil;
   private long nextEquip;
   /** The role just restocked, to put on before the next chest is opened. */
   private ChestRole pendingEquip;
   private int arrangeMoves;
   private SupplyCounts before;
   private final Map<ChestRole, Integer> taken = new EnumMap<>(ChestRole.class);
   private String restockNote = "";

   public TripRefill(StationStore stations, TripTeleports teleports, TripCheckpoint checkpoint, Supplier<SupplyLimits> limits,
                     Supplier<String> stationHome, Supplier<String> checkpointHome, Host host) {
      this.stations = stations;
      this.teleports = teleports;
      this.checkpoint = checkpoint;
      this.limits = limits;
      this.stationHome = stationHome;
      this.checkpointHome = checkpointHome;
      this.host = host;
   }

   public void start(boolean afterDeath, EnumSet<Supply> reason) {
      begin(afterDeath, reason, false);
      step = Step.HOLD;
   }

   /** Dump, restock and equip from where the player stands: no /sethome, no teleports. */
   public void startTest() {
      begin(false, EnumSet.noneOf(Supply.class), true);
      enter(Step.DUMP, System.currentTimeMillis());
   }

   /** Picks a refill cut off by a disconnect back up, from the teleport to the station on. */
   public void resume(boolean afterDeath) {
      begin(afterDeath, reason, false);
      enter(atStation() ? Step.DUMP : Step.TRAVEL, System.currentTimeMillis());
   }

   /** After a disconnect cut a refill off at the end: just go back to the checkpoint. */
   public void resumeReturn() {
      begin(afterDeath, reason, false);
      enter(Step.RETURN, System.currentTimeMillis());
   }

   private void begin(boolean afterDeath, EnumSet<Supply> reason, boolean test) {
      abort();
      this.afterDeath = afterDeath;
      this.reason = EnumSet.copyOf(reason.isEmpty() ? EnumSet.noneOf(Supply.class) : reason);
      this.test = test;
      this.status = Status.RUNNING;
      this.failure = "";
      this.shortSupplies = EnumSet.noneOf(Supply.class);
      this.startedAt = this.stepStart = System.currentTimeMillis();
      this.checkpointAsked = false;
      this.taken.clear();
      this.restockNote = "";
      this.pendingEquip = null;
      this.arrangeMoves = 0;
      this.before = mc.player == null ? null : TripInventory.counts(mc.player);
      teleports.resetFailures();
   }

   public void abort() {
      if (session != null) session.abort();
      session = null;
      step = Step.FINISHED;
   }

   public boolean running() {
      return status == Status.RUNNING && step != Step.FINISHED;
   }

   /** Whether the refill has reached the station's chests, so a resume after a disconnect starts at the right step. */
   public boolean pastTravel() {
      return step == Step.DUMP || step == Step.RESTOCK || step == Step.EQUIP;
   }

   public boolean returning() {
      return step == Step.RETURN;
   }

   /** After a restart: whether the refill being resumed followed a death. */
   public void restoreAfterDeath(boolean afterDeath) {
      this.afterDeath = afterDeath;
   }

   public boolean afterDeath() {
      return afterDeath;
   }

   public EnumSet<Supply> shortSupplies() {
      return EnumSet.copyOf(shortSupplies.isEmpty() ? EnumSet.noneOf(Supply.class) : shortSupplies);
   }

   public String failure() {
      return failure;
   }

   public long tookMs() {
      return System.currentTimeMillis() - startedAt;
   }

   /** The step for the status line: "Travel (cooldown 1m 5s)", "Restock (totems)". */
   public String step() {
      long now = System.currentTimeMillis();
      return switch (step) {
         case HOLD -> "Hold";
         case TRAVEL, RETURN -> (step == Step.TRAVEL ? "Travel" : "Return") + countdown(now);
         case DUMP -> "Dump";
         case RESTOCK -> "Restock (" + (role == null ? "" : role.label().toLowerCase()) + ")"
            + (hopperUntil > now ? " waiting for the hopper" : "");
         case EQUIP -> "Equip";
         case FINISHED -> "Done";
      };
   }

   private String countdown(long now) {
      long cooldown = teleports.cooldownLeft(now);
      if (cooldown > 0) return " (cooldown " + ChatTeleportState.remaining(cooldown) + ")";
      long warmup = teleports.warmupLeft(now);
      return warmup > 0 ? " (teleporting in " + ChatTeleportState.remaining(warmup) + ")" : "";
   }

   /** What the refill took, for Discord: "Took 5 elytras, 3 totems, 12 gapples." */
   public String detail() {
      StringBuilder text = new StringBuilder();
      taken.forEach((r, n) -> {
         if (n > 0) text.append(text.isEmpty() ? "" : ", ").append(n).append(' ').append(r.label().toLowerCase());
      });
      String took = text.isEmpty() ? "Took nothing." : "Took " + text + ".";
      return restockNote.isEmpty() ? took : took + " " + restockNote;
   }

   public boolean atStation() {
      LocalPlayer player = mc.player;
      String server = host.serverKey();
      if (player == null || server == null) return false;
      for (BlockPos pos : stations.all(server).keySet()) {
         if (player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) <= STATION_NEAR_SQ) return true;
      }
      return false;
   }

   public Status tick() {
      LocalPlayer player = mc.player;
      if (status != Status.RUNNING || step == Step.FINISHED || player == null || mc.level == null) return status;
      long now = System.currentTimeMillis();
      switch (step) {
         case HOLD -> hold(now);
         case TRAVEL -> teleport(now, stationHome.get(), Step.DUMP);
         case DUMP -> dump(player, now);
         case RESTOCK -> restock(player, now);
         case EQUIP -> equip(player, now);
         case RETURN -> teleport(now, checkpointHome.get(), Step.FINISHED);
         case FINISHED -> {
         }
      }
      return status;
   }

   private void enter(Step next, long now) {
      step = next;
      stepStart = now;
      sent = false;
      sendAfter = 0;
      doneAt = 0;
      if (next == Step.FINISHED) status = Status.DONE;
   }

   private void hold(long now) {
      host.hold();
      if (atStation()) {
         enter(Step.DUMP, now);
         return;
      }
      if (!afterDeath && !checkpointAsked) {
         checkpointAsked = true;
         if (CheckpointTiming.refreshBeforeRefill(now, checkpoint.lastSuccessAt(), checkpoint.serverWaitMs())) checkpoint.requestNow();
      }
      if (checkpoint.waiting() && now - stepStart < CHECKPOINT_WAIT_MS) return;
      enter(Step.TRAVEL, now);
   }

   private void teleport(long now, String home, Step next) {
      if (doneAt > 0) {
         // Let chunks and the server's position settle before acting on the new spot.
         if (now - doneAt < SETTLE_MS) return;
         if (next == Step.DUMP && !atStation()) {
            fail("No tagged chests near /home " + home + ". Stand at the station and check the chest tags.");
            return;
         }
         enter(next, now);
         return;
      }
      if (!sent) {
         if (now < sendAfter || teleports.cooldownLeft(now) > 0 || teleports.busy(now)) return;
         teleports.send(home, now);
         host.sendCommand("home " + home);
         sent = true;
         return;
      }
      switch (teleports.poll(now)) {
         case DONE -> doneAt = now;
         case COOLDOWN -> sent = false;
         case FAILED -> {
            sent = false;
            sendAfter = now + RESEND_MS;
         }
         case GAVE_UP -> {
            failure = "/home " + home + " failed " + "repeatedly.";
            status = Status.TELEPORT_GAVE_UP;
            step = Step.FINISHED;
         }
         default -> {
         }
      }
   }

   private void dump(LocalPlayer player, long now) {
      String server = host.serverKey();
      if (session == null) {
         if (takeOffWornElytra(player)) return;
         List<BlockPos> dumps = stations.chests(server, ChestRole.DUMP, player.position());
         SupplyLimits current = limits.get();
         boolean junk = host.dumpJunk();
         Predicate<ItemStack> out = stack -> switch (TripInventory.kind(stack)) {
            case ELYTRA -> TripSupplies.isWornOut(TripInventory.durability(stack), stack.getMaxDamage(), current);
            case ARMOUR -> TripInventory.percent(stack) < current.armourMinPercent();
            case OTHER -> junk && !stack.isEmpty();
            default -> false;
         };
         if (dumps.isEmpty() || RestockPlan.dump(TripInventory.summaries(player), current, junk).isEmpty()) {
            startRestock(now);
            return;
         }
         session = new ChestSession(dumps.getFirst(), DUMP_OPEN_MS).moveOut(out);
      }
      // A dump chest that won't open or is full isn't a failure: keep the worn gear and carry on.
      if (session.tick() != Result.RUNNING) {
         session = null;
         startRestock(now);
      }
   }

   /**
    * A worn-out elytra on the chest goes to the dump chest in the same trip: swap a good spare on, or take it off into a
    * free slot. One click per tick; true while it acted.
    */
   private boolean takeOffWornElytra(LocalPlayer player) {
      ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
      if (!worn.is(Items.ELYTRA) || !TripSupplies.isWornOut(TripInventory.durability(worn), worn.getMaxDamage(), limits.get())) return false;
      if (player.containerMenu != player.inventoryMenu) return false;
      int spare = TripInventory.bestElytraMenuSlot(player);
      if (spare >= 0 && TripSupplies.isFresh(TripInventory.durability(player.inventoryMenu.getSlot(spare).getItem()), worn.getMaxDamage(), limits.get())) {
         InventoryUtils.swapSlotsWithPickup(spare, TripInventory.CHEST_SLOT);
         return true;
      }
      if (TripInventory.hasFreeSlot(player)) {
         InventoryUtils.clickSlot(TripInventory.CHEST_SLOT, 0, ClickType.QUICK_MOVE);
         return true;
      }
      return false;
   }

   private void startRestock(long now) {
      roles.clear();
      roles.addAll(RESTOCK_ORDER);
      role = null;
      filling = false;
      filled = false;
      roleChests.clear();
      enter(Step.RESTOCK, now);
   }

   private void restock(LocalPlayer player, long now) {
      if (hopperUntil > now) return;
      String server = host.serverKey();
      if (session == null) {
         if (pendingEquip != null) {
            if (equipStep(player, pendingEquip, now)) return;
            pendingEquip = null;
         }
         if (role == null || roleChests.isEmpty()) {
            if (!nextRole(player, server)) {
               enter(Step.EQUIP, now);
               return;
            }
         }
         hopperUntil = 0;
         if (wanted(player, role) <= 0 || !TripInventory.hasFreeSlot(player)) {
            role = null;
            roleChests.clear();
            return;
         }
         session = newSession(roleChests.peekFirst(), role);
      }
      Result result = session.tick();
      if (result == Result.RUNNING) return;
      boolean emptied = result == Result.DONE && session.chestEmptyOfMatches();
      session = null;
      pendingEquip = role;
      taken.merge(role, Math.max(0, count(player, role) - sessionStartCount), Integer::sum);
      boolean stillWanted = wanted(player, role) > 0 && TripInventory.hasFreeSlot(player);
      if (result == Result.DONE && stillWanted && hopperReopens < HOPPER_REOPENS) {
         // Look in the same chest again: it may hold more than one pass took, and once it is empty a hopper may be
         // feeding it, so give that a moment first.
         hopperReopens++;
         if (emptied) hopperUntil = now + HOPPER_WAIT_MS;
         return;
      }
      roleChests.pollFirst();
      hopperReopens = 0;
      if (!stillWanted) {
         role = null;
         roleChests.clear();
      }
   }

   private int wanted(LocalPlayer player, ChestRole forRole) {
      SupplyCounts counts = TripInventory.counts(player);
      return filling && forRole == ChestRole.ELYTRAS ? RestockPlan.fillWant(counts, limits.get()) : RestockPlan.want(counts, limits.get(), forRole);
   }

   private boolean nextRole(LocalPlayer player, String server) {
      if (roles.isEmpty() && !filled) {
         // Every supply is in; now fill what room is left with elytras.
         filled = true;
         if (limits.get().fillEmpty()) {
            filling = true;
            roles.add(ChestRole.ELYTRAS);
         }
      }
      while (!roles.isEmpty()) {
         role = roles.poll();
         roleChests.clear();
         roleChests.addAll(stations.chests(server, role, player.position()));
         hopperReopens = 0;
         if (roleChests.isEmpty()) {
            if (RestockPlan.want(TripInventory.counts(player), limits.get(), role) > 0) {
               restockNote = (restockNote + " No " + role.label() + " chest tagged.").strip();
            }
            continue;
         }
         return true;
      }
      role = null;
      return false;
   }

   private ChestSession newSession(BlockPos chest, ChestRole forRole) {
      SupplyLimits current = limits.get();
      LocalPlayer player = mc.player;
      sessionStartCount = count(player, forRole);
      return switch (forRole) {
         case ELYTRAS -> new ChestSession(chest, SUPPLY_OPEN_MS).takeIn(
            stack -> stack.is(Items.ELYTRA) && TripSupplies.isFresh(TripInventory.durability(stack), stack.getMaxDamage(), current),
            Comparator.comparingInt(TripInventory::durability).reversed(),
            () -> wanted(player, ChestRole.ELYTRAS));
         case ARMOUR -> new ChestSession(chest, SUPPLY_OPEN_MS).takeIn(
            stack -> neededArmour(player, stack, current),
            Comparator.comparingDouble(TripInventory::percent).reversed(),
            () -> RestockPlan.want(TripInventory.counts(player), current, ChestRole.ARMOUR) - spareArmour(player, current));
         case TOTEMS -> new ChestSession(chest, SUPPLY_OPEN_MS).takeIn(stack -> stack.is(Items.TOTEM_OF_UNDYING), (a, b) -> 0,
            () -> RestockPlan.want(TripInventory.counts(player), current, ChestRole.TOTEMS));
         case GAPPLES -> new ChestSession(chest, SUPPLY_OPEN_MS).takeIn(TripInventory::isGapple,
            Comparator.comparing((ItemStack stack) -> stack.is(Items.ENCHANTED_GOLDEN_APPLE)).reversed(),
            () -> RestockPlan.want(TripInventory.counts(player), current, ChestRole.GAPPLES));
         case FOOD -> new ChestSession(chest, SUPPLY_OPEN_MS).takeIn(TripInventory::isFood,
            Comparator.comparingInt(TripInventory::nutrition).reversed(),
            () -> RestockPlan.want(TripInventory.counts(player), current, ChestRole.FOOD));
         case DUMP -> new ChestSession(chest, SUPPLY_OPEN_MS);
      };
   }

   private int sessionStartCount;

   private int count(LocalPlayer player, ChestRole forRole) {
      SupplyCounts counts = TripInventory.counts(player);
      return switch (forRole) {
         case ELYTRAS -> counts.spareElytraDurabilities().size();
         case TOTEMS -> counts.totems();
         case GAPPLES -> counts.gapples();
         case FOOD -> counts.food();
         case ARMOUR -> (int)TripInventory.summaries(player).stream().filter(s -> s.kind() == RestockPlan.Kind.ARMOUR).count();
         case DUMP -> 0;
      };
   }

   /** An armour piece for a slot that is empty or worn out and not already covered by a good piece in the inventory. */
   private static boolean neededArmour(LocalPlayer player, ItemStack stack, SupplyLimits limits) {
      EquipmentSlot slot = TripInventory.armourSlot(stack);
      if (slot == null || TripInventory.percent(stack) < limits.armourMinPercent()) return false;
      if (slot == EquipmentSlot.CHEST && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) return false;
      ItemStack worn = player.getItemBySlot(slot);
      if (!worn.isEmpty() && TripInventory.percent(worn) >= limits.armourMinPercent()) return false;
      return TripInventory.betterArmourMenuSlot(player, slot, limits) < 0;
   }

   /** Good armour already in the inventory waiting to be put on. */
   private static int spareArmour(LocalPlayer player, SupplyLimits limits) {
      int spare = 0;
      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         if (slot == EquipmentSlot.CHEST && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) continue;
         ItemStack worn = player.getItemBySlot(slot);
         boolean needs = worn.isEmpty() || TripInventory.percent(worn) < limits.armourMinPercent();
         if (needs && TripInventory.betterArmourMenuSlot(player, slot, limits) >= 0) spare++;
      }
      return spare;
   }

   private void equip(LocalPlayer player, long now) {
      if (now < nextEquip) return;
      if (player.containerMenu != player.inventoryMenu) {
         player.closeContainer();
         nextEquip = now + EQUIP_GAP_MS;
         return;
      }
      for (ChestRole gear : EQUIP_ORDER) {
         if (equipStep(player, gear, now)) return;
      }
      if (arrangeStep(player, now)) return;
      SupplyLimits current = limits.get();
      SupplyCounts after = TripInventory.counts(player);
      Outcome outcome = RestockPlan.outcome(after, current, afterDeath);
      if (outcome != Outcome.OK) {
         shortSupplies = TripSupplies.belowMinimum(after, current);
         if (outcome == Outcome.NO_ELYTRA) shortSupplies.add(Supply.ELYTRAS);
         if (!test) {
            status = Status.SHORT;
            step = Step.FINISHED;
            return;
         }
      }
      if (test) {
         enter(Step.FINISHED, now);
         return;
      }
      enter(Step.RETURN, now);
   }

   /**
    * One step towards wearing the best of this gear: waits out the gap between clicks, closes a chest screen, or makes one
    * swap. False once there is nothing left to put on.
    */
   private boolean equipStep(LocalPlayer player, ChestRole gear, long now) {
      if (now < nextEquip) return true;
      if (!canEquip(player, gear)) return false;
      if (player.containerMenu != player.inventoryMenu) {
         player.closeContainer();
      } else {
         equipOne(player, gear);
      }
      nextEquip = now + EQUIP_GAP_MS;
      return true;
   }

   /** Inventory-menu {from, to} for the next piece of this gear to put on, or null. */
   private int[] equipMove(LocalPlayer player, ChestRole gear) {
      SupplyLimits current = limits.get();
      switch (gear) {
         case ARMOUR -> {
            EquipmentSlot[] armour = {EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            int[] menuSlots = {5, 7, 8};
            for (int i = 0; i < armour.length; i++) {
               int better = TripInventory.betterArmourMenuSlot(player, armour[i], current);
               if (better >= 0) return new int[]{better, menuSlots[i]};
            }
            return null;
         }
         case TOTEMS -> {
            if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return null;
            int totem = TripInventory.totemMenuSlot(player);
            return totem >= 0 ? new int[]{totem, TripInventory.OFFHAND_SLOT} : null;
         }
         case ELYTRAS -> {
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            int elytra = TripInventory.bestElytraMenuSlot(player);
            int wornDurability = chest.is(Items.ELYTRA) ? TripInventory.durability(chest) : -1;
            boolean better = elytra >= 0 && TripInventory.durability(player.inventoryMenu.getSlot(elytra).getItem()) > wornDurability;
            return better && (chest.isEmpty() || chest.is(Items.ELYTRA)) ? new int[]{elytra, TripInventory.CHEST_SLOT} : null;
         }
         default -> {
            return null;
         }
      }
   }

   private boolean canEquip(LocalPlayer player, ChestRole gear) {
      return equipMove(player, gear) != null;
   }

   private void equipOne(LocalPlayer player, ChestRole gear) {
      int[] move = equipMove(player, gear);
      if (move != null) InventoryUtils.swapSlotsWithPickup(move[0], move[1]);
   }

   /** One swap putting an item back in the slot the saved loadout has it in; false once everything that can is in place. */
   private boolean arrangeStep(LocalPlayer player, long now) {
      TripLoadout saved = host.loadout();
      if (saved == null || arrangeMoves >= MAX_ARRANGE_MOVES) return false;
      if (now < nextEquip) return true;
      int[] move = saved.nextMove(TripInventory.slotIds(player));
      if (move == null) return false;
      if (player.containerMenu != player.inventoryMenu) {
         player.closeContainer();
      } else {
         InventoryUtils.swapSlotsWithPickup(InventoryUtils.toMenuSlot(move[0]), InventoryUtils.toMenuSlot(move[1]));
         arrangeMoves++;
      }
      nextEquip = now + EQUIP_GAP_MS;
      return true;
   }

   private void fail(String reason) {
      failure = reason;
      status = Status.FAILED;
      if (session != null) session.abort();
      session = null;
      step = Step.FINISHED;
   }

   /** The supplies the refill set out to fix, for Discord. */
   public EnumSet<Supply> reason() {
      return reason.isEmpty() ? EnumSet.noneOf(Supply.class) : EnumSet.copyOf(reason);
   }

   /** Counts when the refill started, for the "took" line. */
   public SupplyCounts before() {
      return before;
   }
}
