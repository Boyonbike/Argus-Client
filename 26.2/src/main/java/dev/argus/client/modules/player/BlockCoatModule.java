package dev.argus.client.modules.player;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.BlockStateChangeEvent;
import dev.argus.client.events.ClientChunkCacheReplaceWithPacketDataEvent;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.MultiPlayerGameModeStartDestroyBlockEvent;
import dev.argus.client.events.MultiPlayerGameModeUseItemEvent;
import dev.argus.client.events.MultiPlayerGameModeUseItemOnEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.movement.FlightModule;
import dev.argus.client.modules.player.blockcoat.BlockCoatBlasts;
import dev.argus.client.modules.player.blockcoat.BlockCoatInteraction;
import dev.argus.client.modules.player.blockcoat.CoatingRules;
import dev.argus.client.modules.player.blockcoat.FlightPolicy;
import dev.argus.client.modules.player.blockcoat.HeldItemPause;
import dev.argus.client.modules.player.blockcoat.LiquidFiller;
import dev.argus.client.modules.player.blockcoat.ModeGeometry;
import dev.argus.client.modules.player.blockcoat.PacketBudget;
import dev.argus.client.modules.player.blockcoat.PassageClearance;
import dev.argus.client.modules.player.blockcoat.PassageGeometry;
import dev.argus.client.modules.player.blockcoat.PlacementGeometry;
import dev.argus.client.modules.player.blockcoat.PlacementOrder;
import dev.argus.client.modules.player.blockcoat.PlacementTracker;
import dev.argus.client.modules.player.blockcoat.ServerPacer;
import dev.argus.client.modules.player.blockcoat.StickySlot;
import dev.argus.client.modules.player.blockcoat.TntBlastPlanner;
import dev.argus.client.modules.player.blockcoat.TntFireSafety;
import dev.argus.client.modules.player.blockcoat.TntRedstoneSafety;
import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.RegistrySetSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.network.PacketSender;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Coats exposed terrain with a block, with priority jump placement, scaffold strips and tunnel backfill.
 * Ported from Comet's Block Coat; never ignites placed blocks.
 */
public final class BlockCoatModule extends Module {
    private static final Minecraft mc = ClientAccess.argusclientMinecraft;

    public enum SurfaceTypes implements NamedEntry {
        ALL("All"), FLOORS("Floors"), WALLS("Walls"), CEILINGS("Ceilings");
        private final String displayName;
        SurfaceTypes(String displayName) { this.displayName = displayName; }
        @Override public String getName() { return displayName; }
    }

    public enum AutoSwitch implements NamedEntry {
        STICKY("Sticky"), SILENT("Silent"), NORMAL("Normal"), DISABLED("Disabled");
        private final String displayName;
        AutoSwitch(String displayName) { this.displayName = displayName; }
        @Override public String getName() { return displayName; }
    }

    public enum Priority implements NamedEntry {
        NEAREST("Nearest"), FARTHEST("Farthest");
        private final String displayName;
        Priority(String displayName) { this.displayName = displayName; }
        @Override public String getName() { return displayName; }
    }

    public enum ServerMode implements NamedEntry {
        ADAPTIVE("Adaptive"), CONSERVATIVE("Conservative"), FAST("Fast");
        private final String displayName;
        ServerMode(String displayName) { this.displayName = displayName; }
        @Override public String getName() { return displayName; }
    }

    public enum TunnelDirection implements NamedEntry {
        MOVEMENT("Movement"), FACING("Facing");
        private final String displayName;
        TunnelDirection(String displayName) { this.displayName = displayName; }
        @Override public String getName() { return displayName; }
    }

    // General
    private final Setting<Set<Block>> blocks = add(new RegistrySetSetting<>("Blocks", BuiltInRegistries.BLOCK, Blocks.TNT));
    private final Setting<Integer> radius = add(new NumberSetting<>("Radius", 5, 1, 8));
    private final Setting<Integer> perTick = add(new NumberSetting<>("BPT", 8, 1, 32));
    private final Setting<Float> reach = add(new NumberSetting<>("Reach", 6.0F, 1.0F, 6.0F).withUnit("m"));
    private final Setting<SurfaceTypes> surfaces = add(new EnumSetting<>("Surfaces", SurfaceTypes.ALL));
    private final Setting<AutoSwitch> autoSwitch = add(new EnumSetting<>("AutoSwitch", AutoSwitch.STICKY));
    private final Setting<Priority> order = add(new EnumSetting<>("Order", Priority.NEAREST));
    private final Setting<Boolean> rotate = add(new BooleanSetting("Rotate", true));
    private final Setting<Integer> retryDelay = add(new NumberSetting<>("RetryDelay", 3, 1, 40));
    private final Setting<Integer> confirmTimeout = add(new NumberSetting<>("ConfirmTimeout", 20, 5, 100));
    private final Setting<Set<Item>> pauseItems = add(new RegistrySetSetting<>("PauseItems", BuiltInRegistries.ITEM, Items.FLINT_AND_STEEL));
    private final Setting<Integer> pauseItemsTimeout = add(new NumberSetting<>("PauseTimeout", 5, 0, 60).withUnit("s"));
    private final Setting<Boolean> pauseItemsResetOnUse = add(new BooleanSetting("PauseResetOnUse", true));
    private final Setting<Boolean> storageOnly = add(new BooleanSetting("StorageOnly", false));
    // TNT saving and liquid filling only apply while the coating block is TNT.
    private final Setting<Boolean> conservativeTnt = add(new BooleanSetting("ConservativeTNT", false));
    private final Setting<Boolean> liquidFiller = add(new BooleanSetting("LiquidFiller", true));
    private final Setting<Boolean> fireSafety = add(new BooleanSetting("FireSafety", true));
    // Scaffold and tunnel
    private final Setting<Boolean> scaffold = add(new BooleanSetting("Scaffold", false));
    private final Setting<Integer> scaffoldWidth = add(new NumberSetting<>("ScaffoldWidth", 3, 1, 9).dependsOn(scaffold));
    private final Setting<Boolean> tunnel = add(new BooleanSetting("TunnelBackfill", false));
    private final Setting<TunnelDirection> tunnelDirection = add(new EnumSetting<>("TunnelDirection", TunnelDirection.MOVEMENT).dependsOn(tunnel));
    private final Setting<Boolean> preserveTightCorridors = add(new BooleanSetting("PreserveCorridors", true));
    private final Setting<Boolean> respectTntClearance = add(new BooleanSetting("RespectTNTClearance", true));
    private final Setting<Boolean> flightCoating = add(new BooleanSetting("CoatWhileFlying", true));
    private final Setting<Boolean> externalFlight = add(new BooleanSetting("ExternalFlight", false));
    // Smart jump place
    private final Setting<Boolean> smartJump = add(new BooleanSetting("SmartJump", true));
    private final Setting<Integer> correctionCooldown = add(new NumberSetting<>("CorrectionCooldown", 10, 1, 100));
    // Preview
    private final Setting<Boolean> preview = add(new BooleanSetting("Render", true));
    private final Setting<Boolean> rejectedPreview = add(new BooleanSetting("RenderRejected", false).dependsOn(preview));
    // Server pacing is advanced, so it comes last and stays collapsed.
    private final Setting<Boolean> server = add(new BooleanSetting("ServerPacing", false).markAsGroup());
    private final Setting<ServerMode> serverMode = add(new EnumSetting<>("ServerMode", ServerMode.ADAPTIVE).dependsOn(server));
    private final Setting<Integer> serverCap = add(new NumberSetting<>("RateCap", 2, 1, 8).dependsOn(server));
    private final Setting<Integer> pendingCap = add(new NumberSetting<>("MaxPending", 6, 1, 16).dependsOn(server));
    private final Setting<Boolean> containerSupports = add(new BooleanSetting("ContainerSupports", true).dependsOn(server));
    private final Setting<Boolean> narrowGaps = add(new BooleanSetting("NarrowGaps", false).dependsOn(server));
    private final Setting<Integer> packetLimit = add(new NumberSetting<>("PacketLimit", 150, 40, 800).dependsOn(server));
    private final Setting<Boolean> extraRotations = add(new BooleanSetting("ExtraRotations", false).dependsOn(server));

    private Block activeBlock = Blocks.TNT;
    private boolean activeConservativeTnt;
    private boolean activeLiquidFiller;
    private FlightModule flightModule;
    private ScaffoldModule scaffoldModule;

    private final ServerPacer pacer = new ServerPacer();
    private final PacketBudget packetBudget = new PacketBudget();
    private final HeldItemPause heldItemPause = new HeldItemPause();
    private final PassageClearance passageClearance = new PassageClearance();
    private boolean coatingInteraction;
    private final AtomicInteger sentPackets = new AtomicInteger();
    private int plannedPackets, rotationsPlanned, corrections;
    private boolean packetLimited;
    private final StickySlot sticky = new StickySlot();
    private final Set<BlockPos> changed = ConcurrentHashMap.newKeySet();
    private final Deque<BlockPos> searchQueue = new ArrayDeque<>();
    private final Map<BlockPos, BlockHitResult> hitCache = new HashMap<>();
    private final BlockCoatBlasts blasts = new BlockCoatBlasts();
    private final BlockCoatBlasts.Terrain blastTerrain = new BlockCoatBlasts.Terrain() {
        @Override public boolean loaded(BlockPos pos) { return BlockCoatModule.this.loaded(pos); }
        @Override public boolean coatable(BlockPos pos) { return terrain(pos); }
        @Override public boolean faceEnabled(Direction face) { return enabledFace(face); }
    };
    // Section answers stay valid until a block, chunk or tick boundary.
    private final TntRedstoneSafety.SectionMemo redstoneSections = new TntRedstoneSafety.SectionMemo(this::redstoneSection);
    private final TntRedstoneSafety.SectionMemo fireSections = new TntRedstoneSafety.SectionMemo(this::fireSection);
    private static final Color PREVIEW_FILL = new Color(70, 220, 110, 35), PREVIEW_LINE = new Color(70, 220, 110, 180);
    private static final Color PENDING_FILL = new Color(255, 180, 50, 40), PENDING_LINE = new Color(255, 180, 50, 200);
    private static final Color REJECTED_FILL = new Color(230, 70, 70, 20), REJECTED_LINE = new Color(230, 70, 70, 100);
    private boolean reorder = true;
    private boolean searching;
    private int rays;
    private Priority queuedPriority;
    private final Set<BlockPos> candidates = new HashSet<>();
    private final Map<BlockPos, Integer> visibilityRetry = new HashMap<>();
    private final PlacementTracker<BlockPos, Block> tracker = new PlacementTracker<>(new PlacementTracker.Listener<>() {
        @Override public void accepted(BlockPos pos, int latencyTicks) { pacer.accepted(latencyTicks, rateCap()); }

        @Override public void retryScheduled(BlockPos pos, boolean failure) {
            changed.add(pos); hitCache.remove(pos); visibilityRetry.remove(pos); reorder = true;
            // A correction already paused the pacer; placements it discarded are not rejections.
            if (failure) pacer.failed(tick, retryDelay.getValue());
            if (pos.equals(jumpPending) && jumpActive) { jumpPlaced = false; jumpPending = null; }
        }

        @Override public void retryReady(BlockPos pos) { changed.add(pos); reorder = true; }
    });
    private final List<BlockPos> validPreview = new ArrayList<>();
    private final List<BlockPos> invalidPreview = new ArrayList<>();
    private final ConcurrentLinkedQueue<Update> updates = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean corrected = new AtomicBoolean();
    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private ClientLevel level;
    private volatile BlockPos scanOrigin;
    private SurfaceTypes scannedSurfaces;
    private boolean scannedStorageOnly;
    private volatile int scannedRadius;
    private Vec3 lastPosition;
    private boolean lastGround;
    private BlockPos takeoffCell;
    private BlockPos jumpPending;
    private int tick;
    private boolean scaffoldSuspended;
    private boolean jumpActive, jumpPlaced;
    private int pauseUntil;
    private float facingYaw;
    private Vec3 travel = new Vec3(0, 0, 1);
    private Vec3 passageHeading = Vec3.ZERO;
    private Vec3 laneHeading = new Vec3(0, 0, 1);
    private boolean hasTravel;
    private final Set<BlockPos> passageLane = new HashSet<>();
    private final Set<BlockPos> strip = new LinkedHashSet<>();
    private final Set<BlockPos> criticalStrip = new LinkedHashSet<>();
    private int coveredCount, planningCount, deferredCount;
    private boolean rayLimited;
    private String status = "Starting";

    private record Update(BlockPos pos, boolean matchesBlock, boolean replaceable, PlacementTracker.Attempt<Block> attempt) {}
    private enum Rejection { None, Occupied, Retry, Redstone, Fire, Entity, Protected, Passage, Height, NoTerrain, Reach, Covered, Planning, Deferred }
    private record Validation(Rejection reason, BlockHitResult hit, boolean liquid) {
        boolean valid() { return reason == Rejection.None; }
    }
    /** Where the coating block is: the offhand, or a hotbar slot. */
    private record Material(int slot, boolean offhand) {
        static final Material NONE = new Material(-1, false);
        boolean found() { return offhand || slot >= 0; }
    }

    public BlockCoatModule() {
        super("BlockCoat", "Coats exposed terrain with blocks, with priority jump placement, scaffold strips and tunnel backfill.", ModuleCategory.PLAYER);
    }

    @Override public void onEnable() { reset(); }
    @Override public void onDisable() { reset(); }
    @Override public String getInfo() { return status; }

    /** Drop deferred work while another builder owns placement, but keep sent attempts for authoritative settlement. */
    private void scaffoldSuspension(boolean suspended) {
        if (scaffoldSuspended == suspended) return;
        scaffoldSuspended = suspended;
        tracker.discardUnsent();
        releaseSticky();
        clearPreview();
        jumpActive = jumpPlaced = false;
        takeoffCell = jumpPending = null;
        strip.clear(); criticalStrip.clear();
        candidates.clear(); searchQueue.clear(); hitCache.clear();
        dirty.set(true); reorder = true;
        pacer.reset();
        lastPosition = null;
        lastGround = mc.player != null && mc.player.onGround();
    }

    private void reset() {
        releaseSticky();
        scaffoldSuspended = false;
        heldItemPause.reset();
        pacer.reset(); packetBudget.reset(); sentPackets.set(0);
        plannedPackets = rotationsPlanned = corrections = 0; packetLimited = false;
        jumpActive = jumpPlaced = false; pauseUntil = 0;
        strip.clear(); criticalStrip.clear(); passageLane.clear(); passageClearance.clear();
        travel = new Vec3(0, 0, 1); passageHeading = Vec3.ZERO; laneHeading = new Vec3(0, 0, 1); hasTravel = false;
        tracker.clear(); visibilityRetry.clear();
        changed.clear(); searchQueue.clear(); hitCache.clear(); reorder = true;
        candidates.clear(); updates.clear();
        blasts.clear();
        redstoneSections.clear(); fireSections.clear();
        clearPreview();
        dirty.set(true); corrected.set(false);
        level = null; scanOrigin = null; lastPosition = null;
        takeoffCell = jumpPending = null;
        tick = 0; searching = false; rays = 0;
        coveredCount = planningCount = deferredCount = 0;
        rayLimited = false;
        status = "Starting";
    }

    private void clearPreview() { validPreview.clear(); invalidPreview.clear(); }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        // Receive callbacks run on the network thread. Only enqueue immutable data here.
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            corrected.set(true);
        } else if (event.getPacket() instanceof ClientboundBlockUpdatePacket packet) {
            enqueueUpdate(packet.getPos(), packet.getBlockState());
        } else if (event.getPacket() instanceof ClientboundSectionBlocksUpdatePacket packet) {
            packet.runUpdates(this::enqueueUpdate);
        }
    }

    private void enqueueUpdate(BlockPos pos, BlockState state) {
        PlacementTracker.Attempt<Block> attempt = tracker.attempt(pos);
        if (tracker.concerns(pos, attempt))
            updates.add(new Update(pos.immutable(), state.is(attempt == null ? activeBlock : attempt.block), state.canBeReplaced(), attempt));
    }

    // May run on the network thread.
    @EventHandler
    private void onSent(PacketEvent.PostSend event) { sentPackets.incrementAndGet(); }

    @EventHandler(priority = -1000)
    private void onUseBlock(MultiPlayerGameModeUseItemOnEvent event) {
        if (!event.isCancelled()) pauseItemUsed(event.getHand());
    }

    @EventHandler(priority = -1000)
    private void onUseItem(MultiPlayerGameModeUseItemEvent event) {
        if (!event.isCancelled()) pauseItemUsed(event.getHand());
    }

    @EventHandler(priority = -1000)
    private void onStartBreakingBlock(MultiPlayerGameModeStartDestroyBlockEvent event) {
        BlockPos pos = event.getPos();
        if (event.isCancelled() || !respectTntClearance.getValue() || activeBlock != Blocks.TNT
            || mc.player == null || mc.level == null || mc.options == null || !mc.options.keyAttack.isDown()
            || !(mc.hitResult instanceof BlockHitResult crosshair) || !crosshair.getBlockPos().equals(pos)
            || !loaded(pos) || !mc.level.getBlockState(pos).is(Blocks.TNT)
            || !likelyPassageOpening(pos)) return;
        passageClearance.begin(clearanceCell(pos), mc.player.blockPosition().getY(), tick);
        // A deliberate removal is not a placement rollback and must never inherit retry state.
        tracker.abandon(pos);
        hitCache.remove(pos);
        visibilityRetry.remove(pos);
        changed.add(pos.immutable());
    }

    @EventHandler
    private void onBlockChange(BlockStateChangeEvent event) {
        BlockPos pos = event.getPos();
        redstoneSections.clear(); fireSections.clear();
        if (event.getOldState().is(Blocks.TNT) && !event.getNewState().is(Blocks.TNT))
            passageClearance.removed(clearanceCell(pos), tick);
        BlockPos origin = scanOrigin;
        if (origin != null && PlacementGeometry.inCube(pos.getX(), pos.getY(), pos.getZ(),
            origin.getX(), origin.getY(), origin.getZ(), scannedRadius + 1)) changed.add(pos.immutable());
        // Sources can sit outside the coating cube, with shielding another blast radius beyond them.
        if (origin != null && PlacementGeometry.inCube(pos.getX(), pos.getY(), pos.getZ(),
            origin.getX(), origin.getY(), origin.getZ(), scannedRadius + 2 * TntBlastPlanner.RADIUS + 1)) blasts.markDirty();
    }

    @EventHandler
    private void onChunkData(ClientChunkCacheReplaceWithPacketDataEvent event) {
        // Full chunk replacement need not emit individual block updates. This event runs after it is applied.
        redstoneSections.clear(); fireSections.clear();
        BlockPos origin = scanOrigin;
        if (origin == null) return;
        ChunkPos chunk = event.getChunk().getPos();
        int margin = scannedRadius + 2 * TntBlastPlanner.RADIUS + 1;
        if (chunk.x() < ((origin.getX() - margin) >> 4) || chunk.x() > ((origin.getX() + margin) >> 4)
            || chunk.z() < ((origin.getZ() - margin) >> 4) || chunk.z() > ((origin.getZ() + margin) >> 4)) return;
        blasts.markDirty();
        if ((conservativeTnt.getValue() || liquidFiller.getValue()) && activeBlock == Blocks.TNT) dirty.set(true);
    }

    /**
     * The placement tick. Argus collects rotations and holds outgoing packets while this event is posted, so
     * each interaction here is sent after the rotation it asks for.
     */
    @EventHandler
    private void onTick(RotationRequestEvent event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) { reset(); return; }
        // Packets are applied before the tick starts, so section answers from the previous tick may be stale.
        redstoneSections.clear(); fireSections.clear();
        Block selected = selectBlock();
        if (activeBlock != selected) { reset(); activeBlock = selected; }
        if (activeConservativeTnt != conservativeTnt.getValue()) {
            activeConservativeTnt = conservativeTnt.getValue();
            tracker.discardUnsent();
            blasts.clear();
        }
        if (activeLiquidFiller != liquidFiller.getValue()) {
            activeLiquidFiller = liquidFiller.getValue();
            tracker.discardUnsent();
            dirty.set(true);
        }
        if (level != mc.level) {
            reset();
            level = mc.level;
            lastPosition = mc.player.position();
            lastGround = mc.player.onGround();
        }
        tick++;
        packetBudget.record(tick, sentPackets.getAndSet(0));
        plannedPackets = rotationsPlanned = 0; packetLimited = false;
        if (sticky.holding() && (autoSwitch.getValue() != AutoSwitch.STICKY || sticky.idle(tick))) releaseSticky();
        // Argus rotates server-side only, so the player's own yaw is still the direction they face.
        facingYaw = mc.player.getYRot();
        clearPreview();
        drainUpdates();
        expirePending();

        if (corrected.getAndSet(false)) {
            corrections++;
            candidates.clear();
            // Keep sent attempts tracked, but discard every deferred interaction.
            tracker.setback();
            pauseUntil = tick + correctionCooldown.getValue(); jumpActive = false;
            pacer.failed(tick, correctionCooldown.getValue());
            dirty.set(true);
            takeoffCell = null;
        }

        scaffoldSuspension(scaffoldActive());
        if (scaffoldSuspended) { status = "Scaffold"; return; }

        Vec3 position = mc.player.position();
        boolean grounded = mc.player.onGround();
        boolean flying = flying();
        Vec3 delta = lastPosition == null ? Vec3.ZERO : position.subtract(lastPosition);
        Vec3 intended = intendedMovement();
        if (delta.horizontalDistanceSqr() > .0001) {
            travel = new Vec3(delta.x, 0, delta.z).normalize();
            hasTravel = true;
        }
        passageHeading = intended.horizontalDistanceSqr() > .0001 ? intended
            : delta.horizontalDistanceSqr() > .0001 ? new Vec3(delta.x, 0, delta.z).normalize() : Vec3.ZERO;
        laneHeading = passageHeading.horizontalDistanceSqr() > .0001 ? passageHeading : facingDirection();
        rebuildPassageLane(position);
        passageClearance.observe(position.x, position.z, tick);
        if (!respectTntClearance.getValue()) passageClearance.clear();
        boolean started = smartJump.getValue() && lastGround && !grounded && mc.player.getDeltaMovement().y > .05
            && mc.player.input.keyPresses.jump() && !flying && !mc.player.isInWater();
        if (started) {
            tracker.discardUnsent();
            jumpActive = true; jumpPlaced = false; jumpPending = null;
        }
        if (grounded || flying || !smartJump.getValue()) jumpActive = false;
        lastGround = grounded; lastPosition = position;
        if (pauseForHeldItem()) {
            tracker.discardUnsent();
            releaseSticky();
            status = "Pause item";
            return;
        }
        if (tick < pauseUntil || mc.player.isInWater() || !mc.player.isAlive()) { status = "Paused"; return; }
        // A passenger sends no movement packets to carry a rotation, so rotated placement waits until you get off.
        if (rotating() && mc.player.isPassenger()) { tracker.discardUnsent(); status = "Riding"; return; }
        if (!flightPlacementAllowed()) { strip.clear(); criticalStrip.clear(); status = "Flying"; return; }
        // Retry while clear, including the descending half of the jump. Follow the player horizontally.
        if (jumpActive && !jumpPlaced) {
            takeoffCell = BlockPos.containing(position.x, mc.player.getBoundingBox().minY - 1, position.z);
            Validation under = validate(takeoffCell, true, null);
            if (attempt(takeoffCell, true, under)) { status = "Jump"; return; }
            if (jumpPending != null && tracker.isPending(jumpPending)) { status = "Jump"; return; }
        }
        strip.clear(); criticalStrip.clear();
        if (scaffold.getValue() && !flying && (grounded || jumpActive)) {
            BlockPos base = BlockPos.containing(position.x, mc.player.getBoundingBox().minY - 1, position.z);
            for (ModeGeometry.Cell cell : ModeGeometry.path(position.x, base.getY(), position.z, travel.x, travel.z, 1))
                criticalStrip.add(new BlockPos(cell.x(), cell.y(), cell.z()));
            for (ModeGeometry.Cell cell : ModeGeometry.path(position.x, base.getY(), position.z, travel.x, travel.z, scaffoldWidth.getValue()))
                strip.add(new BlockPos(cell.x(), cell.y(), cell.z()));
        }
        if (!findCoatingBlock().found()) { status = "No blocks"; return; }
        BlockPos origin = mc.player.blockPosition();
        boolean fullScan = dirty.getAndSet(false) || scannedRadius != radius.getValue()
            || scannedSurfaces != surfaces.getValue() || scannedStorageOnly != storageOnly.getValue() || tick % 40 == 0;
        if (fullScan || !origin.equals(scanOrigin)) scan(origin, !fullScan && scanOrigin != null);
        refreshChanges();

        if (reorder || queuedPriority != order.getValue() || tick % 5 == 0) {
            Comparator<BlockPos> sorter = Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(position));
            Comparator<BlockPos> coverageOrder = order.getValue() == Priority.NEAREST ? sorter : sorter.reversed();
            if (tunnel.getValue()) coverageOrder = Comparator.<BlockPos, Boolean>comparing(pos -> !tunnelTarget(pos)).thenComparing(coverageOrder);
            searchQueue.clear();
            searchQueue.addAll(PlacementOrder.nearFirst(candidates, coverageOrder.thenComparingLong(BlockPos::asLong)));
            reorder = false; queuedPriority = order.getValue();
        }
        int limit = perTick.getValue();
        if (paced()) limit = Math.min(limit, pacer.budget(tick, rateCap(), tracker.pendingCount(), window()));
        // Without extra rotation packets, the tick's one rotation rides the ordinary movement packet.
        if (rotating() && !extraRotations.getValue()) limit = Math.min(limit, 1);
        if (!packetBudget.allows(tick, PacketBudget.placementCost(0, false, false), packetLimit.getValue())) { limit = 0; packetLimited = true; }
        int attempts = 0;
        // Footpath always gets first use of the budget, including at one interaction per tick.
        for (BlockPos target : criticalStrip) {
            if (attempts >= limit) break;
            if (attempt(target, false, validate(target, false, null))) attempts++;
        }
        // Widen only after preparing the path; leave remaining capacity for surrounding coating.
        int wideningLimit = attempts + Math.max(0, (limit - attempts) / 2);
        for (BlockPos target : strip) {
            if (attempts >= wideningLimit) break;
            if (!criticalStrip.contains(target) && attempt(target, false, validate(target, false, null))) attempts++;
        }
        coveredCount = planningCount = deferredCount = 0;
        rays = 0; searching = true;
        int checks = Math.min(128, searchQueue.size());
        for (int i = 0; i < checks && attempts < limit && rays < 256; i++) {
            BlockPos target = searchQueue.removeFirst();
            searchQueue.addLast(target);
            if (tracker.isPending(target) || visibilityRetry.getOrDefault(target, 0) > tick) continue;
            Validation validation = validate(target, false, null);
            if (!validation.valid()) {
                if (validation.reason == Rejection.Reach) visibilityRetry.put(target, tick + 3);
                if (validation.reason == Rejection.Covered) coveredCount++;
                if (validation.reason == Rejection.Planning) planningCount++;
                if (validation.reason == Rejection.Deferred) deferredCount++;
                if (rejectedPreview.getValue() && invalidPreview.size() < 128) invalidPreview.add(target);
                continue;
            }
            if (validPreview.size() < 256) validPreview.add(target);
            if (attempts < limit && attempt(target, false, validation)) attempts++;
        }
        searching = false;
        // The module list has little room, so the status stays short: what is left to coat, and what is holding it up.
        status = packetLimited && attempts == 0 ? "Packet limit" : candidates.size() + " left, " + tracker.pendingCount() + " pending";
        if (conservativeTnt.getValue() && activeBlock == Blocks.TNT) {
            if (planningCount > 0) status += ", planning";
            else if (coveredCount > 0) status += ", " + coveredCount + " covered";
        }
        if (corrections > 0) status += ", " + corrections + " setbacks";
    }

    /** The listed block to coat with: the one already in use while it lasts, else the first found in the hotbar. */
    private Block selectBlock() {
        Set<Block> listed = blocks.getValue();
        if (listed.isEmpty()) return activeBlock;
        if (listed.contains(activeBlock) && holdsBlock(activeBlock)) return activeBlock;
        if (listedBlock(mc.player.getOffhandItem()) instanceof Block block) return block;
        if (listedBlock(mc.player.getMainHandItem()) instanceof Block block) return block;
        for (int slot = 0; slot < 9; slot++)
            if (listedBlock(mc.player.getInventory().getItem(slot)) instanceof Block block) return block;
        return listed.contains(activeBlock) ? activeBlock : listed.iterator().next();
    }

    private Block listedBlock(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock().asItem() == item
            && blocks.getValue().contains(item.getBlock()) ? item.getBlock() : null;
    }

    /** The block being coated with, or null while Block Coat is off. Other modules must not break it. */
    public Block coatingBlock() {
        return isToggled() ? activeBlock : null;
    }

    /**
     * True while Block Coat wants the selected hotbar slot: it is holding its sticky slot, or the coating block
     * is in the main hand. Other modules should not switch slots meanwhile, or the two switch back and forth.
     */
    public boolean holdsHotbar() {
        if (!isToggled() || mc.player == null) return false;
        if (autoSwitch.getValue() == AutoSwitch.SILENT) return false;
        return sticky.holding() || mc.player.getMainHandItem().is(activeBlock.asItem());
    }

    private boolean holdsBlock(Block block) {
        Item item = block.asItem();
        return mc.player.getOffhandItem().is(item) || InventoryUtils.findHotbarSlot(item) >= 0;
    }

    private Material findCoatingBlock() {
        if (pauseForHeldItem() || blocks.getValue().isEmpty()) return Material.NONE;
        Item item = activeBlock.asItem();
        if (item == Items.AIR) return Material.NONE;
        int selected = InventoryUtils.getSelectedSlot();
        if (mc.player.getOffhandItem().is(item)) return new Material(-1, true);
        if (mc.player.getMainHandItem().is(item)) return new Material(selected, false);
        if (autoSwitch.getValue() == AutoSwitch.DISABLED) return Material.NONE;
        int slot = InventoryUtils.findHotbarSlot(item);
        return slot >= 0 && slot < 9 ? new Material(slot, false) : Material.NONE;
    }

    private boolean holdingPauseItem() {
        return mc.player != null && ((!mc.player.getMainHandItem().isEmpty()
            && pauseItems.getValue().contains(mc.player.getMainHandItem().getItem()))
            || (!mc.player.getOffhandItem().isEmpty()
            && pauseItems.getValue().contains(mc.player.getOffhandItem().getItem())));
    }

    private boolean pauseForHeldItem() {
        if (mc.player != null && mc.player.isUsingItem()) pauseItemUsed(mc.player.getUsedItemHand());
        return heldItemPause.paused(holdingPauseItem(), System.nanoTime(), pauseItemsTimeout.getValue() * 1_000_000_000L);
    }

    private void pauseItemUsed(InteractionHand hand) {
        if (coatingInteraction || mc.player == null || mc.level == null) return;
        ItemStack stack = mc.player.getItemInHand(hand);
        heldItemPause.itemUsed(pauseItemsResetOnUse.getValue(), !stack.isEmpty() && pauseItems.getValue().contains(stack.getItem()), System.nanoTime());
    }

    /** Argus's own Scaffold owns placement while it is on; coating waits rather than competing for the same cells. */
    private boolean scaffoldActive() {
        if (scaffoldModule == null) scaffoldModule = ClientContext.getModule(ScaffoldModule.class);
        return scaffoldModule != null && scaffoldModule.isToggled();
    }

    private boolean flightPlacementAllowed() {
        return FlightPolicy.allowPlacement(flightCoating.getValue(), flying(), mc.player.onGround(), jumpActive);
    }

    private boolean flying() {
        if (flightModule == null) flightModule = ClientContext.getModule(FlightModule.class);
        return externalFlight.getValue() || mc.player.getAbilities().flying || mc.player.isFallFlying()
            || (flightModule != null && flightModule.isToggled());
    }

    private boolean scaffoldTarget(BlockPos target) {
        return scaffold.getValue() && !flying() && (mc.player.onGround() || jumpActive) && strip.contains(target);
    }

    private boolean tunnelTarget(BlockPos target) {
        Vec3 offset = Vec3.atCenterOf(target).subtract(mc.player.position());
        if (!tunnel.getValue()) return false;
        if (tunnelDirection.getValue() == TunnelDirection.MOVEMENT) {
            Vec3 heading = passageHeading.horizontalDistanceSqr() > .0001 ? passageHeading : hasTravel ? travel : Vec3.ZERO;
            if (heading.horizontalDistanceSqr() > .0001) return ModeGeometry.behind(offset.x, offset.z, heading.x, heading.z);
        }
        return ModeGeometry.behindFacing(offset.x, offset.z, facingYaw);
    }

    /** Convert physical movement keys into the horizontal world direction Minecraft will travel. */
    private Vec3 intendedMovement() {
        if (mc.player == null || mc.player.input == null) return Vec3.ZERO;
        Input keys = mc.player.input.keyPresses;
        double forward = (keys.forward() ? 1 : 0) - (keys.backward() ? 1 : 0);
        double lateral = (keys.left() ? 1 : 0) - (keys.right() ? 1 : 0);
        if (forward == 0 && lateral == 0) return Vec3.ZERO;
        double yaw = Math.toRadians(facingYaw);
        Vec3 movement = new Vec3(-Math.sin(yaw) * forward + Math.cos(yaw) * lateral, 0,
            Math.cos(yaw) * forward + Math.sin(yaw) * lateral);
        return movement.normalize();
    }

    private Vec3 facingDirection() {
        double yaw = Math.toRadians(facingYaw);
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    private void rebuildPassageLane(Vec3 position) {
        passageLane.clear();
        if (!preserveTightCorridors.getValue() || flying()) return;
        int feetY = mc.player.blockPosition().getY();
        for (PassageGeometry.Cell cell : PassageGeometry.lane(position.x, position.z,
            laneHeading.x, laneHeading.z, 2)) {
            passageLane.add(new BlockPos(cell.x(), feetY, cell.z()));
            passageLane.add(new BlockPos(cell.x(), feetY + 1, cell.z()));
        }
    }

    private boolean paced() { return serverMode.getValue() != ServerMode.FAST; }
    private int rateCap() { return Math.min(perTick.getValue(), serverMode.getValue() == ServerMode.CONSERVATIVE ? 1 : serverCap.getValue()); }
    private int window() { return serverMode.getValue() == ServerMode.CONSERVATIVE ? 2 : pendingCap.getValue(); }
    private boolean rotating() { return rotate.getValue(); }

    /**
     * Whether a rotated placement sends a rotation packet of its own. Silent rotations always do; otherwise
     * the tick's one rotation rides the movement packet unless extra rotations are allowed.
     */
    private boolean rotationPacket() {
        return rotating() && (RotationManager.isSilentRotationMode() || extraRotations.getValue());
    }

    /** Hotbar-switch packets a placement adds; none when the block is already held or in the offhand. */
    private int switchPackets(Material material) {
        if (material.offhand() || material.slot() == InventoryUtils.getSelectedSlot()) return 0;
        return switch (autoSwitch.getValue()) {
            case SILENT -> 2;
            case STICKY, NORMAL -> 1;
            case DISABLED -> 0;
        };
    }

    /** Return to the slot Sticky switched away from, unless the player has since picked another. */
    private void releaseSticky() {
        sticky.release(() -> !pauseForHeldItem());
    }

    private void drainUpdates() {
        Update update;
        while ((update = updates.poll()) != null) {
            blasts.markDirty();
            changed.add(update.pos);
            visibilityRetry.remove(update.pos);
            tracker.update(update.pos, update.matchesBlock, update.replaceable, update.attempt, tick, retryDelay.getValue());
        }
    }

    private void expirePending() {
        int timeout = paced() ? pacer.timeout(confirmTimeout.getValue()) : confirmTimeout.getValue();
        tracker.expire(tick, timeout, retryDelay.getValue(), this::inRadius);
        visibilityRetry.entrySet().removeIf(e -> tick >= e.getValue() || !inRadius(e.getKey()));
    }

    private void refreshChanges() {
        Set<BlockPos> affected = new HashSet<>();
        for (BlockPos pos : changed) {
            if (!changed.remove(pos)) continue;
            affected.add(pos);
            for (Direction side : Direction.values()) affected.add(pos.relative(side));
        }
        for (BlockPos pos : affected) {
            hitCache.remove(pos); visibilityRetry.remove(pos);
            boolean eligible = inRadius(pos) && candidateTarget(pos);
            boolean modified = eligible ? candidates.add(pos) : candidates.remove(pos);
            if (modified) reorder = true;
        }
    }

    private boolean terrain(BlockPos pos) {
        if (!loaded(pos) || mc.level.isOutsideBuildHeight(pos)) return false;
        BlockState state = mc.level.getBlockState(pos);
        return CoatingRules.surface(state.is(Blocks.TNT) || state.is(activeBlock), !state.getFluidState().isEmpty(), state.canBeReplaced(),
            !state.getCollisionShape(mc.level, pos).isEmpty(), state.getBlock().getExplosionResistance(),
            state.getDestroySpeed(mc.level, pos));
    }

    /** Item storage blocks; ender chests are excluded since coating them protects nothing. */
    private static boolean storage(BlockState state) {
        Block block = state.getBlock();
        return block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock
            || block instanceof HopperBlock || block instanceof DispenserBlock || block instanceof AbstractFurnaceBlock
            || block instanceof BrewingStandBlock || block instanceof CrafterBlock || block instanceof DecoratedPotBlock;
    }

    private boolean enabledFace(Direction face) {
        return switch (surfaces.getValue()) {
            case ALL -> true;
            case FLOORS -> face == Direction.UP;
            case CEILINGS -> face == Direction.DOWN;
            case WALLS -> face.getAxis().isHorizontal();
        };
    }

    private boolean inRadius(BlockPos pos) {
        BlockPos origin = mc.player.blockPosition();
        return PlacementGeometry.inCube(pos.getX(), pos.getY(), pos.getZ(), origin.getX(), origin.getY(), origin.getZ(), radius.getValue());
    }

    private void scan(BlockPos origin, boolean incremental) {
        BlockPos previousOrigin = scanOrigin;
        if (!incremental) candidates.clear();
        else candidates.removeIf(pos -> !inRadius(pos));
        visibilityRetry.clear();
        reorder = true;
        scanOrigin = origin.immutable(); scannedRadius = radius.getValue(); scannedSurfaces = surfaces.getValue();
        scannedStorageOnly = storageOnly.getValue();
        int r = radius.getValue();
        // Scan targets rather than surfaces, so a terrain face just outside the cube still coats its boundary cell.
        for (BlockPos mutable : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            if (incremental && PlacementGeometry.inCube(mutable.getX(), mutable.getY(), mutable.getZ(),
                previousOrigin.getX(), previousOrigin.getY(), previousOrigin.getZ(), r)) continue;
            if (candidateTarget(mutable)) candidates.add(mutable.immutable());
        }
        hitCache.keySet().retainAll(candidates);
        // A predicted TNT ghost remains untrusted until an authoritative update or chunk unload,
        // even after leaving the coating cube or disabling the saving toggle.
        tracker.pruneUnconfirmed(pos -> inRadius(pos) || (activeBlock == Blocks.TNT && loaded(pos)));
    }

    private boolean replaceable(BlockPos pos) {
        if (pos == null || mc.level.isOutsideBuildHeight(pos) || !loaded(pos)
            || !mc.level.getWorldBorder().isWithinBounds(pos)) return false;
        BlockState state = mc.level.getBlockState(pos);
        return !state.is(activeBlock) && state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    private boolean liquidTarget(BlockPos pos) {
        return liquidFiller.getValue() && activeBlock == Blocks.TNT && pos != null
            && mc.level.getWorldBorder().isWithinBounds(pos)
            && LiquidFiller.isolatedWaterSource(pos, mc.level::isOutsideBuildHeight,
                // A predicted TNT ghost can hide a source still present on the server.
                neighbour -> loaded(neighbour) && !tracker.unconfirmed(neighbour) ? mc.level.getBlockState(neighbour) : null);
    }

    private boolean candidateTarget(BlockPos pos) {
        return liquidTarget(pos) || (replaceable(pos) && hasTerrainFace(pos));
    }

    private boolean hasTerrainFace(BlockPos target) {
        for (Direction side : Direction.values()) {
            BlockPos neighbour = target.relative(side);
            if (enabledFace(side.getOpposite()) && terrain(neighbour)
                && (!storageOnly.getValue() || storage(mc.level.getBlockState(neighbour)))) return true;
        }
        return false;
    }

    private boolean touchesWall(BlockPos target) {
        for (Direction side : Direction.values()) {
            if (side.getAxis().isHorizontal() && terrain(target.relative(side))) return true;
        }
        return false;
    }

    private static PassageClearance.Cell clearanceCell(BlockPos pos) {
        return new PassageClearance.Cell(pos.getX(), pos.getY(), pos.getZ());
    }

    private boolean passageProtected(BlockPos target) {
        if (respectTntClearance.getValue() && activeBlock == Blocks.TNT
            && passageClearance.protects(clearanceCell(target))) return true;
        return preserveTightCorridors.getValue() && passageLane.contains(target)
            && tightCorridorAt(target, target, false, laneHeading);
    }

    /** A mined TNT block is route intent only when it opens a body-height cell in a narrow passage. */
    private boolean likelyPassageOpening(BlockPos target) {
        int feetY = mc.player.blockPosition().getY();
        if (target.getY() != feetY && target.getY() != feetY + 1) return false;
        Vec3 toward = Vec3.atCenterOf(target).subtract(mc.player.position()).multiply(1, 0, 1);
        if (toward.horizontalDistanceSqr() < .01 || toward.horizontalDistanceSqr() > 36) return false;
        toward = toward.normalize();
        Vec3 look = mc.player.getLookAngle().multiply(1, 0, 1);
        if (look.horizontalDistanceSqr() < .0001 || look.normalize().dot(toward) < .55) return false;
        return tightCorridorAt(target, target, true, toward);
    }

    private boolean tightCorridorAt(BlockPos centre, BlockPos forcedOpen, boolean forceWholeColumn, Vec3 heading) {
        if (heading.horizontalDistanceSqr() < .0001) return false;
        boolean alongX = Math.abs(heading.x) >= Math.abs(heading.z);
        int lateralX = alongX ? 0 : 1;
        int lateralZ = alongX ? 1 : 0;
        int feetY = mc.player.blockPosition().getY();
        return PassageGeometry.narrowCorridor(offset -> bodyColumnOpen(
            centre.getX() + lateralX * offset, centre.getZ() + lateralZ * offset,
            feetY, forcedOpen, forceWholeColumn), 2);
    }

    private boolean bodyColumnOpen(int x, int z, int feetY, BlockPos forcedOpen, boolean forceWholeColumn) {
        for (int y = feetY; y <= feetY + 1; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (forcedOpen != null && forcedOpen.getX() == x && forcedOpen.getZ() == z
                && (forceWholeColumn || forcedOpen.getY() == y)) continue;
            if (!loaded(pos) || mc.level.isOutsideBuildHeight(pos)
                || !mc.level.getWorldBorder().isWithinBounds(pos)
                || !mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty()) return false;
        }
        return true;
    }

    /** Filler can also use blast-resistant supports; ordinary coating still needs eligible terrain. */
    private boolean usableSupport(BlockPos pos, BlockState state, boolean liquid) {
        return loaded(pos) && !mc.level.isOutsideBuildHeight(pos)
            && mc.level.getWorldBorder().isWithinBounds(pos)
            && !state.canBeReplaced() && state.getFluidState().isEmpty()
            && !state.getCollisionShape(mc.level, pos).isEmpty()
            && (containerSupports.getValue() || !BlockCoatInteraction.clickable(state))
            && (liquid || terrain(pos) || state.is(activeBlock));
    }

    private BlockHitResult supportHit(BlockPos target, boolean liquid) {
        Vec3 eye = mc.player.getEyePosition();
        List<Direction> sides = new ArrayList<>(List.of(Direction.values()));
        // Ordinary faces first: a container face costs two extra sneak-input packets.
        sides.sort(Comparator.comparing((Direction side) -> BlockCoatInteraction.clickable(mc.level.getBlockState(target.relative(side))))
            .thenComparingDouble(side -> Vec3.atCenterOf(target.relative(side)).distanceToSqr(eye)));
        for (Direction side : sides) {
            BlockPos support = target.relative(side);
            if (!loaded(support) || (paced() && tracker.unconfirmed(support))) continue;
            BlockState state = mc.level.getBlockState(support);
            if (!usableSupport(support, state, liquid)) continue;
            Direction face = side.getOpposite();
            int axis = face.getAxis() == Direction.Axis.X ? 0 : face.getAxis() == Direction.Axis.Y ? 1 : 2;
            for (AABB localBox : state.getCollisionShape(mc.level, support).toAabbs()) {
                AABB box = localBox.move(support.getX(), support.getY(), support.getZ());
                var bounds = new PlacementGeometry.Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
                for (var sample : PlacementGeometry.faceSamples(bounds, axis, face.getAxisDirection().getStep(),
                    new PlacementGeometry.Point(eye.x, eye.y, eye.z), narrowGaps.getValue())) {
                    BlockHitResult hit = new BlockHitResult(new Vec3(sample.x(), sample.y(), sample.z()), face, support, false);
                    if (searching && rays >= 256) { rayLimited = true; return null; }
                    if (visibleHit(target, hit, liquid)) return hit;
                }
            }
        }
        return null;
    }

    private boolean visibleHit(BlockPos target, BlockHitResult hit, boolean liquid) {
        if (paced() && tracker.unconfirmed(hit.getBlockPos())) return false;
        if (!hit.getBlockPos().relative(hit.getDirection()).equals(target)) return false;
        BlockState support = mc.level.getBlockState(hit.getBlockPos());
        if (!usableSupport(hit.getBlockPos(), support, liquid)) return false;
        Vec3 eye = mc.player.getEyePosition();
        double maxReach = Math.min(reach.getValue(), mc.player.blockInteractionRange()) - (paced() ? .15 : 0);
        if (eye.distanceToSqr(hit.getLocation()) > maxReach * maxReach) return false;
        Direction face = hit.getDirection();
        Vec3 inside = hit.getLocation().add(-face.getStepX() * .001, -face.getStepY() * .001, -face.getStepZ() * .001);
        if (searching) {
            if (rays >= 256) { rayLimited = true; return false; }
            rays++;
        }
        BlockHitResult ray = mc.level.clip(new ClipContext(eye, inside, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return ray.getType() == HitResult.Type.BLOCK && ray.getBlockPos().equals(hit.getBlockPos())
            && ray.getDirection() == face && ray.getLocation().distanceToSqr(hit.getLocation()) < .0004;
    }

    private boolean loaded(BlockPos pos) { return mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4); }

    /** A section's palette tells whether it can hold a redstone component, without reading its blocks. */
    private TntRedstoneSafety.Section redstoneSection(int sectionX, int sectionY, int sectionZ) {
        LevelChunk chunk = mc.level.getChunkSource().getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
        if (chunk == null) return TntRedstoneSafety.Section.UNKNOWN;
        int index = chunk.getSectionIndexFromSectionY(sectionY);
        // Outside the build height: those positions are skipped before the section matters.
        if (index < 0 || index >= chunk.getSectionsCount()) return TntRedstoneSafety.Section.CLEAR;
        LevelChunkSection section = chunk.getSection(index);
        return section.hasOnlyAir() || !section.maybeHas(TntRedstoneSafety::component)
            ? TntRedstoneSafety.Section.CLEAR : TntRedstoneSafety.Section.MAYBE;
    }

    /** A section's palette tells whether it can hold fire or lava, without reading its blocks. */
    private TntRedstoneSafety.Section fireSection(int sectionX, int sectionY, int sectionZ) {
        LevelChunk chunk = mc.level.getChunkSource().getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
        if (chunk == null) return TntRedstoneSafety.Section.UNKNOWN;
        int index = chunk.getSectionIndexFromSectionY(sectionY);
        if (index < 0 || index >= chunk.getSectionsCount()) return TntRedstoneSafety.Section.CLEAR;
        LevelChunkSection section = chunk.getSection(index);
        return section.hasOnlyAir() || !section.maybeHas(TntFireSafety::hazard)
            ? TntRedstoneSafety.Section.CLEAR : TntRedstoneSafety.Section.MAYBE;
    }

    /** The target must be free of entities that would block the coating block, as the server will check. */
    private boolean unobstructed(BlockPos target) {
        return mc.level.isUnobstructed(activeBlock.defaultBlockState(), target, CollisionContext.empty());
    }

    private Validation reject(Rejection reason) { return new Validation(reason, null, false); }

    private Validation validate(BlockPos target, boolean underfoot, BlockHitResult preferred) {
        if (pauseForHeldItem() || !flightPlacementAllowed()) return reject(Rejection.Protected);
        if (!CoatingRules.tunnelAllows(tunnel.getValue(), underfoot, tunnelTarget(target))) return reject(Rejection.Protected);
        boolean liquid = !underfoot && liquidTarget(target);
        if ((!liquid && !replaceable(target)) || !inRadius(target)) return reject(Rejection.Occupied);
        if (!underfoot && passageProtected(target)) return reject(Rejection.Passage);
        if (tracker.retryBlocked(target, tick)) return reject(Rejection.Retry);
        // Unpowered circuits can react to placement.
        if (!TntRedstoneSafety.canPlace(activeBlock == Blocks.TNT, target, mc.level::isOutsideBuildHeight,
            pos -> loaded(pos) ? mc.level.getBlockState(pos) : null, mc.level::hasNeighborSignal, redstoneSections))
            return reject(Rejection.Redstone);
        if (fireSafety.getValue() && !TntFireSafety.canPlace(activeBlock == Blocks.TNT, target, mc.level::isOutsideBuildHeight,
            pos -> loaded(pos) ? mc.level.getBlockState(pos) : null, fireSections))
            return reject(Rejection.Fire);
        boolean platform = !underfoot && scaffoldTarget(target);
        if (!underfoot && !platform && !liquid && !hasTerrainFace(target)) return reject(Rejection.NoTerrain);
        AABB block = new AABB(target);
        if (underfoot) {
            if (!jumpActive || jumpPlaced || flying() || mc.player.getBoundingBox().minY < target.getY() + 1.0
                || !mc.player.getBoundingBox().inflate(0.03, 0, 0.03).move(0, -1, 0).intersects(block)
                || mc.player.getBoundingBox().intersects(block)) return reject(Rejection.Protected);
        } else {
            int feetY = mc.player.blockPosition().getY();
            if (!platform && !liquid && !tunnelTarget(target) && (target.getY() == feetY || target.getY() == feetY + 1)
                && !CoatingRules.heightAllowed(target.getY(), feetY, touchesWall(target))) return reject(Rejection.Height);
            if (mc.player.getBoundingBox().inflate(0.08, 0, 0.08).intersects(block)) return reject(Rejection.Protected);
        }
        if (!unobstructed(target)) return reject(Rejection.Entity);
        if (conservativeTnt.getValue() && activeBlock == Blocks.TNT && !underfoot && !platform && !liquid) {
            if (blasts.covered(target, tick, radius.getValue(), tracker, blastTerrain)) return reject(Rejection.Covered);
            // Incomplete coverage must defer placement rather than spend TNT on an unknown result.
            if (blasts.limited()) return reject(Rejection.Planning);
        }
        BlockHitResult hit;
        rayLimited = false;
        if (preferred != null) hit = visibleHit(target, preferred, liquid) ? preferred : null;
        else {
            BlockHitResult cached = hitCache.get(target);
            // Search again past a cached container face in case an ordinary face is now usable.
            hit = cached != null && !BlockCoatInteraction.clickable(cached) && visibleHit(target, cached, liquid) ? cached : supportHit(target, liquid);
            if (hit != null) hitCache.put(target, hit); else hitCache.remove(target);
        }
        return hit == null ? reject(rayLimited ? Rejection.Deferred : Rejection.Reach) : new Validation(Rejection.None, hit, liquid);
    }

    /** Places on a target that was validated against the live world this tick. */
    private boolean attempt(BlockPos target, boolean underfoot, Validation validation) {
        if (pauseForHeldItem() || tracker.isPending(target) || !validation.valid()) return false;
        // The jump support comes first: nothing else is placed until it has gone out.
        if (!underfoot && jumpActive && !jumpPlaced) return false;
        Material material = findCoatingBlock();
        if (!material.found()) return false;
        // Keep one outstanding slot available for the footpath when coating alongside scaffold.
        int reservationWindow = scaffold.getValue() && !flying() && !underfoot && !scaffoldTarget(target)
            ? Math.max(1, window() - 1) : window();
        if (paced() && pacer.budget(tick, rateCap(), tracker.pendingCount(), reservationWindow) <= 0) return false;
        boolean rotated = rotating();
        if (rotated && rotationsPlanned > 0 && !extraRotations.getValue()) return false;
        BlockHitResult hit = validation.hit;
        int cost = PacketBudget.placementCost(switchPackets(material), rotationPacket(), BlockCoatInteraction.clickable(hit));
        if (!packetBudget.allows(tick, plannedPackets + cost, packetLimit.getValue())) { packetLimited = true; return false; }
        plannedPackets += cost;
        if (rotated) rotationsPlanned++;
        PlacementTracker.Attempt<Block> reservation = tracker.reserve(target, tick, activeBlock);
        if (underfoot) { jumpPending = target; jumpPlaced = true; }
        pacer.sent(tick);

        float[] angles = rotated ? RotationManager.calculateRotations(mc.player.getEyePosition(), hit.getLocation()) : null;
        // An extra rotation is its own packet and has to go out before the interaction it aims.
        boolean ownRotation = rotated && extraRotations.getValue() && !RotationManager.isSilentRotationMode();
        if (ownRotation) sendRotation(angles);

        boolean offhand = material.offhand();
        AutoSwitch mode = autoSwitch.getValue();
        int previousSlot = InventoryUtils.getSelectedSlot();
        boolean swapBack = !offhand && mode == AutoSwitch.SILENT && material.slot() != previousSlot;
        if (!offhand && mode == AutoSwitch.STICKY) sticky.hold(material.slot(), tick);
        else if (!offhand && mode != AutoSwitch.DISABLED && material.slot() != previousSlot) InventoryUtils.selectHotbarSlot(material.slot());
        try {
            tracker.markSent(target, reservation, tick);
            coatingInteraction = true;
            try {
                BlockCoatInteraction.use(hit, offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, containerSupports.getValue());
            } finally {
                coatingInteraction = false;
            }
        } finally {
            if (swapBack) InventoryUtils.selectHotbarSlot(previousSlot);
        }
        // Argus holds the packets above until this rotation has been sent.
        if (rotated && !ownRotation)
            ClientContext.rotationManager.requestRotation(angles, underfoot ? 100 : criticalStrip.contains(target) ? 80 : 20);
        return true;
    }

    private void sendRotation(float[] angles) {
        float[] packetAngles = RotationManager.unwrapYaw(angles);
        ClientContext.rotationManager.updateRenderRotations(angles);
        PacketSender.sendPositionRotation(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
            packetAngles[0], packetAngles[1], ClientContext.playerStateTracker.isPacketOnGround());
    }

    @EventHandler
    private void onRender(WorldRenderEvent.Main event) {
        if (!preview.getValue() || mc.player == null || mc.level != level || corrected.get()) return;
        PoseStack poses = event.getPoseStack();
        for (BlockPos pos : validPreview) box(poses, pos, PREVIEW_FILL, PREVIEW_LINE);
        for (BlockPos pos : tracker.pendingPositions()) box(poses, pos, PENDING_FILL, PENDING_LINE);
        if (rejectedPreview.getValue()) for (BlockPos pos : invalidPreview) box(poses, pos, REJECTED_FILL, REJECTED_LINE);
    }

    private static void box(PoseStack poses, BlockPos pos, Color fill, Color line) {
        WorldBoxRenderer.fillBox(poses, pos, fill);
        WorldBoxRenderer.outlineBox(poses, pos, line, 1.0F);
    }
}
