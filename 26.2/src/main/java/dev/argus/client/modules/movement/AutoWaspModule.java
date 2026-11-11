/*
 * Adapted from DEVILS by ThianYong-hub and DEVILS contributors.
 * Upstream: https://github.com/ThianYong-hub/DEVILS
 * Revision: 2440a168c3ba6f051f2adf8249a8e6fb5f3a5b02
 * Auto Wasp is also based on Meteor Client, Copyright (c) Meteor Development.
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Modified for Comet by Boyonabike on 2026-10-22: Minecraft 26.1.2 mappings,
 * local inventory integration, lifecycle/navigation guards and settings credits;
 * VolytraFly boost and setback settings sized to the server's movement limits,
 * server-glide settings, ping-aware target prediction, worn-out elytra handling
 * and chest-swap hysteresis.
 * Ported to Minecraft 1.21.11 for Comet on 2026-10-23: chest-slot swaps use
 * the 1.21.11 inventory click API.
 * Ported to Argus Client by Boyonabike on 2026-11-10: Argus settings, events,
 * friends, chat and rotation manager replace their Meteor counterparts; the
 * offset is three number settings; Comet's inventory-loan checks are dropped.
 * Distributed under GNU GPL v3; see licenses/GPL-3.0.txt and THIRD_PARTY_NOTICES.md.
 * This program is distributed WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package dev.argus.client.modules.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.MinecraftDisconnectEvent;
import dev.argus.client.events.MinecraftTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.events.RotationRequestEvent;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.movement.autowasp.AutoWaspChestSwap;
import dev.argus.client.modules.movement.autowasp.AutoWaspFlightController;
import dev.argus.client.modules.movement.autowasp.AutoWaspPathfinder;
import dev.argus.client.modules.movement.autowasp.AutoWaspSpeed;
import dev.argus.client.modules.movement.autowasp.AutoWaspStandoff;
import dev.argus.client.modules.movement.autowasp.AutoWaspTargetTracker;
import dev.argus.client.modules.player.FreecamModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

public final class AutoWaspModule extends Module {
    private static final Minecraft mc = ClientAccess.argusclientMinecraft;
    private static final Direction[] CARDINAL_DIRECTIONS = {
        Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    private static final int TARGET_SEARCH_TICKS = 10;
    private static final int CHEST_SWAP_COOLDOWN_TICKS = 10;
    // Menu slot of the chest armour piece in the player's own inventory.
    private static final int CHEST_MENU_SLOT = 6;
    // The lowest priority, so any other module's rotation wins over the glide-steering heading.
    private static final int GLIDE_STEER_PRIORITY = Integer.MIN_VALUE;
    private static final float TPS = (float) AutoWaspSpeed.TICKS_PER_SECOND;

    // General
    private final Setting<Float> horizontalMaxBps = add(new NumberSetting<>("HorizontalMax",
        (float) (AutoWaspSpeed.BUNCHED_SAFE * TPS), 2.8F, (float) (AutoWaspSpeed.HORIZONTAL_TOP * TPS)).withUnit("b/s"));
    private final Setting<Float> verticalMaxBps = add(new NumberSetting<>("VerticalMax",
        (float) (AutoWaspSpeed.VERTICAL_TOP * TPS), 0.0F, (float) (AutoWaspSpeed.VERTICAL_TOP * TPS)).withUnit("b/s"));
    private final Setting<Float> max3dBps = add(new NumberSetting<>("Max3D",
        180.0F, 20.0F, (float) (AutoWaspSpeed.HORIZONTAL_TOP * TPS)).withUnit("b/s"));
    private final Setting<Boolean> avoidLanding = add(new BooleanSetting("AvoidLanding", true));
    private final Setting<Boolean> predictMovement = add(new BooleanSetting("PredictMovement", true));
    private final Setting<Boolean> onlyFriends = add(new BooleanSetting("OnlyFriends", false));
    private final Setting<Boolean> friendFilter = add(new BooleanSetting("FriendFilter", false));
    private final Setting<Action> action = add(new EnumSetting<>("OnTargetLoss", Action.TOGGLE));
    private final Setting<Boolean> keepSearching = add(new BooleanSetting("KeepSearching", true));
    private final Setting<Float> offsetX = add(new NumberSetting<>("OffsetX", 0.0F, -10.0F, 10.0F));
    private final Setting<Float> offsetY = add(new NumberSetting<>("OffsetY", 0.0F, -10.0F, 10.0F));
    private final Setting<Float> offsetZ = add(new NumberSetting<>("OffsetZ", 0.0F, -10.0F, 10.0F));
    private final Setting<Float> minDistance = add(new NumberSetting<>("MinDistance", 0.0F, 0.0F, 20.0F).withUnit("m"));
    // Chest swap
    private final Setting<Boolean> autoChestSwap = add(new BooleanSetting("ChestSwap", true));
    private final Setting<Float> chestSwapHorizontalRange = add(new NumberSetting<>("SwapHorizontalRange", 2.5F, 0.5F, 6.0F).dependsOn(autoChestSwap));
    private final Setting<Float> chestSwapVerticalRange = add(new NumberSetting<>("SwapVerticalRange", 1.0F, 0.0F, 4.0F).dependsOn(autoChestSwap));
    private final Setting<Float> chestSwapGroundDistance = add(new NumberSetting<>("SwapGroundDistance", 1.25F, 0.0F, 4.0F).dependsOn(autoChestSwap));
    private final Setting<Float> chestSwapHysteresis = add(new NumberSetting<>("SwapHysteresis", 1.5F, 0.0F, 4.0F).dependsOn(autoChestSwap));
    // Speed ramp
    private final Setting<Boolean> ramp = add(new BooleanSetting("SpeedRamp", false).markAsGroup());
    private final Setting<Float> rampStep = add(new NumberSetting<>("RampStep", 0.3F, 0.01F, 5.0F).dependsOn(ramp));
    private final Setting<Integer> setbackRewindTicks = add(new NumberSetting<>("SetbackRewind", 15, 0, 200).dependsOn(ramp));
    private final Setting<Integer> setbackHoldTicks = add(new NumberSetting<>("SetbackHold", 1, 0, 20).dependsOn(ramp));
    private final Setting<Integer> pinnedGlideTicks = add(new NumberSetting<>("PinnedGlide", 10, 0, 60).dependsOn(ramp));
    // Server glide
    private final Setting<Boolean> serverGlideGuard = add(new BooleanSetting("GlideGuard", true));
    private final Setting<Boolean> steerServerGlide = add(new BooleanSetting("SteerGlide", true));

    private final AutoWaspPathfinder pathfinder = new AutoWaspPathfinder(this);
    private final AutoWaspFlightController flightController = new AutoWaspFlightController(this, pathfinder);
    private final AutoWaspTargetTracker targetTracker = new AutoWaspTargetTracker();

    private Player target;
    private int targetSearchTimer;
    private boolean waitingForElytra;
    private int chestSwapCooldown;
    private FreecamModule freecam;
    // The heading the flight controller wants reported, and the module tick it asked on.
    private float steerYaw, steerPitch;
    private long steerTick = Long.MIN_VALUE;
    private long ticks;

    public AutoWaspModule() {
        super("AutoWasp", "Follows a target with elytra using obstacle-aware routing.", ModuleCategory.MOVEMENT);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.level == null) { disable(); return; }

        // Each flight module owns movement; avoid having another controller overwrite this one.
        ElytraFlyModule elytraFly = ClientContext.getModule(ElytraFlyModule.class);
        if (elytraFly != null && elytraFly.isToggled()) {
            elytraFly.disable();
            say("Disabled " + elytraFly.getName() + " for Auto Wasp pursuit.", ChatFormatting.GRAY);
        }
        resetNavigationState();
        chestSwapCooldown = 0;

        if (target == null || target.isRemoved()) {
            if (tryAcquireTarget(false)) {
                say("Target set to: " + target.getName().getString(), ChatFormatting.GRAY);
            } else if (!keepSearching.getValue()) {
                say("No valid targets.", ChatFormatting.RED);
                disable();
            }
        }
    }

    @Override
    public void onDisable() {
        target = null;
        targetSearchTimer = 0;
        waitingForElytra = false;
        chestSwapCooldown = 0;
        steerTick = Long.MIN_VALUE;
        targetTracker.reset();
        flightController.onDeactivate();
        pathfinder.onDeactivate();
    }

    // Start of the client tick, before the player moves.
    @EventHandler
    private void onTick(MinecraftTickEvent event) {
        ticks++;
        onTickSafe();
    }

    // End of the player's tick, after this tick's position was reported.
    @EventHandler
    private void onPostTick(LocalPlayerTickEvent event) {
        if (mc.player != null && mc.level != null) flightController.postTick();
    }

    @EventHandler
    private void onMove(LocalPlayerMoveEvent event) {
        onMoveSafe(event);
    }

    // Runs on Netty: the flight controller only fills atomic mailboxes here.
    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        flightController.onReceive(event.getPacket());
    }

    // Ahead of Argus's rotation manager, which holds packets back and sends them again later.
    @EventHandler(priority = 1000)
    private void onSend(PacketEvent.Send event) {
        flightController.onSend(event.getPacket());
    }

    /** Argus sends rotations it is asked for while this event is posted, so the glide steering is requested here. */
    @EventHandler
    private void onRotationRequest(RotationRequestEvent event) {
        // With movement sync the request comes round before this tick's movement, so last tick's heading still counts.
        if (ticks - steerTick > 1 || mc.player == null || !mc.player.isFallFlying()) return;
        ClientContext.rotationManager.requestRotation(new float[] {steerYaw, steerPitch}, GLIDE_STEER_PRIORITY);
    }

    @EventHandler
    private void onDisconnect(MinecraftDisconnectEvent event) {
        if (isToggled()) disable();
    }

    /** The heading to report so the server's hidden glide turns away from a wall. The camera does not move. */
    public void steerGlide(float yaw, float pitch) {
        steerYaw = yaw;
        steerPitch = pitch;
        steerTick = ticks;
    }

    private boolean freecamActive() {
        if (freecam == null) freecam = ClientContext.getModule(FreecamModule.class);
        return freecam != null && freecam.isToggled();
    }

    private void onTickSafe() {
        if (mc.player == null || mc.level == null || !mc.player.isAlive() || mc.player.isSpectator()) return;
        flightController.preTick(hasUsableElytra());
        if (freecamActive()) return;

        pathfinder.tickCaches();
        if (chestSwapCooldown > 0) chestSwapCooldown--;
        if (targetSearchTimer > 0) targetSearchTimer--;

        if (target == null || target.level() != mc.level || target.isRemoved() || target.isDeadOrDying() || target.getHealth() <= 0) {
            if (!handleMissingTarget()) {
                tickAutoChestSwap();
                return;
            }
        }

        if (target == null) {
            tickAutoChestSwap();
            return;
        }

        boolean isFriend = isFriend(target);
        boolean shouldIgnore = friendFilter.getValue()
            ? (onlyFriends.getValue() && !isFriend) || (!onlyFriends.getValue() && isFriend)
            : onlyFriends.getValue() && !isFriend;
        if (shouldIgnore) {
            if (!handleMissingTarget()) {
                tickAutoChestSwap();
                return;
            }
        }

        if (target == null) {
            tickAutoChestSwap();
            return;
        }

        targetTracker.sample(target.getId(), target.position());
        boolean shouldUseElytra = tickAutoChestSwap();
        if (!hasUsableElytra()) {
            if (!shouldUseElytra) {
                waitingForElytra = false;
                flightController.resetFlightTracking();
                return;
            }
            if (!waitingForElytra) {
                say("No usable elytra equipped, waiting for swap...", ChatFormatting.YELLOW);
                waitingForElytra = true;
            }
            flightController.resetFlightTracking();
            return;
        }

        waitingForElytra = false;
        if (!mc.player.isFallFlying()) {
            flightController.handleNotGliding();
            return;
        }

        flightController.onGlidingTick();
        pathfinder.tickPathing(mc.player.position(), getTargetPos(), flightController.getStuckTicks());
    }

    private void onMoveSafe(LocalPlayerMoveEvent event) {
        if (mc.player == null || mc.level == null || !mc.player.isAlive() || mc.player.isSpectator()
            || event.getMoverType() != MoverType.SELF) return;
        if (freecamActive()) return;
        if (target == null || target.level() != mc.level || target.isRemoved() || target.isDeadOrDying()) return;
        if (!hasUsableElytra() || !mc.player.isFallFlying()) return;

        // The aim is untouched; outside the standoff the steering point and lead are exactly as before.
        Vec3 aim = getTargetPos();
        Vec3 hold = AutoWaspStandoff.holdPoint(aim, target.position(), mc.player.position(), minDistance.getValue());

        Vec3 steer;
        boolean direct;
        if (hold != aim) {
            // Inside the standoff, hold the ring directly so no waypoint or shortcut can pull closer.
            steer = hold;
            direct = true;
        } else {
            steer = pathfinder.steerTarget(aim);
            direct = steer == aim;
        }

        // Only a straight approach matches the target's own movement; a waypoint is a fixed point to pass.
        Vec3 lead = predictMovement.getValue() && direct ? targetTracker.velocity() : Vec3.ZERO;
        flightController.onMove(event, steer, lead);
    }

    private static boolean isFriend(Player player) {
        return ClientContext.socialRegistry != null && ClientContext.socialRegistry.isFriendPlayer(player);
    }

    /** The nearest living player the friend settings allow. */
    private void findTarget() {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : mc.level.players()) {
            if (player == mc.player || player == mc.getCameraEntity()) continue;
            if (!player.isAlive() || player.isDeadOrDying() || player.getHealth() <= 0) continue;

            boolean isFriend = isFriend(player);
            boolean allowed = friendFilter.getValue() ? (onlyFriends.getValue() ? isFriend : !isFriend)
                : !onlyFriends.getValue() || isFriend;
            if (!allowed) continue;

            double distance = player.distanceToSqr(mc.player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        target = best;
    }

    private Vec3 getTargetPos() {
        if (target == null || mc.player == null) return mc.player != null ? mc.player.position() : Vec3.ZERO;

        Vec3 targetPos = target.position();
        if (predictMovement.getValue()) targetPos = targetTracker.predict(targetPos, pingMs());
        targetPos = targetPos.add(offsetX.getValue(), offsetY.getValue(), offsetZ.getValue());
        if (avoidLanding.getValue()) targetPos = liftTargetIfOnGround(targetPos);
        return pathfinder.adjustToSafeCorridor(targetPos, mc.player.position());
    }

    private int pingMs() {
        var connection = mc.getConnection();
        var info = connection == null || mc.player == null ? null : connection.getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : Math.max(0, info.getLatency());
    }

    private Vec3 liftTargetIfOnGround(Vec3 targetPos) {
        if (target == null) return targetPos;

        double distance = Math.max(0.35, target.getBoundingBox().getXsize() / 2.0);
        for (Direction direction : CARDINAL_DIRECTIONS) {
            BlockPos pos = BlockPos.containing(targetPos.relative(direction, distance).relative(direction.getClockWise(), distance)).below();
            if (pathfinder.isSolid(pos) && Math.abs(targetPos.y - (pos.getY() + 1)) <= 0.35) {
                return new Vec3(targetPos.x, pos.getY() + 1.35, targetPos.z);
            }
        }
        return targetPos;
    }

    private boolean tickAutoChestSwap() {
        boolean shouldUseElytra = !shouldSwapToChestplate();
        if (autoChestSwap.getValue() && target != null) requestChestGear(shouldUseElytra);
        return shouldUseElytra;
    }

    private boolean shouldSwapToChestplate() {
        if (!autoChestSwap.getValue() || mc.player == null || target == null) return false;

        ItemStack worn = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        boolean wearingChestplate = !worn.isEmpty() && !worn.has(DataComponents.GLIDER);
        double extra = wearingChestplate ? chestSwapHysteresis.getValue() : 0;
        double horizontalDistance = Math.hypot(mc.player.getX() - target.getX(), mc.player.getZ() - target.getZ());
        double verticalDistance = Math.abs(mc.player.getY() - target.getY());
        int scanDepth = Math.max(3, Mth.ceil(chestSwapGroundDistance.getValue() + extra) + 2);
        double groundDistance = pathfinder.distanceToSolidBelow(mc.player.position(), scanDepth);
        return AutoWaspChestSwap.wearChestplate(wearingChestplate, horizontalDistance, verticalDistance, groundDistance,
            chestSwapHorizontalRange.getValue(), chestSwapVerticalRange.getValue(), chestSwapGroundDistance.getValue(),
            chestSwapHysteresis.getValue());
    }

    private void requestChestGear(boolean glider) {
        if (mc.player == null || mc.gameMode == null || chestSwapCooldown > 0
            || mc.player.containerMenu != mc.player.inventoryMenu
            || !mc.player.inventoryMenu.getCarried().isEmpty()) return;
        ItemStack worn = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        // A worn-out elytra still counts as a glider but cannot glide, so it is replaced like a missing one.
        if (glider ? isUsableGlider(worn) : !worn.isEmpty() && !worn.has(DataComponents.GLIDER)) return;
        Slot chestSlot = mc.player.inventoryMenu.getSlot(CHEST_MENU_SLOT);
        if (!chestSlot.mayPickup(mc.player)) return;
        List<AutoWaspChestSwap.Candidate> candidates = new ArrayList<>();
        Inventory inventory = mc.player.getInventory();
        for (int i = 0; i <= Inventory.INVENTORY_SIZE; i++) {
            int slot = i == Inventory.INVENTORY_SIZE ? Inventory.SLOT_OFFHAND : i;
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || stack.has(DataComponents.GLIDER) != glider || !chestSlot.mayPlace(stack)
                || EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) continue;
            double[] armor = new double[2];
            if (!glider) stack.forEachModifier(EquipmentSlot.CHEST, (attribute, modifier) -> {
                if (attribute.equals(Attributes.ARMOR)) armor[0] += modifier.amount();
                else if (attribute.equals(Attributes.ARMOR_TOUGHNESS)) armor[1] += modifier.amount();
            });
            candidates.add(new AutoWaspChestSwap.Candidate(slot, glider, remainingDurability(stack), armor[0], armor[1]));
        }
        int slot = AutoWaspChestSwap.best(candidates, glider);
        if (slot < 0) return;
        int containerId = mc.player.inventoryMenu.containerId;
        if (Inventory.isHotbarSlot(slot) || slot == Inventory.SLOT_OFFHAND) {
            // One swap click moves a hotbar or offhand item straight into the chest slot.
            InventoryUtils.clickSlot(containerId, CHEST_MENU_SLOT, slot, ContainerInput.SWAP);
        } else {
            // Main inventory slots share their index with the menu: pick up, place on the chest, put back what came off.
            InventoryUtils.clickSlot(containerId, slot, 0, ContainerInput.PICKUP);
            InventoryUtils.clickSlot(containerId, CHEST_MENU_SLOT, 0, ContainerInput.PICKUP);
            if (!mc.player.inventoryMenu.getCarried().isEmpty()) InventoryUtils.clickSlot(containerId, slot, 0, ContainerInput.PICKUP);
        }
        if (glider) flightController.elytraSwapped();
        chestSwapCooldown = CHEST_SWAP_COOLDOWN_TICKS;
    }

    private boolean hasUsableElytra() {
        return mc.player != null && isUsableGlider(mc.player.getItemBySlot(EquipmentSlot.CHEST));
    }

    /** A glider on its last point of durability cannot glide. */
    private static boolean isUsableGlider(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.GLIDER) && remainingDurability(stack) > 1;
    }

    private static int remainingDurability(ItemStack stack) {
        return stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
    }

    private boolean handleMissingTarget() {
        boolean lostTarget = target != null;
        clearTargetState();
        if (lostTarget && !keepSearching.getValue() && action.getValue() != Action.CHOOSE_NEW_TARGET) {
            handleTargetLoss();
            return false;
        }
        if (tryAcquireTarget(true)) {
            say("New target: " + target.getName().getString(), ChatFormatting.GRAY);
            return true;
        }

        if (keepSearching.getValue()) return false;

        handleTargetLoss();
        return false;
    }

    private boolean tryAcquireTarget(boolean useCooldown) {
        if (useCooldown && targetSearchTimer > 0) return false;

        findTarget();
        targetSearchTimer = TARGET_SEARCH_TICKS;
        return target != null;
    }

    private void clearTargetState() {
        target = null;
        targetTracker.reset();
        pathfinder.clearTargetState();
        flightController.clearStuckState();
    }

    private void resetNavigationState() {
        targetSearchTimer = 0;
        targetTracker.reset();
        pathfinder.resetNavigationState();
        flightController.resetNavigationState();
    }

    private void handleTargetLoss() {
        say("Lost target!", ChatFormatting.YELLOW);

        switch (action.getValue()) {
            case CHOOSE_NEW_TARGET -> {
                findTarget();
                if (target == null) {
                    say("No valid targets found.", ChatFormatting.RED);
                    disable();
                } else {
                    say("New target: " + target.getName().getString(), ChatFormatting.GRAY);
                }
            }
            case TOGGLE -> disable();
            case DISCONNECT -> {
                if (isToggled()) disable();
                if (mc.player != null && mc.player.connection != null) {
                    mc.player.connection.handleDisconnect(new ClientboundDisconnectPacket(
                        Component.literal("%s[%sAuto Wasp%s] Lost target.".formatted(ChatFormatting.GRAY, ChatFormatting.BLUE, ChatFormatting.GRAY))
                    ));
                }
            }
        }
    }

    private void say(String message, ChatFormatting colour) {
        ChatUtils.printChat(Component.literal(message).withStyle(colour), ChatUtils.moduleSignature(this));
    }

    public Minecraft client() {
        return mc;
    }

    /** HorizontalMax in blocks per tick. */
    public double horizontalMaxSpeed() {
        return horizontalMaxBps.getValue() / AutoWaspSpeed.TICKS_PER_SECOND;
    }

    /** VerticalMax in blocks per tick. */
    public double verticalMaxSpeed() {
        return verticalMaxBps.getValue() / AutoWaspSpeed.TICKS_PER_SECOND;
    }

    /** Max3D in blocks per tick. */
    public double max3dSpeed() {
        return max3dBps.getValue() / AutoWaspSpeed.TICKS_PER_SECOND;
    }

    public double rampStepValue() {
        return rampStep.getValue();
    }

    public int setbackRewindTicksValue() {
        return setbackRewindTicks.getValue();
    }

    public int setbackHoldTicksValue() {
        return setbackHoldTicks.getValue();
    }

    public int pinnedGlideTicksValue() {
        return pinnedGlideTicks.getValue();
    }

    public boolean serverGlideGuardValue() {
        return serverGlideGuard.getValue();
    }

    public boolean steerServerGlideValue() {
        return steerServerGlide.getValue();
    }

    @Override
    public String getInfo() {
        if (target == null) return "Searching";
        String state = flightController.state();
        String name = target.getName().getString();
        return state.equals("Pursuing") || state.equals("Waiting") ? name : name + " | " + state;
    }

    public enum Action implements NamedEntry {
        TOGGLE("Toggle"),
        CHOOSE_NEW_TARGET("NewTarget"),
        DISCONNECT("Disconnect");

        private final String title;

        Action(String title) {
            this.title = title;
        }

        @Override
        public String getName() {
            return title;
        }
    }
}
