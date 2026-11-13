package dev.argus.client.modules.player;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.FeatureBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.world.BlockInteractionUtils;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Places sponges where they will soak up the most connected water, ported from Comet's Smart Sponge.
 * Each candidate is scored with vanilla's own sponge traversal, so the first sponge goes where it removes the most.
 */
public final class AutoSpongeModule extends Module {
   /** Vanilla sponge reach: six-neighbour connected water, depth six, at most 64 absorbed blocks. */
   private static final int MAX_DEPTH = 6;
   private static final int MAX_ABSORBED = 64;
   private static final long CONFIRM_TIMEOUT_MS = 2000L;
   private static final long RETRY_AFTER_MS = 2000L;

   private final Setting<Float> rangeSetting;
   private final Setting<Integer> delaySetting;
   private final Setting<Boolean> rotateSetting;
   private final Setting<Boolean> breakWetSetting;
   private final Setting<Boolean> renderSetting;
   private final Setting<Color> fillSetting;
   private final Setting<Color> outlineSetting;
   private final Setting<Float> lineWidthSetting;

   private final ElapsedTimer placementTimer = new ElapsedTimer();
   // Packet callbacks may run on the network thread; only these are shared with it.
   private final Set<BlockPos> owned = ConcurrentHashMap.newKeySet();
   private final Queue<Update> updates = new ConcurrentLinkedQueue<>();
   private volatile BlockPos pending;
   private volatile boolean correction;
   private final Set<BlockPos> wet = new HashSet<>();
   private final Map<BlockPos, Long> retryAt = new HashMap<>();
   private long pendingSince;
   private ClientLevel world;
   private BlockPos mining;
   private String status;

   private record Update(BlockPos pos, BlockState state) {
   }

   private record Spot(BlockPos pos, int score, double distance) {
   }

   public AutoSpongeModule() {
      super("AutoSponge", "Places sponges where they soak up the most connected water and waterlogged blocks.", ModuleCategory.PLAYER, "SmartSponge");
      this.rangeSetting = this.add(new NumberSetting<>("Range", 4.5F, 1.0F, 6.0F).withUnit("m"));
      this.delaySetting = this.add(new NumberSetting<>("Delay", 250, 0, 2000).withUnit("ms"));
      this.rotateSetting = this.add(new BooleanSetting("Rotate", true));
      this.breakWetSetting = this.add(new BooleanSetting("BreakWet", false));
      this.renderSetting = this.add(new BooleanSetting("Render", true).markVisibilityDependency());
      this.fillSetting = this.add(new ColorSetting("Fill", new Color(0x30E8D44D, true)).dependsOn(this.renderSetting));
      this.outlineSetting = this.add(new ColorSetting("Outline", new Color(0xA0E8D44D, true)).dependsOn(this.renderSetting));
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
      this.stopMining();
      this.pending = null;
      this.correction = false;
      this.owned.clear();
      this.updates.clear();
      this.wet.clear();
      this.retryAt.clear();
      this.world = null;
      this.status = null;
   }

   private void stopMining() {
      if (this.mining != null && ClientAccess.argusclientMinecraft.gameMode != null) {
         ClientAccess.argusclientMinecraft.gameMode.stopDestroyBlock();
      }

      this.mining = null;
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         this.correction = true;
      } else if (event.getPacket() instanceof ClientboundBlockUpdatePacket packet) {
         this.enqueue(packet.getPos(), packet.getBlockState());
      } else if (event.getPacket() instanceof ClientboundSectionBlocksUpdatePacket packet) {
         packet.runUpdates(this::enqueue);
      }
   }

   private void enqueue(BlockPos pos, BlockState state) {
      if (pos.equals(this.pending) || this.owned.contains(pos)) {
         this.updates.add(new Update(pos.immutable(), state));
      }
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

      long now = System.currentTimeMillis();
      if (this.correction) {
         // The server moved us; whatever was in flight may have been rejected.
         this.correction = false;
         this.pending = null;
         this.stopMining();
         this.placementTimer.reset();
      }

      this.applyUpdates(now);
      if (!player.isAlive()) {
         this.stopMining();
         return;
      }

      if (this.pending != null) {
         if (now - this.pendingSince <= CONFIRM_TIMEOUT_MS) {
            this.status = "Waiting";
            return;
         }

         this.retryAt.put(this.pending, now + RETRY_AFTER_MS);
         this.pending = null;
      }

      this.retryAt.values().removeIf(at -> at <= now);
      if (this.breakWetSetting.getValue() && this.collectWet()) {
         return;
      }

      this.stopMining();
      if (!this.placementTimer.hasElapsed(this.delaySetting.getValue().longValue())) {
         return;
      }

      InventoryUtils.SwapSlot spongeSlot = InventoryUtils.findSwapSlot(Items.SPONGE, false);
      if (!spongeSlot.isFound()) {
         this.status = "No sponge";
         return;
      }

      Spot spot = this.bestSpot(player, level);
      if (spot == null) {
         // Searching is the costly part, so an empty search also waits out the delay.
         this.status = "Idle";
         this.placementTimer.reset();
         return;
      }

      Direction support = BlockInteractionUtils.findSupportDirection(spot.pos(), BlockInteractionUtils.isStrictDirectionEnabled(), true);
      if (support == null) {
         this.retryAt.put(spot.pos(), now + RETRY_AFTER_MS);
         return;
      }

      this.place(player, spot, support, spongeSlot, now);
   }

   private void applyUpdates(long now) {
      Update update;
      while ((update = this.updates.poll()) != null) {
         boolean sponge = update.state().is(Blocks.SPONGE) || update.state().is(Blocks.WET_SPONGE);
         if (update.pos().equals(this.pending)) {
            if (sponge) {
               this.owned.add(update.pos());
            } else {
               this.retryAt.put(update.pos(), now + RETRY_AFTER_MS);
            }

            this.pending = null;
         }

         if (this.owned.contains(update.pos())) {
            if (update.state().is(Blocks.WET_SPONGE)) {
               this.wet.add(update.pos());
            } else {
               this.wet.remove(update.pos());
            }

            if (!sponge) {
               this.owned.remove(update.pos());
            }
         }
      }
   }

   private Spot bestSpot(LocalPlayer player, ClientLevel level) {
      Vec3 eye = player.getEyePosition();
      float range = this.rangeSetting.getValue();
      double rangeSq = (double) range * range;
      int radius = (int) Math.ceil(range);
      BlockPos origin = BlockPos.containing(eye);
      Map<BlockPos, Boolean> waterCache = new HashMap<>();
      Predicate<BlockPos> absorbable = pos -> waterCache.computeIfAbsent(pos, key -> this.absorbable(level, key));
      List<Spot> spots = new ArrayList<>();
      for (BlockPos mutable : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
         double distance = Vec3.atCenterOf(mutable).distanceToSqr(eye);
         if (distance > rangeSq) {
            continue;
         }

         BlockPos pos = mutable.immutable();
         if (this.retryAt.containsKey(pos) || !this.waterNearby(pos, absorbable) || !this.placeable(player, level, pos)) {
            continue;
         }

         int score = score(pos, absorbable) + (absorbable.test(pos) ? 1 : 0);
         spots.add(new Spot(pos, score, distance));
      }

      // Most water first; ties go to the closer spot. Supports are only searched for the winner.
      return spots.stream()
         .max(Comparator.comparingInt(Spot::score).thenComparing(Comparator.comparingDouble(Spot::distance).reversed()))
         .orElse(null);
   }

   private void place(LocalPlayer player, Spot spot, Direction support, InventoryUtils.SwapSlot spongeSlot, long now) {
      BlockPos pos = spot.pos();
      int previousSlot = player.getInventory().getSelectedSlot();
      InteractionHand hand = spongeSlot.hand();
      InventoryUtils.swapTo(spongeSlot);
      boolean placed;
      try {
         placed = BlockInteractionUtils.placeBlock(pos, null, support, false, hand);
      } finally {
         InventoryUtils.swapBack(previousSlot, spongeSlot);
      }

      if (!placed) {
         this.retryAt.put(pos, now + RETRY_AFTER_MS);
         return;
      }

      this.pending = pos;
      this.pendingSince = now;
      this.placementTimer.reset();
      this.status = "+" + spot.score();
      if (this.rotateSetting.getValue()) {
         float[] rotations = RotationManager.getRotationsToFace(Vec3.atCenterOf(pos), support);
         ClientContext.rotationManager.requestRotation(rotations, 100);
      }

      if (this.renderSetting.getValue()) {
         ClientContext.featureBoxRenderer.addBox(this, pos);
      }
   }

   /** Mines one wet sponge this module placed, a tick at a time. Returns true while it is busy doing so. */
   private boolean collectWet() {
      ClientLevel level = this.world;
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      float range = this.rangeSetting.getValue();
      for (BlockPos pos : List.copyOf(this.wet)) {
         if (!level.getBlockState(pos).is(Blocks.WET_SPONGE)) {
            this.wet.remove(pos);
            continue;
         }

         if (Vec3.atCenterOf(pos).distanceTo(player.getEyePosition()) > range) {
            continue;
         }

         if (this.mining != null && !this.mining.equals(pos)) {
            this.stopMining();
         }

         Direction face = Direction.getApproximateNearest(player.getEyePosition().subtract(Vec3.atCenterOf(pos)));
         this.mining = pos;
         if (this.rotateSetting.getValue()) {
            ClientContext.rotationManager.requestRotation(RotationManager.getRotationsToFace(Vec3.atCenterOf(pos), face), 100);
         }

         ClientAccess.argusclientMinecraft.gameMode.continueDestroyBlock(pos, face);
         player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).getAttackAnimation(), false);
         this.status = "Collecting";
         return true;
      }

      return false;
   }

   private boolean loaded(ClientLevel level, BlockPos pos) {
      return !level.isOutsideBuildHeight(pos) && level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) && level.getWorldBorder().isWithinBounds(pos);
   }

   private boolean absorbable(ClientLevel level, BlockPos pos) {
      if (!this.loaded(level, pos)) {
         return false;
      }

      BlockState state = level.getBlockState(pos);
      return state.getFluidState().is(FluidTags.WATER)
         && (state.getBlock() instanceof BucketPickup
            || state.getBlock() instanceof LiquidBlock
            || state.is(Blocks.KELP)
            || state.is(Blocks.KELP_PLANT)
            || state.is(Blocks.SEAGRASS)
            || state.is(Blocks.TALL_SEAGRASS));
   }

   private boolean waterNearby(BlockPos pos, Predicate<BlockPos> absorbable) {
      if (absorbable.test(pos)) {
         return true;
      }

      for (Direction direction : Direction.values()) {
         if (absorbable.test(pos.relative(direction))) {
            return true;
         }
      }

      return false;
   }

   private boolean placeable(LocalPlayer player, ClientLevel level, BlockPos pos) {
      if (!this.loaded(level, pos)) {
         return false;
      }

      BlockState state = level.getBlockState(pos);
      return state.canBeReplaced()
         && (state.getFluidState().isEmpty() || state.getFluidState().is(FluidTags.WATER))
         && !new AABB(pos).intersects(player.getBoundingBox().inflate(0.08, 0.0, 0.08))
         && BlockInteractionUtils.canPlaceBlock(pos, Blocks.SPONGE, true);
   }

   /** How many blocks vanilla's sponge would absorb if placed at {@code origin}. */
   static int score(BlockPos origin, Predicate<BlockPos> absorbable) {
      Set<BlockPos> visited = new HashSet<>();
      ArrayDeque<BlockPos> queue = new ArrayDeque<>();
      ArrayDeque<Integer> depths = new ArrayDeque<>();
      visited.add(origin);
      queue.add(origin);
      depths.add(0);
      int count = 0;
      while (!queue.isEmpty()) {
         BlockPos pos = queue.removeFirst();
         int depth = depths.removeFirst();
         if (depth == MAX_DEPTH) {
            continue;
         }

         for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            if (!visited.add(next) || !absorbable.test(next)) {
               continue;
            }

            if (++count >= MAX_ABSORBED) {
               return count;
            }

            queue.addLast(next);
            depths.addLast(depth + 1);
         }
      }

      return count;
   }
}
