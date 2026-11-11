package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.InputPressEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.MinecraftTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.hud.BoatSpeedHudModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.KeyBindSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.KeyUtils;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.mixin.KeyMappingAccessor;
import dev.argus.mixin.ServerboundInteractPacketAccessor;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Flies a boat through blocks. WASD steers, jump rises, sprint descends, sneak dismounts.
 * Ported from Quiettee Utils' Boat Phase; the Boat Shot hooks are left out because Argus has no Boat Shot.
 */
public final class BoatPhaseModule extends Module {
    private static final int PRIORITY_FIRST = 1000;
    private static final int PRIORITY_LATE = -1000;

    private final Minecraft mc = ClientAccess.argusclientMinecraft;

    // Setting names are the original's (Meteor kebab-case written in Argus CamelCase); groups follow its order.
    // General
    private final Setting<Boolean> generalGroup = add(new BooleanSetting("General", true).markAsGroup());
    private final Setting<Boolean> phase = add(new BooleanSetting("Phase", true).dependsOn(generalGroup));
    private final Setting<Double> horizontalSpeed = add(new NumberSetting<>("HorizontalSpeed", 2.8, 0.0, 9.0).dependsOn(generalGroup));
    private final Setting<Double> horizontalLimit = add(new NumberSetting<>("HorizontalLimit", 0.99, 0.01, 9.0).withSliderMaximum(3).dependsOn(generalGroup));
    private final Setting<Boolean> fastAir = add(new BooleanSetting("FastAirTravel", false).dependsOn(generalGroup));
    private final Setting<Boolean> hashTravel = add(new BooleanSetting("CorrectionHashTravel", false).dependsOn(generalGroup));
    private final Setting<Double> hashSpeed = add(new NumberSetting<>("HashTravelSpeed", 3.2, 0.1, 3.8).dependsOn(generalGroup, hashTravel));
    private final Setting<Boolean> airSubsteps = add(new BooleanSetting("AirSubsteps", true).dependsOn(generalGroup));
    private final Setting<Double> substepSpeed = add(new NumberSetting<>("SubstepSpeed", 3.2, 0.1, BoatPhaseAirSteps.MAX_SPEED).dependsOn(generalGroup, airSubsteps));
    private final Setting<Boolean> extendedSubsteps = add(new BooleanSetting("ExtendedSubsteps", true).dependsOn(generalGroup, airSubsteps));
    private final Setting<Double> experimentalSpeed = add(new NumberSetting<>("ExperimentalSpeed", 6.5, 3.8, BoatPhaseAirSteps.TRIAL_SPEED)
        .dependsOn(generalGroup, airSubsteps, extendedSubsteps));
    private final Setting<Double> verticalSpeed = add(new NumberSetting<>("VerticalSpeed", 3.0, 0.1, BoatPhaseVertical.MAX_SPEED).withSliderMaximum(BoatPhaseVertical.MAX_SPEED).dependsOn(generalGroup));
    private final Setting<Double> phaseHorizontalSpeed = add(new NumberSetting<>("PhaseHorizontalSpeed", 0.24, 0.01, BoatPhaseMotion.MAX_PHASE_HORIZONTAL).dependsOn(generalGroup, phase));
    private final Setting<Boolean> adaptivePhase = add(new BooleanSetting("AdaptivePhase", true).dependsOn(generalGroup, phase));
    private final Setting<Double> phaseSpeed = add(new NumberSetting<>("PhaseSpeed", 3.0, 0.24, 9.0).withSliderMaximum(3).dependsOn(generalGroup, phase, adaptivePhase));
    private final Setting<Double> airAcceleration = add(new NumberSetting<>("AirAcceleration", 0.1, 0.01, 1.0).withSliderMaximum(0.3).dependsOn(generalGroup));
    private final Setting<Boolean> lockYaw = add(new BooleanSetting("LockBoatYaw", true).dependsOn(generalGroup));
    private final Setting<Boolean> stopLava = add(new BooleanSetting("StopBeforeLava", true).dependsOn(generalGroup));
    private final Setting<Boolean> antiKick = add(new BooleanSetting("AntiKick", true).dependsOn(generalGroup));

    // Quick dive
    private final Setting<Boolean> diveGroup = add(new BooleanSetting("QuickDive", true).markAsGroup());
    private final KeyBindSetting diveKey = add(new KeyBindSetting("DiveKey", "Not bound", v -> diveGroup.isExpanded()));
    private final Setting<Double> diveDistance = add(new NumberSetting<>("DiveDistance", 30.0, 1.0, BoatPhaseMotion.MAX_TRAVEL).dependsOn(diveGroup));
    private final Setting<Boolean> diveWithPassenger = add(new BooleanSetting("DiveWithPassenger", false).dependsOn(diveGroup));

    // Recovery
    private final Setting<Boolean> recoveryGroup = add(new BooleanSetting("Recovery", true).markAsGroup());
    private final Setting<Integer> correctionHold = add(new NumberSetting<>("CorrectionHold", 8, 1, 100).withSliderMaximum(40).dependsOn(recoveryGroup));
    private final Setting<Boolean> landedRetry = add(new BooleanSetting("RetryAfterLanding", true).dependsOn(recoveryGroup));
    private final Setting<Boolean> remountRetry = add(new BooleanSetting("RetryAfterRemount", true).dependsOn(recoveryGroup));
    private final Setting<Double> landedRetrySpeed = add(new NumberSetting<>("LandedRetrySpeed", 3.8, 0.1, BoatPhaseAirSteps.MAX_SPEED).dependsOn(recoveryGroup, landedRetry));
    private final Setting<Boolean> pauseOthers = add(new BooleanSetting("PauseConflictingModules", true).dependsOn(recoveryGroup));
    private final Setting<Boolean> debugFile = add(new BooleanSetting("DebugFile", true).dependsOn(recoveryGroup));

    // Navigation
    private final Setting<Boolean> navigationGroup = add(new BooleanSetting("Navigation", true).markAsGroup());
    private final KeyBindSetting cruiseKey = add(new KeyBindSetting("CruiseKey", "Not bound", v -> navigationGroup.isExpanded()));
    private final KeyBindSetting surfaceKey = add(new KeyBindSetting("SurfaceKey", "Not bound", v -> navigationGroup.isExpanded()));
    private final KeyBindSetting returnHeightKey = add(new KeyBindSetting("ReturnHeightKey", "Not bound", v -> navigationGroup.isExpanded()));

    // Speedometer
    // The speedometer itself is the Boat Speed HUD element (placed, scaled and coloured in the HUD editor).
    private final Setting<Boolean> meterGroup = add(new BooleanSetting("Speedometer", true).markAsGroup());
    private final KeyBindSetting meterKey = add(new KeyBindSetting("SpeedometerKey", "Not bound", v -> meterGroup.isExpanded()));

    private final BoatPhaseMotion motion = new BoatPhaseMotion();
    private final BoatPhaseEventGate eventGate = new BoatPhaseEventGate();
    private final BoatPhaseTravel travel = new BoatPhaseTravel();
    private final BoatPhaseMeter meter = new BoatPhaseMeter();
    private final BoatPhaseRemount remount = new BoatPhaseRemount();
    private BoatPhaseTravel.Mode plannedMode = BoatPhaseTravel.Mode.LIMITED;
    private Connection travelConnection;
    private ClientLevel travelWorld;
    private ClientLevel hashWorld;
    private Connection hashConnection;
    private Integer correctionHash;
    private int plannedHashTick = -1;
    private Object lastPlayerCorrection, lastVehicleCorrection, lastInputPacket, lastObservationPacket;
    private Object lastBoardInteraction;
    private int lastGroundedAge = -1000;
    private String boardingRetryReason = "no grounded boarding interaction";
    private String lastModeDescription = "";
    private final List<Module> paused = new ArrayList<>();
    private final ConcurrentLinkedQueue<Observation> observations = new ConcurrentLinkedQueue<>();
    private final AtomicInteger observationCount = new AtomicInteger();
    private volatile long epoch;
    private volatile Connection connection;
    private LocalPlayer player;
    private ClientLevel world;
    private ClientPacketListener network;
    private AbstractBoat boat;
    private boolean driver;
    private boolean correcting;
    private boolean hadGuest;
    private Vec3 lastWire;
    private Vec3 plannedFrom;
    private int plannedTick = -1;
    private int sentCount;
    private int corrections;
    private double requestedDiveDistance;
    private String travelName = "Quick dive";
    private boolean plannedThroughBlocks;
    private boolean plannedClear;
    private boolean verticalRejected;
    private int lastFastVerticalTick = -100;
    private boolean adaptiveBlocked;
    private int lastAdaptiveTick = -100;
    private int lastSneakTick = -100;
    private boolean sentSneak;
    private int airborneTicks;
    private boolean cruising;
    private float cruiseYaw;
    private double entryHeight;
    private Vec3 lastStep = Vec3.ZERO;
    private ServerboundMoveVehiclePacket substepPacket;
    private boolean splitting;
    private Vec3 substepOrigin;
    private String status = "board a boat";
    private PrintWriter log;
    private long rideStartedNanos;

    private record Observation(long epoch, Connection connection, Packet<?> packet) {}

    public BoatPhaseModule() {
        super("BoatPhase", "Fly a boat through blocks. WASD, jump and sprint.", ModuleCategory.MOVEMENT, "Ferryman");
        experimentalSpeed.setChangeCallback(this::retryExtension);
    }

    /** The module when it is switched on, otherwise null. Used by the boat mixins. */
    public static BoatPhaseModule active() {
        BoatPhaseModule module = ClientContext.getModule(BoatPhaseModule.class);
        return module != null && module.isToggled() ? module : null;
    }

    // ---- Chat helpers ------------------------------------------------------------------------

    private void info(String format, Object... args) {
        ChatUtils.printChat(Component.literal(String.format(Locale.ROOT, format, args)), ChatUtils.moduleSignature(this), ChatUtils.MessagePriority.LOW);
    }

    private void warning(String format, Object... args) {
        ChatUtils.printChat(Component.literal(String.format(Locale.ROOT, format, args)), ChatUtils.moduleSignature(this), ChatUtils.MessagePriority.MID);
    }

    // ---- Lifecycle ---------------------------------------------------------------------------

    @Override
    public void onEnable() {
        detach();
        correctionHash = null; hashWorld = null; hashConnection = null;
        travel.retry();
        verticalRejected = false;
        remount.reset();
        eventGate.reset();
        adaptiveBlocked = false;
        status = "board a boat";
    }

    @Override
    public void onDisable() { detach(); }

    @EventHandler
    private void onDisconnect(MinecraftDisconnectEvent event) {
        detach();
        travel.retry(); travelConnection = null;
        verticalRejected = false;
        remount.reset();
        correctionHash = null; hashWorld = null; hashConnection = null;
    }

    private boolean sameSession() {
        return player == mc.player && world == mc.level && network == mc.getConnection();
    }

    private boolean owns(AbstractBoat candidate) {
        return boat == candidate && driver && sameSession() && mc.player != null && mc.player.isAlive()
            && !candidate.isRemoved() && mc.player.getRootVehicle() == candidate && candidate.getControllingPassenger() == mc.player;
    }

    /** True while the boat should ignore block collisions. */
    public boolean phases(AbstractBoat candidate) {
        return owns(candidate) && phase.getValue() && plannedThroughBlocks && !correcting && motion.holdTicks() == 0 && mc.gui.screen() == null;
    }

    // ---- Movement ----------------------------------------------------------------------------

    public Vec3 movement(AbstractBoat candidate, Vec3 vanilla) {
        if (!owns(candidate)) return vanilla;
        plannedMode = travelMode();
        if (lockYaw.getValue()) candidate.setYRot(mc.player.getYRot());
        if (correcting || motion.holdTicks() > 0 || mc.gui.screen() != null) {
            candidate.setDeltaMovement(Vec3.ZERO);
            return Vec3.ZERO;
        }
        Vec3 from = candidate.position();
        double forward = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        double sideways = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        if (forward != 0 || sideways != 0) cruising = false;
        float yaw = mc.player.getYRot();
        if (cruising) { forward = 1; yaw = cruiseYaw; }
        boolean up = mc.options.keyJump.isDown(), down = sprintKeyDown();
        double floor = world.getMinY() + 2;
        double topOffset = candidate.getSelfAndPassengers().mapToDouble(e -> e.getBoundingBox().maxY - candidate.getY()).max().orElse(candidate.getBbHeight());
        double ceiling = world.getMaxY() - topOffset;
        double airLimit = travelSpeed();
        double vSpeed = travelVerticalSpeed();
        double airSpeed = airLimit;
        double solidSpeed = Math.min(phaseSpeed.getValue(), horizontalLimit.getValue());
        boolean adaptive = phase.getValue() && adaptivePhase.getValue() && !adaptiveBlocked;
        BoatPhaseMotion.Point step = motion.plan(point(from), forward, sideways, up, down, yaw,
            adaptive ? Math.max(airSpeed, solidSpeed) : airSpeed, vSpeed, floor, ceiling, false);
        Vec3 delta = new Vec3(step.x(), step.y(), step.z());
        boolean throughBlocks = phase.getValue() && !world.noCollision(candidate, candidate.getBoundingBox().expandTowards(delta).deflate(1e-7));
        boolean embedded = !world.noCollision(candidate, candidate.getBoundingBox().deflate(1e-7));
        if (!embedded && !supported(candidate) && !candidate.isInWater()) airborneTicks++;
        else airborneTicks = 0;
        if (throughBlocks) {
            if (adaptive) {
                step = motion.plan(point(from), forward, sideways, up, down, yaw, solidSpeed, vSpeed, floor, ceiling, false);
                step = BoatPhaseMotion.collisionStep(step, phaseHorizontalSpeed.getValue(), p -> {
                    Vec3 desired = new Vec3(p.x(), p.y(), p.z());
                    return point(Entity.collideBoundingBox(candidate, desired, candidate.getBoundingBox(), world,
                        world.getEntityCollisions(candidate, candidate.getBoundingBox().expandTowards(desired))));
                });
                if (Math.hypot(step.x(), step.z()) > phaseHorizontalSpeed.getValue() + 1e-6) lastAdaptiveTick = motion.tick();
            } else step = BoatPhaseMotion.phaseStep(step, Math.min(phaseHorizontalSpeed.getValue(), BoatPhaseMotion.PROVEN_PHASE_HORIZONTAL));
            delta = new Vec3(step.x(), step.y(), step.z());
        } else {
            step = motion.plan(point(from), forward, sideways, up, down, yaw, airSpeed, vSpeed, floor, ceiling, false);
            step = BoatPhaseMotion.airStep(step, BoatPhaseMotion.horizontalStep(point(lastStep), airLimit), airAcceleration.getValue(), airborneTicks,
                antiKick.getValue(), up || down || motion.diveRemaining() > 0 || motion.riseRemaining() > 0, from.y - floor);
            delta = new Vec3(step.x(), step.y(), step.z());
            if (phase.getValue() && !world.noCollision(candidate, candidate.getBoundingBox().expandTowards(delta).deflate(1e-7))) {
                throughBlocks = true;
                step = BoatPhaseMotion.horizontalStep(step, horizontalLimit.getValue());
                step = BoatPhaseMotion.phaseStep(step, Math.min(phaseHorizontalSpeed.getValue(), BoatPhaseMotion.PROVEN_PHASE_HORIZONTAL));
                delta = new Vec3(step.x(), step.y(), step.z());
            }
        }
        Vec3 waterStep = waterDeparture(candidate, delta.y);
        if (waterStep != null) { delta = waterStep; throughBlocks = false; }
        plannedThroughBlocks = throughBlocks;
        AABB path = candidate.getBoundingBox().expandTowards(delta);
        if (!loaded(path)) {
            status = "waiting for chunks";
            delta = Vec3.ZERO;
            motion.cancelTravel();
        } else if (stopLava.getValue() && containsLava(path)) {
            status = "lava ahead";
            delta = Vec3.ZERO;
            motion.cancelTravel();
        } else if (motion.diveRemaining() > 0 && from.y + delta.y <= floor + 1e-6) {
            status = "world floor";
            motion.cancelTravel();
        } else if (motion.riseRemaining() > 0 && from.y + delta.y >= ceiling - 1e-6) {
            status = "world ceiling";
            motion.cancelTravel();
        } else {
            double remaining = Math.max(motion.diveRemaining(), motion.riseRemaining());
            status = remaining > 0 ? String.format(Locale.ROOT, "%s %.1f", travelName, remaining)
                : throughBlocks ? adaptive ? "adaptive phase" : adaptiveBlocked && adaptivePhase.getValue() ? "phasing (fallback)" : "phasing"
                : (cruising ? "cruising" : "piloting") + (speedFallback() ? " (speed fallback)" : "");
        }
        if (waterStep != null) status = delta.y > 0 ? "exiting water" : "water exit blocked";
        plannedFrom = from;
        plannedTick = motion.tick();
        plannedClear = !throughBlocks && clearTravel(candidate, delta);
        delta = hashStep(candidate, delta);
        candidate.setDeltaMovement(delta);
        return delta;
    }

    private boolean supported(AbstractBoat candidate) {
        return supported(candidate, .05);
    }

    private boolean supported(AbstractBoat candidate, double depth) {
        AABB b = candidate.getBoundingBox();
        return mc.level != null && !mc.level.noCollision(candidate, new AABB(b.minX + 1e-4, b.minY - depth, b.minZ + 1e-4,
            b.maxX - 1e-4, b.minY + 1e-7, b.maxZ - 1e-4));
    }

    private boolean travelReady(AbstractBoat candidate) {
        return isToggled() && owns(candidate) && !correcting && motion.holdTicks() == 0 && mc.gui.screen() == null;
    }

    private double travelVerticalSpeed() { return verticalRejected ? Math.min(9, verticalSpeed.getValue()) : verticalSpeed.getValue(); }

    public double travelSpeed() {
        return switch (travelMode()) {
            case HASH -> hashSpeed.getValue();
            case EXTENDED -> experimentalSpeed.getValue();
            case SUBSTEPS -> extendedSubsteps.getValue() && travel.rejected(BoatPhaseTravel.Mode.EXTENDED)
                ? BoatPhaseAirSteps.MAX_SPEED : substepSpeed.getValue();
            case DIRECT -> horizontalSpeed.getValue();
            case LIMITED -> Math.min(horizontalSpeed.getValue(), horizontalLimit.getValue());
        };
    }

    private BoatPhaseTravel.Mode travelMode() {
        return travel.select(hashTravel.getValue(), hashReady(), airSubsteps.getValue(), fastAir.getValue(), extendedSubsteps.getValue());
    }

    private boolean speedFallback() {
        return travelMode() == BoatPhaseTravel.Mode.LIMITED && (airSubsteps.getValue() || fastAir.getValue() || hashTravel.getValue())
            || travelMode() == BoatPhaseTravel.Mode.SUBSTEPS && extendedSubsteps.getValue() && travel.rejected(BoatPhaseTravel.Mode.EXTENDED);
    }

    private boolean hashReady() {
        return hashTravel.getValue() && !travel.rejected(BoatPhaseTravel.Mode.HASH) && correctionHash != null
            && world == hashWorld && connection == hashConnection;
    }

    private Vec3 hashStep(AbstractBoat candidate, Vec3 delta) {
        plannedHashTick = -1;
        if (Math.abs(delta.y) > BoatPhaseVertical.STEP) return delta;
        if (!hashReady() || delta.horizontalDistance() <= horizontalLimit.getValue()) return delta;

        candidate.setYRot(Mth.wrapDegrees(candidate.getYRot()));
        candidate.setXRot(Mth.wrapDegrees(candidate.getXRot()));
        Vec3 to = candidate.position().add(delta);
        double y = BoatPhaseHash.altitude(to.x, to.y, to.z, candidate.getYRot(), candidate.getXRot(), correctionHash);
        Vec3 adjusted = new Vec3(delta.x, y - candidate.getY(), delta.z);
        if (!Double.isFinite(y) || !clearTravel(candidate, adjusted)) return Vec3.ZERO;
        plannedHashTick = motion.tick();
        return adjusted;
    }

    public boolean clearTravel(AbstractBoat candidate, Vec3 delta) {
        if (!travelReady(candidate) || !Double.isFinite(delta.lengthSqr()) || delta.length() > Math.hypot(BoatPhaseVertical.MAX_SPEED, BoatPhaseAirSteps.TRIAL_SPEED)) return false;
        for (Entity entity : candidate.getSelfAndPassengers().toList()) {
            AABB path = entity.getBoundingBox().expandTowards(delta).deflate(1e-7);
            if (path.minY < world.getMinY() + 2 || path.maxY > world.getMaxY() + 1 || !loaded(path)
                || !world.getWorldBorder().isWithinBounds(path) || !world.noCollision(entity, path) || containsFluid(path)) return false;
        }
        return true;
    }

    public void afterMove(AbstractBoat candidate) {
        if (!owns(candidate)) return;
        boolean ground = candidate.getDeltaMovement().y <= 0 && supported(candidate);
        candidate.setOnGround(ground);
        candidate.verticalCollisionBelow = ground;
        if (!plannedThroughBlocks && !ground) candidate.verticalCollision = false;
    }

    private boolean loaded(AABB box) {
        for (int x = ((int) Math.floor(box.minX)) >> 4; x <= ((int) Math.floor(box.maxX)) >> 4; x++) {
            for (int z = ((int) Math.floor(box.minZ)) >> 4; z <= ((int) Math.floor(box.maxZ)) >> 4; z++) {
                if (!world.getChunkSource().hasChunk(x, z)) return false;
            }
        }
        return true;
    }

    private boolean containsLava(AABB box) {
        for (BlockPos pos : BlockPos.betweenClosed((int) Math.floor(box.minX), (int) Math.floor(box.minY), (int) Math.floor(box.minZ),
            (int) Math.floor(box.maxX), (int) Math.floor(box.maxY), (int) Math.floor(box.maxZ))) {
            if (world.getFluidState(pos).is(FluidTags.LAVA)) return true;
        }
        return false;
    }

    private boolean containsFluid(AABB box) {
        for (BlockPos pos : BlockPos.betweenClosed((int) Math.floor(box.minX), (int) Math.floor(box.minY), (int) Math.floor(box.minZ),
            (int) Math.floor(box.maxX), (int) Math.floor(box.maxY), (int) Math.floor(box.maxZ))) {
            if (!world.getFluidState(pos).isEmpty()) return true;
        }
        return false;
    }

    private boolean containsWater(AABB box) {
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
            if (world.getFluidState(pos).is(FluidTags.WATER)) return true;
        return false;
    }

    public Vec3 waterDeparture(AbstractBoat candidate, double ascent) {
        if (candidate == null || world == null || !Double.isFinite(ascent) || ascent <= 0 || !containsWater(candidate.getBoundingBox())) return null;
        Vec3 step = new Vec3(0, Math.min(.5, ascent), 0);
        return clearWaterExit(candidate, step) ? step : Vec3.ZERO;
    }

    private boolean clearWaterExit(AbstractBoat candidate, Vec3 step) {
        if (!travelReady(candidate) || !Double.isFinite(step.lengthSqr()) || step.y <= 0 || step.y > .5 || step.horizontalDistanceSqr() != 0) return false;
        for (Entity entity : candidate.getSelfAndPassengers().toList()) {
            AABB path = entity.getBoundingBox().expandTowards(step).deflate(1e-7);
            if (path.minY < world.getMinY() + 2 || path.maxY > world.getMaxY() + 1 || !loaded(path)
                || !world.getWorldBorder().isWithinBounds(path) || !world.noCollision(entity, path)) return false;
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(path.minX, path.minY, path.minZ), BlockPos.containing(path.maxX, path.maxY, path.maxZ))) {
                var fluid = world.getFluidState(pos);
                if (!fluid.isEmpty() && !fluid.is(FluidTags.WATER)) return false;
            }
        }
        return true;
    }

    // ---- Ticking and ride state --------------------------------------------------------------

    /** Runs ahead of other tick handlers, as the original did (Meteor {@code EventPriority.HIGHEST}). */
    @EventHandler(priority = PRIORITY_FIRST)
    private void onTick(MinecraftTickEvent event) {
        if (mc.player != null && !eventGate.tick(mc.player, mc.player.tickCount)) return;
        Connection currentConnection = mc.getConnection() == null ? null : mc.getConnection().getConnection();
        if (travelConnection != currentConnection) { travel.retry(); verticalRejected = false; remount.reset(); travelConnection = currentConnection; }
        if (travelWorld != mc.level) { remount.reset(); travelWorld = mc.level; }
        if (hashWorld != mc.level || mc.getConnection() == null || hashConnection != mc.getConnection().getConnection()) {
            correctionHash = null; hashWorld = null; hashConnection = null;
        }
        if (boat != null && sameSession()) {
            if (KeyUtils.isPhysicallyDown(mc.options.keyShift)) lastSneakTick = motion.tick();
            if (supported(boat)) lastGroundedAge = mc.player.tickCount;
            drainObservations();
        }
        AbstractBoat current = mc.player != null && mc.player.getRootVehicle() instanceof AbstractBoat b ? b : null;
        boolean controlling = current != null && current.getControllingPassenger() == mc.player;
        if (!sameSession() || boat != current || controlling != driver || (mc.player != null && !mc.player.isAlive())) {
            detach();
            if (current != null && mc.player.isAlive()) attach(current, controlling);
        }
        if (boat == null) { status = "board a boat"; return; }
        motion.nextTick();
        meter.tick(motion.tick(), System.nanoTime());
        drainObservations();
        String modeDescription = modeDescription();
        if (!modeDescription.equals(lastModeDescription)) {
            record("SPEED-MODE %s hashRequested=%b substeps=%b direct=%b", modeDescription, hashTravel.getValue(), airSubsteps.getValue(), fastAir.getValue());
            lastModeDescription = modeDescription;
        }
        if (driver) {
            if (pauseOthers.getValue()) pauseConflicts();
            boolean guest = boat.getPassengers().size() > 1;
            if (guest && !hadGuest && diveWithPassenger.getValue()) toggleDive();
            hadGuest = guest;
            if (motion.holdTicks() > 0) status = "correction hold " + motion.holdTicks();
        } else status = "passenger - observing";
        if (motion.tick() % 20 == 0) {
            record("STATE role=%s boat=%d local=%s wire=%s passengers=%s inside=%b health=%.1f tx=%d corrections=%d status=%s",
                driver ? "driver" : "passenger", boat.getId(), boat.position(), lastWire, boat.getPassengers().stream().map(Entity::getId).toList(),
                !world.noCollision(boat, boat.getBoundingBox()), mc.player.getHealth() + mc.player.getAbsorptionAmount(), sentCount, corrections, status);
            long now = System.nanoTime();
            record("SPEED sentBpt=%.3f sentBps=%.2f echoBpt=%.3f echoBps=%.2f packetsLastTick=%d mode=%s elapsedMs=%d",
                meter.sentBpt(), meter.sentBps(), meter.echoBpt(now), meter.echoBps(now), meter.packetsLastTick(), modeDescription,
                (now - rideStartedNanos) / 1_000_000L);
            if (log != null) log.flush();
        }
    }

    private void attach(AbstractBoat current, boolean controlling) {
        boat = current;
        player = mc.player;
        world = mc.level;
        network = mc.getConnection();
        connection = network.getConnection();
        driver = controlling;
        lastWire = boat.position();
        motion.reset();
        adaptiveBlocked = false;
        lastAdaptiveTick = lastSneakTick = -100;
        travel.newRide();
        lastFastVerticalTick = -100;
        meter.reset();
        lastModeDescription = "";
        rideStartedNanos = System.nanoTime();
        sentSneak = false;
        plannedHashTick = -1;
        plannedClear = false;
        sentCount = corrections = 0;
        entryHeight = boat.getY();
        hadGuest = false;
        openLog();
        boolean retryToken = remount.board(boat.getId(), player.tickCount, driver, remountRetry.getValue() || landedRetry.getValue());
        record("RETRY-CHECK token=%b driver=%b substepRejected=%b reason=%s", retryToken, driver,
            travel.rejected(BoatPhaseTravel.Mode.SUBSTEPS), boardingRetryReason);
        if (retryToken && remountRetry.getValue() && (travel.flightRejected() || verticalRejected)) {
            retryConfiguredFlight();
            record("REMOUNT-RETRY horizontal=%.2f vertical=%.2f", travelSpeed(), travelVerticalSpeed());
            info("Reboarded: retrying %.2f horizontal / %.2f vertical b/t.", travelSpeed(), travelVerticalSpeed());
        } else if (retryToken && !remountRetry.getValue() && airSubsteps.getValue() && travel.rejected(BoatPhaseTravel.Mode.SUBSTEPS)) {
            travel.retry(BoatPhaseTravel.Mode.SUBSTEPS);

            travel.reject(BoatPhaseTravel.Mode.EXTENDED);
            substepSpeed.setValue(landedRetrySpeed.getValue());
            record("LANDED-RETRY speed=%.2f", substepSpeed.getValue());
            info("Landed remount: retrying air substeps at %.2f b/t.", substepSpeed.getValue());
        }
        record("MOUNT boat=%d type=%s role=%s position=%s passengers=%s", boat.getId(), boat.getType(), driver ? "driver" : "passenger",
            boat.position(), boat.getPassengers().stream().map(Entity::getId).toList());
        if (driver && pauseOthers.getValue()) pauseConflicts();
        info(driver ? "Boat ready. WASD steers, jump rises, sprint descends; sneak dismounts." : "Passenger seat: observing the driver's boat; no movement controls are sent.");
    }

    private void detach() {
        if (boat != null && driver) {
            boolean deliberate = sameSession() && player.isAlive() && player.getRootVehicle() != boat && recentSneak();
            if (sameSession()) remount.intent(boat.getId(), player.tickCount,
                remountRetry.getValue() || supported(boat) || player.tickCount - lastGroundedAge <= 5, deliberate,
                retryableFallback(), remountRetry.getValue() || landedRetry.getValue());
        }
        epoch++;
        connection = null;
        record("END tx=%d corrections=%d recentSneak=%b inputSneak=%b stillMounted=%b adaptiveFallback=%b", sentCount, corrections,
            recentSneak(), sentSneak, player != null && boat != null && player.getRootVehicle() == boat, adaptiveBlocked);
        if (log != null) { log.close(); log = null; }
        observations.clear();
        observationCount.set(0);
        lastPlayerCorrection = lastVehicleCorrection = lastInputPacket = lastObservationPacket = null;
        plannedMode = BoatPhaseTravel.Mode.LIMITED;
        lastFastVerticalTick = -100;
        plannedHashTick = -1;
        meter.reset();
        if (boat != null && driver && !boat.isRemoved()) boat.setDeltaMovement(Vec3.ZERO);
        for (Module module : paused) if (!module.isToggled()) module.enable();
        paused.clear();
        boat = null;
        player = null;
        world = null;
        network = null;
        driver = correcting = hadGuest = false;
        cruising = plannedThroughBlocks = false;
        airborneTicks = 0;
        lastAdaptiveTick = -100;
        lastGroundedAge = -1000;
        lastStep = Vec3.ZERO;
        lastWire = plannedFrom = null;
        plannedTick = -1;
        motion.reset();
    }

    /** Movement modules that fight Boat Phase for the boat; they are switched back on when the ride ends. */
    private void pauseConflicts() {
        for (Class<? extends Module> type : List.of(EntityControlModule.class, FlightModule.class, ElytraFlyModule.class)) {
            Module module = ClientContext.getModule(type);
            if (module != null && module != this && module.isToggled()) {
                module.disable();
                if (!paused.contains(module)) paused.add(module);
                record("PAUSE module=%s", module.getName());
            }
        }
    }

    // ---- Actions (keys and the module page) --------------------------------------------------

    public void toggleDive() {
        if (!isToggled() || boat == null || !owns(boat)) { info("Board the driver's seat and enable Boat Phase first."); return; }
        if (motion.diveRemaining() > 0 || motion.riseRemaining() > 0) {
            motion.cancelTravel();
            info("Vertical travel canceled.");
        } else if (motion.holdTicks() == 0) {
            cruising = false;
            travelName = "Quick dive";
            requestedDiveDistance = diveDistance.getValue();
            motion.dive(requestedDiveDistance);
            record("DIVE distance=%.2f start=%s", requestedDiveDistance, boat.position());
            info("Diving %.0f blocks. Jump cancels.", requestedDiveDistance);
        }
    }

    public void toggleCruise() {
        if (!isToggled() || boat == null || !owns(boat)) { info("Board the driver's seat first."); return; }
        cruising = !cruising;
        cruiseYaw = mc.player.getYRot();
        info(cruising ? "Cruise started. WASD cancels; jump and sprint control height." : "Cruise canceled.");
        record("CRUISE active=%b yaw=%.2f", cruising, cruiseYaw);
    }

    private void travelToHeight(double height, String name) {
        if (motion.holdTicks() > 0) return;
        double delta = height - boat.getY();
        if (Math.abs(delta) < 0.01) { info("Already at that height."); return; }
        if (Math.abs(delta) > BoatPhaseMotion.MAX_TRAVEL) { warning("That height is more than %.0f blocks away.", BoatPhaseMotion.MAX_TRAVEL); return; }
        cruising = false;
        requestedDiveDistance = Math.abs(delta);
        travelName = name;
        if (delta > 0) motion.rise(delta); else motion.dive(-delta);
        record("TRAVEL name=%s fromY=%.3f targetY=%.3f", name, boat.getY(), height);
        info("%s: %.1f blocks %s. Jump or sprint cancels a rise.", name, Math.abs(delta), delta > 0 ? "up" : "down");
    }

    public void returnHeight() {
        if (!isToggled() || boat == null || !owns(boat)) { info("Board the driver's seat first."); return; }
        travelToHeight(entryHeight, "Entry height");
    }

    public void surface() {
        if (!isToggled() || boat == null || !owns(boat)) { info("Board the driver's seat first."); return; }
        double max = Math.min(128, world.getMaxY() - boat.getBoundingBox().maxY);
        for (int dy = 1; dy <= max; dy++) {
            AABB target = boat.getBoundingBox().move(0, dy, 0);
            if (!loaded(target)) break;
            if (!world.noCollision(boat, target) || containsFluid(target)) continue;
            boolean clear = true;
            for (Entity passenger : boat.getPassengers()) {
                AABB passengerBox = passenger.getBoundingBox().move(0, dy, 0);
                if (passengerBox.maxY > world.getMaxY() + 1 || !world.noCollision(passenger, passengerBox) || containsFluid(passengerBox)) { clear = false; break; }
            }
            if (clear) { travelToHeight(boat.getY() + dy, "Clear pocket"); return; }
        }
        info("No clear pocket with passenger headroom found within 128 blocks above.");
    }

    public void resumeAdaptivePhase() {
        adaptiveBlocked = false;
        lastAdaptiveTick = -100;
        record("ADAPTIVE-RESUME horizontalLimit=%.3f", horizontalLimit.getValue());
        info("Adaptive fallback cleared. The AdaptivePhase setting still controls whether it is enabled.");
    }

    public void retryFastAir() {
        travel.retry();
        verticalRejected = false;
        lastStep = Vec3.ZERO;
        record("SPEED-RETRY mode=%s", modeDescription());
        info("Speed fallback cleared. %s", modeDescription());
    }

    @EventHandler
    private void onInputPress(InputPressEvent event) {
        if (mc.gui.screen() != null) return;
        if (matches(diveKey, event)) toggleDive();
        else if (matches(cruiseKey, event)) toggleCruise();
        else if (matches(surfaceKey, event)) surface();
        else if (matches(returnHeightKey, event)) returnHeight();
        else if (matches(meterKey, event)) toggleSpeedometer();
    }

    private static boolean matches(KeyBindSetting setting, InputPressEvent event) {
        return !setting.followsDefault() && setting.getValue().mouse() == event.isMouse() && setting.getValue().code() == event.getCode();
    }

    /** A key bound to one of Boat Phase's actions is not also the module's own toggle key. */
    @Override
    public boolean claimsKeyPress(int code, boolean mouse) {
        for (KeyBindSetting key : new KeyBindSetting[]{diveKey, cruiseKey, surfaceKey, returnHeightKey, meterKey}) {
            if (!key.followsDefault() && key.getValue().mouse() == mouse && key.getValue().code() == code) return true;
        }
        return false;
    }

    private void recover() {
        cruising = false;
        meter.reset();
        lastStep = Vec3.ZERO;
        airborneTicks = 0;
        if (!adaptiveBlocked && !recentSneak() && motion.tick() - lastAdaptiveTick <= 10) {
            adaptiveBlocked = true;
            record("ADAPTIVE-FALLBACK lastAdaptiveTick=%d", lastAdaptiveTick);
            warning("Adaptive phase corrected; slow step for this ride. Board again or use Resume adaptive phase to retry.");
        }
        motion.corrected(correctionHold.getValue());
    }

    // ---- Packets -----------------------------------------------------------------------------

    @EventHandler(priority = PRIORITY_LATE)
    private void onSend(PacketEvent.Send event) {
        Packet<?> sent = event.getPacket();
        if (!event.isCancelled() && mc.isSameThread() && mc.player != null && mc.level != null
            && mc.getConnection() != null && event.getConnection() == mc.getConnection().getConnection() && !mc.player.isPassenger()
            && sent instanceof ServerboundInteractPacket interaction && lastBoardInteraction != interaction) {
            lastBoardInteraction = interaction;
            if (mc.level.getEntity(interaction.entityId()) instanceof AbstractBoat candidate) {
                boolean grounded = supported(candidate, .35);
                boolean armed = remount.intent(candidate.getId(), mc.player.tickCount, grounded || remountRetry.getValue(), true,
                    retryableFallback(), remountRetry.getValue() || landedRetry.getValue());
                boardingRetryReason = armed ? "deliberate boarding" : !landedRetry.getValue() && !remountRetry.getValue() ? "retry disabled"
                    : !retryableFallback() ? "no flight rejection; terrain limits are separate"
                    : "boat not landed";
            }
        }
        if (event.isCancelled() || !(sent instanceof ServerboundMoveVehiclePacket packet) || boat == null || !owns(boat) || event.getConnection() != connection || correcting) return;
        if (packet == substepPacket) return;
        if (splitting) { event.cancel(); return; }
        if (motion.alreadySent()) {
            record("SUPPRESS extra vehicle movement");
            event.cancel();
            return;
        }
        if (plannedHashTick == motion.tick() && hashReady()) {
            Vec3 p = packet.position();
            if (BoatPhaseHash.hash(p.x, p.y, p.z, Mth.wrapDegrees(packet.yRot()), Mth.wrapDegrees(packet.xRot())) != correctionHash) {
                event.cancel();
                if (lastWire != null) rewindBoat(lastWire);
                boat.setDeltaMovement(Vec3.ZERO);
                travel.reject(BoatPhaseTravel.Mode.HASH);
                record("HASH-STOP outgoing coordinates or rotation changed");
            }
            return;
        }
        if (lastWire != null) {
            Vec3 delta = packet.position().subtract(lastWire);
            boolean horizontalBatch = (plannedMode == BoatPhaseTravel.Mode.SUBSTEPS || plannedMode == BoatPhaseTravel.Mode.EXTENDED)
                && !plannedThroughBlocks;
            int horizontalCount = horizontalBatch ? BoatPhaseAirSteps.count(delta.horizontalDistance(), plannedMode == BoatPhaseTravel.Mode.EXTENDED
                ? BoatPhaseAirSteps.TRIAL_PACKETS : BoatPhaseAirSteps.MAX_PACKETS) : 1;
            int verticalCount = BoatPhaseVertical.count(delta.y);
            int count = Math.max(horizontalCount, verticalCount);

            if (horizontalCount == 0 || verticalCount == 0 || count > 1 && (plannedTick != motion.tick()
                || plannedFrom == null || plannedFrom.distanceTo(lastWire) > 1e-4 || !plannedClear && !plannedThroughBlocks)) {
                event.cancel(); rewindBoat(lastWire); boat.setDeltaMovement(Vec3.ZERO);
                record("STEPS-STOP unverified sweep or packet budget; oversized update suppressed");
                return;
            }
            if (count > 1) {
                event.cancel();
                splitting = true;
                substepOrigin = lastWire;
                try {
                    for (int i = 1; i <= count; i++) {
                        Vec3 end = substepOrigin.add(delta.scale((double) i / count));
                        substepPacket = new ServerboundMoveVehiclePacket(end, packet.yRot(), packet.xRot(), packet.onGround());
                        network.send(substepPacket);
                        if (lastWire.distanceTo(end) > 1e-5) break;
                    }
                } finally { substepPacket = null; splitting = false; }
                boolean finished = motion.sent(point(substepOrigin), point(lastWire));
                lastStep = lastWire.subtract(substepOrigin);
                if (Math.abs(lastStep.y) > BoatPhaseVertical.STEP + 1e-6) {
                    lastFastVerticalTick = motion.tick();
                    travel.newRide();
                } else if (horizontalBatch) {
                    if (lastStep.horizontalDistance() > horizontalLimit.getValue() + 1e-6) lastFastVerticalTick = -100;
                    travel.sent(lastStep.horizontalDistance() > BoatPhaseAirSteps.MAX_SPEED + 1e-6
                        ? BoatPhaseTravel.Mode.EXTENDED : BoatPhaseTravel.Mode.SUBSTEPS,
                        motion.tick(), lastStep.horizontalDistance(), horizontalLimit.getValue());
                }
                if (lastWire.distanceTo(packet.position()) > 1e-5) {
                    rewindBoat(lastWire);
                    boat.setDeltaMovement(Vec3.ZERO);
                    record("SUBSTEP-INTERRUPTED position=%s", lastWire);
                }
                record("AIR-STEPS count=%d total=%s horizontal=%.3f", count, lastStep, lastStep.horizontalDistance());
                if (finished) info("%s movement sent: %.1f blocks.", travelName, requestedDiveDistance);
            }
        }
    }

    /**
     * Called by {@code BoatPhaseConnectionMixin} on the client thread once a packet has been handed to the connection.
     * This must run before {@code send} returns: the substep loop in {@link #onSend} reads {@code lastWire} straight after
     * each send. Argus's {@code PacketEvent.PostSend} fires later on the Netty thread, so it cannot be used here.
     */
    public void afterSend(Connection sender, Packet<?> sent) {
        if (sender != connection) return;
        if (sent instanceof ServerboundPlayerInputPacket input && boat != null && sameSession()) {
            if (lastInputPacket == input) return;
            lastInputPacket = input;
            sentSneak = input.input().shift();
            if (sentSneak) lastSneakTick = motion.tick();
            record("INPUT %s", input.input());
        }
        if (!(sent instanceof ServerboundMoveVehiclePacket packet) || boat == null || !owns(boat)) return;

        if (!eventGate.vehicle(packet)) return;
        if (packet == substepPacket && splitting) {
            meter.sent(motion.tick(), System.nanoTime(), lastWire.x, lastWire.z, packet.position().x, packet.position().y, packet.position().z);
            record("SUBSTEP-WIRE from=%s to=%s delta=%s", lastWire, packet.position(), packet.position().subtract(lastWire));
            lastWire = packet.position();
            sentCount++;
            return;
        }
        if (motion.alreadySent()) return;
        if (correcting) {
            motion.acknowledged();
            record("CORRECTION-ACK position=%s", packet.position());
            return;
        }
        Vec3 from = lastWire == null ? packet.position() : lastWire;
        boolean finished = motion.sent(point(from), point(packet.position()));
        lastWire = packet.position();
        lastStep = lastWire.subtract(from);
        meter.sent(motion.tick(), System.nanoTime(), from.x, from.z, lastWire.x, lastWire.y, lastWire.z);
        BoatPhaseTravel.Mode sentMode = plannedHashTick == motion.tick() ? BoatPhaseTravel.Mode.HASH
            : plannedTick == motion.tick() && !plannedThroughBlocks ? plannedMode : BoatPhaseTravel.Mode.LIMITED;
        travel.sent(sentMode, motion.tick(), lastStep.horizontalDistance(), horizontalLimit.getValue());
        if (sentMode != BoatPhaseTravel.Mode.LIMITED && lastStep.horizontalDistance() > horizontalLimit.getValue() + 1e-6) lastFastVerticalTick = -100;
        if (plannedHashTick == motion.tick() && lastStep.horizontalDistance() > horizontalLimit.getValue()) {
            record("HASH-WIRE horizontal=%.3f hash=%d", lastStep.horizontalDistance(), correctionHash);
        }
        sentCount++;
        record("WIRE from=%s to=%s delta=%s local=%s plannedFrom=%s plannedTick=%d inside=%b passengers=%d ground=%b airTicks=%d horizontalLimit=%.3f fallback=%b status=%s",
            from, lastWire, lastWire.subtract(from), boat.position(), plannedFrom, plannedTick,
            !world.noCollision(boat, boat.getBoundingBox()), boat.getPassengers().size(), packet.onGround(), airborneTicks, horizontalLimit.getValue(), adaptiveBlocked, status);
        if (finished) info("%s movement sent: %.1f blocks.", travelName, requestedDiveDistance);
    }

    /** Called by the network mixin just before the client applies a vehicle correction. */
    public void beforeVehicleCorrection(ClientPacketListener handler, ClientboundMoveVehiclePacket packet) {
        if (handler != network || boat == null || !owns(boat)) return;
        if (lastVehicleCorrection == packet) return;
        lastVehicleCorrection = packet;
        rejectFastAir();
        correcting = true;
        corrections++;
        record("VEHICLE-CORRECTION server=%s local=%s lastWire=%s error=%.3f", packet.position(), boat.position(), lastWire,
            packet.position().distanceTo(boat.position()));
        recover();
    }

    /** Called by the network mixin once the vehicle correction has been applied. */
    public void afterVehicleCorrection(ClientPacketListener handler, ClientboundMoveVehiclePacket packet) {
        if (handler != network || !correcting) return;
        lastWire = packet.position();
        if (boat != null) boat.setDeltaMovement(Vec3.ZERO);
        correcting = false;
        status = "server correction";
        if (corrections <= 3 || corrections % 10 == 0) warning("Server corrected the boat (#%d). Dive canceled; pausing %d ticks.", corrections, correctionHold.getValue());
    }

    /** Called by the network mixin just before the client applies a player position correction. */
    public void beforePlayerCorrection(ClientboundPlayerPositionPacket packet) {
        if (lastPlayerCorrection == packet) return;
        lastPlayerCorrection = packet;
        if (mc.player != null && mc.level != null && mc.getConnection() != null) {
            PositionMoveRotation p = PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(mc.player), packet.change(), packet.relatives());
            correctionHash = BoatPhaseHash.hash(p.position().x, p.position().y, p.position().z, p.yRot(), p.xRot());
            hashWorld = mc.level; hashConnection = mc.getConnection().getConnection();
            record("HASH-REFERENCE hash=%d recentSneak=%b", correctionHash, recentSneak());
        }
        if (boat == null || !driver || !sameSession()) return;
        if (!recentSneak()) rejectFastAir();
        correcting = true;
        corrections++;
        recover();
        record("PLAYER-CORRECTION packet=%s recentSneak=%b inputSneak=%b", packet, recentSneak(), sentSneak);
    }

    /** Called by the network mixin once the player position correction has been applied. */
    public void afterPlayerCorrection() {
        if (!correcting) return;
        correcting = false;
        if (boat != null) { lastWire = boat.position(); boat.setDeltaMovement(Vec3.ZERO); }
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        long observedEpoch = epoch;
        Connection owner = connection;
        if (owner == null || event.getConnection() != owner) return;
        Packet<?> received = event.getPacket();
        if (!(received instanceof ClientboundSetPassengersPacket || received instanceof ClientboundEntityPositionSyncPacket
            || received instanceof ClientboundTeleportEntityPacket || received instanceof ClientboundMoveEntityPacket)) return;
        if (observationCount.incrementAndGet() > 256) { observationCount.decrementAndGet(); return; }
        observations.add(new Observation(observedEpoch, owner, received));
    }

    private void drainObservations() {
        Observation entry;
        while ((entry = observations.poll()) != null) {
            observationCount.updateAndGet(n -> Math.max(0, n - 1));
            if (entry.epoch != epoch || entry.connection != connection || boat == null || !sameSession()) continue;
            Packet<?> packet = entry.packet;
            if (lastObservationPacket == packet) continue;
            lastObservationPacket = packet;
            if (packet instanceof ClientboundSetPassengersPacket p && p.getVehicle() == boat.getId()) {
                record("SERVER-PASSENGERS ids=%s", java.util.Arrays.toString(p.getPassengers()));
            } else if (packet instanceof ClientboundEntityPositionSyncPacket p && p.id() == boat.getId()) {
                record("SERVER-BOAT-SYNC values=%s", p.values());
                Vec3 pos = p.values().position();
                meter.echo(motion.tick(), System.nanoTime(), pos.x, pos.y, pos.z);
            } else if (packet instanceof ClientboundTeleportEntityPacket p && p.id() == boat.getId()) {
                record("SERVER-BOAT-POSITION values=%s relatives=%s", p.change(), p.relatives());
            } else if (packet instanceof ClientboundMoveEntityPacket p && p.getEntity(world) == boat && p.hasPosition()) {
                record("SERVER-BOAT-DELTA x=%d y=%d z=%d local=%s", p.getXa(), p.getYa(), p.getZa(), boat.position());
            }
        }
    }

    // ---- Log ---------------------------------------------------------------------------------

    private void openLog() {
        if (!debugFile.getValue()) return;
        try {
            Path folder = mc.gameDirectory.toPath().resolve("boat-phase");
            Files.createDirectories(folder);
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
            Path path = folder.resolve("boat-" + time + ".log");
            log = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8));
            record("BOAT-PHASE v18 server=%s role=%s", mc.getCurrentServer() == null ? "local" : mc.getCurrentServer().ip, driver ? "driver" : "passenger");
            for (Setting<?> setting : getSettings()) record("cfg %s = %s", setting.getName(), setting.getValue());
            info("Boat log: %s", path);
        } catch (IOException e) {
            logger.warn("Cannot open Boat Phase log", e);
        }
    }

    private void record(String format, Object... args) {
        if (log != null) log.printf(Locale.ROOT, "[%d] %s%n", motion.tick(), String.format(Locale.ROOT, format, args));
    }

    /**
     * Puts the boat back on the last position the server saw. The rider was already placed for this tick, so it is
     * moved with the boat, otherwise the player is left stretched ahead of (or behind) the boat until the next tick.
     */
    private void rewindBoat(Vec3 position) {
        boat.setPos(position);
        boat.setDeltaMovement(Vec3.ZERO);
        for (Entity passenger : boat.getPassengers()) boat.positionRider(passenger);
    }

    /**
     * The sprint key as vanilla sees it (held, or latched by toggle-sprint), like the original's {@code keySprint.isDown()}.
     * Argus's Sprint module makes {@code isDown()} always true, which would sink the boat nonstop, so that forced
     * press is skipped by reading the key's own state.
     */
    private boolean sprintKeyDown() {
        return SprintModule.forceSprintKeyDown ? ((KeyMappingAccessor) mc.options.keySprint).argus$isDownRaw() : mc.options.keySprint.isDown();
    }

    private static BoatPhaseMotion.Point point(Vec3 v) { return new BoatPhaseMotion.Point(v.x, v.y, v.z); }

    private boolean recentSneak() {
        return motion.tick() - lastSneakTick <= 10 || (sameSession() && mc.player != null && KeyUtils.isPhysicallyDown(mc.options.keyShift));
    }

    private void rejectFastAir() {
        if (motion.tick() - lastFastVerticalTick >= 0 && motion.tick() - lastFastVerticalTick <= 20) {
            verticalRejected = true;
            lastFastVerticalTick = -100;
            travel.newRide();
            record("VERTICAL-REJECTED next=%.2f", travelVerticalSpeed());
            warning("Fast vertical movement corrected; using at most 9 vertical b/t until reboarding or Retry fast air.");
            return;
        }
        BoatPhaseTravel.Mode rejected = travel.corrected(motion.tick());
        if (rejected == BoatPhaseTravel.Mode.LIMITED) return;
        record("SPEED-REJECTED mode=%s next=%s", rejected, modeDescription());
        if (rejected == BoatPhaseTravel.Mode.HASH)
            warning("Hash experiment rejected. Air substeps remain available; movement resumes after recovery / boarding.");
        else if (rejected == BoatPhaseTravel.Mode.EXTENDED)
            warning("Experimental speed corrected; returning to 3.8 b/t. Reboard, change ExperimentalSpeed or use Retry fast air to retry.");
        else warning("%s corrected: using the limited speed. Retry fast air or re-enable Boat Phase to retry.", rejected);
    }

    private boolean retryableFallback() {
        return remountRetry.getValue() ? travel.flightRejected() || verticalRejected : travel.rejected(BoatPhaseTravel.Mode.SUBSTEPS);
    }

    private void retryConfiguredFlight() {
        travel.retryFlight();
        verticalRejected = false;
        lastFastVerticalTick = -100;
        lastStep = Vec3.ZERO;
        motion.corrected(4);
    }

    private void retryExtension() {
        travel.retry(BoatPhaseTravel.Mode.EXTENDED);
    }

    private String modeDescription() {
        String mode = switch (travelMode()) {
            case EXTENDED -> "Adjustable substeps";
            case SUBSTEPS -> "Air substeps";
            case DIRECT -> "Single update";
            case HASH -> "Hash experiment";
            case LIMITED -> hashTravel.getValue() && !travel.rejected(BoatPhaseTravel.Mode.HASH) && !hashReady()
                ? "Waiting for hash reference" : speedFallback() ? "Speed fallback - Retry fast air" : "Limited";
        };
        return String.format(Locale.ROOT, "%s | cap %.2f b/t", mode, travelSpeed());
    }

    // ---- Speedometer readouts (drawn by the Boat Speed HUD element) --------------------------

    /** True while there is a ride to show: you drive a boat with Boat Phase on. */
    public boolean hasReadout() { return boat != null && owns(boat); }

    /** Speed sent to the server, per tick and per second. */
    public String sentReadout() {
        return String.format(Locale.ROOT, "%.2f b/t | %.1f b/s", meter.sentBpt(), meter.sentBps());
    }

    /** Speed the server reports the boat moving at, from its position updates. */
    public String echoReadout() {
        long now = System.nanoTime();
        double echo = meter.echoBpt(now);
        return Double.isFinite(echo) ? String.format(Locale.ROOT, "%.2f b/t | %.1f b/s", echo, meter.echoBps(now)) : "waiting for matching positions";
    }

    /** The travel mode and its speed cap; a speed fallback is marked in gold. */
    public String modeReadout() {
        return (speedFallback() ? ChatFormatting.GOLD.toString() : "") + modeDescription();
    }

    /** What the boat is doing, and how many movement updates went out last tick. */
    public String statusReadout() {
        return status + " | " + meter.packetsLastTick() + " updates/tick";
    }

    /** Shows or hides the Boat Speed HUD element. */
    public void toggleSpeedometer() {
        BoatSpeedHudModule hud = ClientContext.getModule(BoatSpeedHudModule.class);
        if (hud == null) return;
        if (hud.isToggled()) hud.disable(); else hud.enable();
        info("Boat Speed HUD %s.", hud.isToggled() ? "on" : "off");
    }

    @Override
    public String getInfo() {
        return boat == null ? status : String.format(Locale.ROOT, "%s | %.2f b/t sent", status, meter.sentBpt());
    }
}
