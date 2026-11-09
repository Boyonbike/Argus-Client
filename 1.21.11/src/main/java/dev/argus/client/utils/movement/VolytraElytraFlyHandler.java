package dev.argus.client.utils.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.modules.movement.ElytraFlyModule;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.KeyBindSetting;
import dev.argus.client.settings.SettingLabelCondition;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.KeyUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.movement.comet.AdaptiveFlightController.Motion;
import dev.argus.client.utils.movement.comet.CometHorizontalFlight;
import dev.argus.client.utils.movement.comet.ElytraBoostDelay;
import dev.argus.client.utils.movement.comet.SmartElytraSwap;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayDeque;
import java.util.function.Predicate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/*
 * Volytra Fly elytra mode.
 * Ported and adapted from VolytraFly by Volizray:
 * https://github.com/Volizray/VolytraFly-Addon
 * The original VolytraFly is released under CC0 1.0 Universal.
 * See THIRD_PARTY_NOTICES.md and licenses/VolytraFly-CC0.txt for attribution and the full licence text.
 */

/**
 * Faithful port of VolytraFly's elytra movement logic (General, Landing, Inventory and
 * Autopilot), plus optional Comet-style adaptive turning, base/boost speed caps and smart
 * elytra handling. Highway Mode, HUD and visuals are not part of this handler.
 */
public class VolytraElytraFlyHandler extends ElytraFlyModeHandler {
   private static final double RUBBERBAND_CAP_TOLERANCE_PERCENT = 5.0;
   private static final double RUBBERBAND_CAP_DURATION_SECONDS = 10.0;
   private static final int SPEED_PRESERVATION_REWIND_TICKS = 15;
   private static final int RUBBERBAND_CAP_TRIGGER_COUNT = 3;
   private static final long RUBBERBAND_CAP_TRIGGER_WINDOW_NANOS = 3_000_000_000L;
   private static final int AVOIDANCE_STUCK_TICKS = 3;
   private static final int VERTICAL_STEP_TIMEOUT_TICKS = 40;
   /** Boost H Speed's highest number, in b/s. */
   private static final double BOOST_H_NUMERIC_MAX = 300.0;
   /**
    * One notch past {@link #BOOST_H_NUMERIC_MAX}: the slider's top, shown as "Volytra Max", which uses Volytra's own
    * Horizontal Speed instead of a number. (The slider snaps in steps of 2 b/s here, so 300 and this are neighbours.)
    */
   private static final double BOOST_H_VOLYTRA_MAX = 301.0;

   private final Setting<Double> startSpeed;
   private final Setting<Double> horizontalSpeed;
   private final Setting<Double> accelerationPlateau;
   private final Setting<Double> accelerationStep;
   private final Setting<Double> verticalStartSpeed;
   private final Setting<Double> verticalSpeed;
   private final Setting<Double> verticalAccelerationPlateau;
   private final Setting<Double> verticalAccelerationStep;
   private final Setting<Integer> accelerationDelay;
   private final Setting<Double> fallMultiplier;
   private final Setting<Boolean> accelerateUpward;
   private final Setting<Boolean> autoTakeOff;
   private final Setting<Boolean> stopInWater;
   private final Setting<Boolean> dontGoIntoUnloadedChunks;
   private final Setting<Boolean> noCrash;
   private final Setting<Integer> crashLookAhead;
   private final Setting<Boolean> instaDrop;
   private final Setting<Boolean> limitMaxHeight;
   private final Setting<Double> maxHeight;
   private final Setting<Boolean> rubberbandSpeedCap;
   private final Setting<Double> rubberbandSpeedCapThreshold;

   private final Setting<Boolean> adaptiveTurning;
   private final Setting<Double> adaptiveTurnRate;
   private final Setting<Boolean> customSpeeds;
   private final KeyBindSetting boostKey;
   private final Setting<Double> baseHorizontalSpeed;
   private final Setting<Double> baseVerticalSpeed;
   private final Setting<Double> boostHorizontalSpeed;
   private final Setting<Double> boostVerticalSpeed;
   private final Setting<Boolean> speedCap3d;
   private final Setting<Double> maxSpeed3d;
   private final Setting<Boolean> tweaksGroup;

   private final Setting<Boolean> playerAvoidance;
   private final Setting<Double> avoidanceRadius;
   private final Setting<Boolean> avoidanceIgnoreFriends;
   private final Setting<Boolean> avoidWitherSkulls;
   private final Setting<Double> witherSkullRadius;
   private final Setting<Boolean> avoidArrows;
   private final Setting<Double> arrowRadius;
   private final Setting<Boolean> avoidBlocks;
   private final Setting<Double> blockAvoidanceRadius;
   private final Setting<Boolean> avoidanceLateral;

   private final Setting<Boolean> antiSlam;
   private final Setting<Double> antiSlamDistance;
   private final Setting<Double> antiSlamMinDistance;
   private final Setting<Double> antiSlamMinSpeed;

   private final Setting<Boolean> smartElytra;
   private final Setting<Boolean> cometMotion;
   private final Setting<Double> cometAcceleration;
   private final Setting<Double> cometBraking;
   private final Setting<Double> cometTurnRate;
   private final Setting<Double> cometBoostRampStep;
   private final Setting<Boolean> replace;
   private final Setting<Integer> replaceDurability;
   private final Setting<Boolean> autoReplenish;
   private final Setting<Integer> replenishSlot;

   private final Setting<Boolean> autoPilot;
   private final Setting<Boolean> useFireworks;
   private final Setting<Double> autoPilotFireworkDelay;
   private final Setting<Double> autoPilotMinimumHeight;

   private double acceleration;
   private double verticalAcceleration;
   private double velX;
   private double velY;
   private double velZ;
   private Vec3 forward = Vec3.ZERO;
   private Vec3 right = Vec3.ZERO;

   private boolean atMaxSpeed;
   private boolean atMaxVerticalSpeed;
   private boolean jumpPressed;
   private boolean lastJumpPressed;
   private boolean incrementJumpTimer;
   private int jumpTimer;
   private int accelerationDelayTicks;
   private int verticalAccelerationDelayTicks;
   private int ticksLeft;
   private Vec3 lastGlidePos;
   private double lastGlideIntendedSpeed;
   private int rubberbandCapTicksLeft;
   private double rubberbandCapSpeed;
   private int positionCorrections;
   private final ArrayDeque<Long> rubberbandQualifyingTimestamps = new ArrayDeque<>();

   private boolean avoidanceSteering;
   private Vec3 avoidanceLateralDir;
   private int avoidanceStuckTicksCount;
   private boolean verticalStepActive;
   private boolean verticalStepUp;
   private double verticalStepStartY;
   private int verticalStepTicks;
   private boolean lastForwardPressed;

   private Vec3 lastHorizontalDirection = Vec3.ZERO;
   private final ElytraBoostDelay boostDelay = new ElytraBoostDelay();
   private long clientTicks;
   private final CometHorizontalFlight cometFlight = new CometHorizontalFlight();
   private boolean cometFlying;
   // Diagnostics for the module list: why boost is or isn't running, and recent server setbacks.
   private final ArrayDeque<Long> cometSetbackTicks = new ArrayDeque<>();
   private String cometState = null;
   // Position corrections arrive on the network thread; the flight consumes them on the client thread.
   private final AtomicInteger cometCorrections = new AtomicInteger();

   public VolytraElytraFlyHandler(ElytraFlyModule module) {
      super(module);

      // General
      this.startSpeed = this.addDouble("Volytra Start Speed", "volytraStartSpeed", 2.999, 0.0, 50.0, this.nonComet());
      this.horizontalSpeed = this.addDouble("Volytra Horizontal Speed", "volytraHorizontalSpeed", 14.999, 0.0, 100.0, this.modeOnly());
      this.accelerationPlateau = this.addDouble("Volytra Acceleration Plateau", "volytraAccelerationPlateau", 14.999, 0.01, 100.0, this.nonComet());
      this.accelerationStep = this.addDouble("Volytra Acceleration Step", "volytraAccelerationStep", 0.3, 0.01, 5.0, this.nonComet());
      this.verticalStartSpeed = this.addDouble("Volytra Vertical Start Speed", "volytraVerticalStartSpeed", 5.999, 0.0, 50.0, this.modeOnly());
      this.verticalSpeed = this.addDouble("Volytra Vertical Speed", "volytraVerticalSpeed", 5.999, 0.0, 50.0, this.modeOnly());
      this.verticalAccelerationPlateau = this.addDouble("Volytra Vertical Acceleration Plateau", "volytraVerticalAccelerationPlateau", 29.999, 0.01, 100.0, this.nonComet());
      this.verticalAccelerationStep = this.addDouble("Volytra Vertical Acceleration Step", "volytraVerticalAccelerationStep", 1.0, 0.01, 5.0, this.nonComet());
      this.accelerationDelay = this.addInt("Volytra Acceleration Delay", "volytraAccelerationDelay", 1, 0, 100, this.modeOnly());
      this.fallMultiplier = this.addDouble("Volytra Fall Multiplier", "volytraFallMultiplier", 0.0, 0.0, 1.0, this.modeOnly());
      this.accelerateUpward = this.addBool("Volytra Accelerate Upward", "volytraAccelerateUpward", false, this.modeOnly());
      this.autoTakeOff = this.addBool("Volytra Auto Takeoff", "volytraAutoTakeoff", false, this.modeOnly());
      this.stopInWater = this.addBool("Volytra Stop In Water", "volytraStopInWater", false, this.modeOnly());
      this.dontGoIntoUnloadedChunks = this.addBool("Volytra No Unloaded Chunks", "volytraNoUnloadedChunks", false, this.modeOnly());
      this.noCrash = this.addBool("Volytra No Crash", "volytraNoCrash", false, this.modeOnly());
      this.crashLookAhead = this.addInt("Volytra Crash Lookahead", "volytraCrashLookahead", 3, 1, 15, this.modeOnly());
      this.instaDrop = this.addBool("Volytra Insta Drop", "volytraInstaDrop", false, this.modeOnly());
      this.limitMaxHeight = this.addBool("Volytra Limit Height", "volytraLimitHeight", true, this.modeOnly());
      this.maxHeight = this.addDouble("Volytra Max Height", "volytraMaxHeight", 500.0, -128.0, 4096.0, this.modeOnly());
      this.rubberbandSpeedCap = this.addBool("Volytra Rubberband Cap", "volytraRubberbandCap", true, this.modeOnly());
      this.rubberbandSpeedCapThreshold = this.addDouble("Volytra Rubberband Threshold", "volytraRubberbandThreshold", 200.0, 0.0, 400.0, this.modeOnly());

      // Player avoidance
      this.playerAvoidance = this.addBool("Volytra Player Avoidance", "volytraPlayerAvoidance", false, this.modeOnly());
      this.avoidanceRadius = this.addDouble("Volytra Avoidance Radius", "volytraAvoidanceRadius", 30.0, 0.0, 50.0, this.visibleWith(this.playerAvoidance));
      this.avoidanceIgnoreFriends = this.addBool("Volytra Ignore Friends", "volytraIgnoreFriends", true, this.visibleWith(this.playerAvoidance));
      this.avoidWitherSkulls = this.addBool("Volytra Avoid Wither Skulls", "volytraAvoidWitherSkulls", true, this.visibleWith(this.playerAvoidance));
      this.witherSkullRadius = this.addDouble("Volytra Wither Skull Radius", "volytraWitherSkullRadius", 10.0, 0.0, 50.0, value -> this.active() && this.playerAvoidance.getValue() && this.avoidWitherSkulls.getValue());
      this.avoidArrows = this.addBool("Volytra Avoid Arrows", "volytraAvoidArrows", false, this.visibleWith(this.playerAvoidance));
      this.arrowRadius = this.addDouble("Volytra Arrow Radius", "volytraArrowRadius", 50.0, 0.0, 50.0, value -> this.active() && this.playerAvoidance.getValue() && this.avoidArrows.getValue());
      this.avoidBlocks = this.addBool("Volytra Avoid Blocks", "volytraAvoidBlocks", true, this.visibleWith(this.playerAvoidance));
      this.blockAvoidanceRadius = this.addDouble("Volytra Block Radius", "volytraBlockRadius", 3.0, 0.0, 10.0, value -> this.active() && this.playerAvoidance.getValue() && this.avoidBlocks.getValue());
      this.avoidanceLateral = this.addBool("Volytra Sidestep", "volytraSidestep", true, this.visibleWith(this.playerAvoidance));

      // Landing
      this.antiSlam = this.addBool("Volytra Anti Slam", "volytraAntiSlam", true, this.modeOnly());
      this.antiSlamDistance = this.addDouble("Volytra Anti Slam Distance", "volytraAntiSlamDistance", 10.0, 0.1, 30.0, this.visibleWith(this.antiSlam));
      this.antiSlamMinDistance = this.addDouble("Volytra Anti Slam Min Distance", "volytraAntiSlamMinDistance", 0.5, 0.0, 5.0, this.visibleWith(this.antiSlam));
      this.antiSlamMinSpeed = this.addDouble("Volytra Anti Slam Min Speed", "volytraAntiSlamMinSpeed", 0.2, 0.0, 1.0, this.visibleWith(this.antiSlam));

      // Inventory
      this.replace = this.addBool("Volytra Replace Elytra", "volytraReplaceElytra", false, this.modeOnly());
      this.replaceDurability = this.addInt("Volytra Replace Durability", "volytraReplaceDurability", 2, 1, 500, this.visibleWith(this.replace));
      this.autoReplenish = this.addBool("Volytra Auto Replenish", "volytraAutoReplenish", false, this.modeOnly());
      this.replenishSlot = this.addInt("Volytra Replenish Slot", "volytraReplenishSlot", 9, 1, 9, this.visibleWith(this.autoReplenish));

      // Autopilot
      this.autoPilot = this.addBool("Volytra Auto Pilot", "volytraAutoPilot", false, this.modeOnly());
      this.useFireworks = this.addBool("Volytra Use Fireworks", "volytraUseFireworks", false, this.modeOnly());
      this.autoPilotFireworkDelay = this.addDouble("Volytra Firework Delay", "volytraFireworkDelay", 8.0, 1.0, 100.0, this.visibleWith(this.useFireworks));
      this.autoPilotMinimumHeight = this.addDouble("Volytra Min Height", "volytraMinHeight", 120.0, -128.0, 4096.0, this.modeOnly());

      // Tweaks
      this.tweaksGroup = this.module.add(new BooleanSetting("Tweaks", false).markAsGroup());
      this.tweaksGroup.expanded = true;
      this.adaptiveTurning = this.addBool("Volytra Adaptive Turning", "volytraAdaptiveTurning", true, this.modeOnly());
      this.adaptiveTurnRate = this.addDouble("Volytra Turn Rate", "volytraTurnRate", 15.0, 0.0, 90.0, this.visibleWithoutComet(this.adaptiveTurning));
      this.customSpeeds = this.addBool("Volytra Base/Boost Speeds", "volytraCustomSpeeds", false, this.modeOnly());
      // One toggle owns base and boost: off flies Volytra's own speeds with no boost; on shows all four sliders
      // and the boost key.
      this.baseHorizontalSpeed = this.addSpeed("Volytra Base H Speed", "volytraBaseHorizontalSpeed", 41.2, 0.0, 300.0, this.visibleWith(this.customSpeeds));
      this.baseVerticalSpeed = this.addSpeed("Volytra Base V Speed", "volytraBaseVerticalSpeed", 21.0, 0.0, 60.0, this.visibleWith(this.customSpeeds));
      this.boostHorizontalSpeed = this.addSpeed("Volytra Boost H Speed", "volytraBoostHorizontalSpeed", BOOST_H_VOLYTRA_MAX, 0.0, BOOST_H_VOLYTRA_MAX, this.visibleWith(this.customSpeeds));
      // The top of the slider is not a number: it is the fastest Volytra Fly goes (its Horizontal Speed).
      this.boostHorizontalSpeed.setBoundaryLabel("Volytra Max", SettingLabelCondition.MAX);
      this.boostVerticalSpeed = this.addSpeed("Volytra Boost V Speed", "volytraBoostVerticalSpeed", 60.0, 0.0, 60.0, this.visibleWith(this.customSpeeds));
      this.boostKey = this.module.add(new KeyBindSetting("Volytra Boost Key", "Sprint", this.visibleWith(this.customSpeeds)));
      this.boostKey.setConfigName("volytraBoostKey");
      this.speedCap3d = this.addBool("Volytra 3D Speed Cap", "volytra3dSpeedCap", false, this.modeOnly());
      this.maxSpeed3d = this.addSpeed("Volytra Max 3D Speed", "volytraMax3dSpeed", 200.0, 0.0, 480.0, this.visibleWith(this.speedCap3d));
      this.smartElytra = this.addBool("Volytra Smart Elytra", "volytraSmartElytra", false, this.modeOnly());
      this.cometMotion = this.addBool("Volytra Comet Motion", "volytraCometMotion", true, this.modeOnly());
      this.cometAcceleration = this.addDouble("Volytra Comet Acceleration", "volytraCometAcceleration", 0.15, 0.01, 2.0, this.visibleWith(this.cometMotion));
      this.cometBraking = this.addDouble("Volytra Comet Braking", "volytraCometBraking", 0.3, 0.01, 2.0, this.visibleWith(this.cometMotion));
      this.cometTurnRate = this.addDouble("Volytra Comet Turn Rate", "volytraCometTurnRate", 15.0, 0.0, 45.0, this.visibleWith(this.cometMotion, this.adaptiveTurning));
      this.cometBoostRampStep = this.addDouble("Volytra Comet Boost Ramp Step", "volytraCometBoostRampStep", 0.3, 0.01, 5.0, this.visibleWith(this.cometMotion, this.customSpeeds));

      module.modeSetting.setChangeCallback(this::onModeChanged);
   }

   private boolean active() {
      return this.module.modeSetting.getValue() == ElytraFlyModule.ModeChoice.VOLYTRA;
   }

   private <T> Predicate<T> modeOnly() {
      return value -> this.active();
   }

   private <T> Predicate<T> visibleWith(Setting<Boolean> parent) {
      return value -> this.active() && parent.getValue();
   }

   private <T> Predicate<T> visibleWith(Setting<Boolean> first, Setting<Boolean> second) {
      return value -> this.active() && first.getValue() && second.getValue();
   }

   private <T> Predicate<T> nonComet() {
      return value -> this.active() && !this.cometMotion.getValue();
   }

   private <T> Predicate<T> visibleWithoutComet(Setting<Boolean> parent) {
      return value -> this.active() && !this.cometMotion.getValue() && parent.getValue();
   }

   private BooleanSetting addBool(String name, String configName, boolean defaultValue, Predicate<Boolean> visible) {
      BooleanSetting setting = this.module.add(new BooleanSetting(name, defaultValue, visible));
      setting.setConfigName(configName);
      return setting;
   }

   private Setting<Double> addDouble(String name, String configName, double defaultValue, double min, double max, Predicate<Double> visible) {
      Setting<Double> setting = this.module.add(new NumberSetting<>(name, defaultValue, min, max, visible));
      setting.setConfigName(configName);
      return setting;
   }

   private Setting<Integer> addInt(String name, String configName, int defaultValue, int min, int max, Predicate<Integer> visible) {
      Setting<Integer> setting = this.module.add(new NumberSetting<>(name, defaultValue, min, max, visible));
      setting.setConfigName(configName);
      return setting;
   }

   private Setting<Double> addSpeed(String name, String configName, double defaultValue, double min, double max, Predicate<Double> visible) {
      NumberSetting<Double> setting = this.module.add(new NumberSetting<>(name, defaultValue, min, max, visible));
      setting.setConfigName(configName);
      setting.withUnit(" b/s");
      return setting;
   }

   private void onModeChanged() {
      if (!this.module.isToggled()) return;
      if (this.active()) {
         this.onEnable();
      } else {
         this.onDisable();
      }
   }

   @Override
   public void onEnable() {
      LocalPlayer player = this.minecraft().player;
      this.atMaxSpeed = false;
      this.lastJumpPressed = false;
      this.jumpPressed = false;
      this.incrementJumpTimer = false;
      this.jumpTimer = 0;
      this.ticksLeft = 0;
      this.accelerationDelayTicks = 0;
      this.verticalAccelerationDelayTicks = 0;
      this.lastGlidePos = null;
      this.lastGlideIntendedSpeed = 0.0;
      this.lastForwardPressed = false;
      this.lastHorizontalDirection = Vec3.ZERO;
      this.boostDelay.reset();
      this.cometFlying = false;

      this.rubberbandCapTicksLeft = 0;
      double currentHorizontalSpeed = player != null
         ? Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z)
         : 0.0;
      double cap = this.effectiveHorizontalSpeedCap();
      this.acceleration = Math.max(this.effectiveHorizontalStartSpeed(), Math.min(currentHorizontalSpeed, cap));
      if (this.acceleration >= cap) this.atMaxSpeed = true;

      this.resetVerticalAcceleration();
      this.rubberbandQualifyingTimestamps.clear();
      this.avoidanceSteering = false;
      this.avoidanceLateralDir = null;
      this.avoidanceStuckTicksCount = 0;
      this.verticalStepActive = false;
      this.verticalStepTicks = 0;
      this.positionCorrections = 0;
   }

   @Override
   public void onDisable() {
      this.releaseAvoidance();
      this.releaseVerticalStep();
      this.releaseAllForcedKeys();
      this.lastForwardPressed = false;
      this.lastHorizontalDirection = Vec3.ZERO;
      this.boostDelay.reset();
      this.cometFlying = false;
      this.rubberbandCapTicksLeft = 0;
   }

   @Override
   public void handleMovement(LocalPlayerMoveEvent event) {
      Minecraft minecraft = this.minecraft();
      LocalPlayer player = minecraft.player;
      ClientLevel world = minecraft.level;
      if (player == null || world == null) return;
      if (!player.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.GLIDER)) {
         this.lastHorizontalDirection = Vec3.ZERO;
         return;
      }

      this.autoTakeoff(player);
      this.updatePlayerAvoidance(player);

      if (player.isFallFlying()) {
         Vec3 movement = event.getVelocity();
         this.velX = 0.0;
         this.velZ = 0.0;
         this.velY = movement.y;
         this.forward = Vec3.directionFromRotation(0.0F, player.getYRot()).scale(0.1);
         this.right = Vec3.directionFromRotation(0.0F, player.getYRot() + 90.0F).scale(0.1);

         if (player.isInWater() && this.stopInWater.getValue()) {
            player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            return;
         }

         this.handleFallMultiplier();
         this.handleAutopilot(player);

         if (this.cometMotion.getValue()) {
            this.handleCometHorizontal(player);
         } else {
            this.cometFlying = false;
            this.resetRampOnCollision(player);
            this.handleAcceleration();
            this.handleHorizontalSpeed();
         }

         this.handleVerticalAcceleration();
         this.handleVerticalSpeed();
         this.applyCustomVerticalSpeedCap();
         this.handleAntiSlam(world, player);
         this.handleMaxHeight(player);

         int chunkX = (int)((player.getX() + this.velX) / 16.0);
         int chunkZ = (int)((player.getZ() + this.velZ) / 16.0);
         if (this.dontGoIntoUnloadedChunks.getValue() && !world.hasChunk(chunkX, chunkZ)) {
            this.velX = 0.0;
            this.velZ = 0.0;
         }

         if (this.noCrash.getValue()) {
            Vec3 velocity = player.getDeltaMovement();
            if (velocity.lengthSqr() > 1.0E-8) {
               Vec3 lookAhead = player.position().add(velocity.normalize().scale(this.crashLookAhead.getValue()));
               BlockHitResult hit = world.clip(new ClipContext(
                  player.position(),
                  new Vec3(lookAhead.x, player.getY(), lookAhead.z),
                  ClipContext.Block.COLLIDER,
                  ClipContext.Fluid.NONE,
                  player
               ));
               if (hit.getType() == HitResult.Type.BLOCK) {
                  this.velX = 0.0;
                  this.velZ = 0.0;
               }
            }
         }

         if (!this.cometMotion.getValue()) this.applyAdaptiveTurning();
         if (this.cometMotion.getValue()) this.applyTotalCeiling(this.cometFlight.totalCeiling());
         this.applySpeedCap3d();
         if (this.cometMotion.getValue()) {
            // Momentum continues from what was actually sent, including chunk/crash stops and the caps.
            this.cometFlight.sent(this.velX, this.velZ);
         }

         event.setVelocity(new Vec3(this.velX, this.velY, this.velZ));
         this.lastGlidePos = player.position();
         this.lastGlideIntendedSpeed = Math.hypot(this.velX, this.velZ);
      } else {
         if (this.lastForwardPressed) {
            this.setKey(minecraft.options.keyUp, false);
            this.lastForwardPressed = false;
         }
         this.lastGlidePos = null;
         this.lastGlideIntendedSpeed = 0.0;
         this.lastHorizontalDirection = Vec3.ZERO;
         this.cometFlying = false;
      }
   }

   /**
    * Comet's horizontal flight (cruise, unlimited Sprint boost and the transition between them); see
    * {@link CometHorizontalFlight}. Vertical motion stays on Volytra's model.
    */
   private void handleCometHorizontal(LocalPlayer player) {
      if (!this.cometFlying) {
         // Comet starts each controlled glide from rest with the boost ramp released.
         this.cometFlight.reset();
         this.cometFlying = true;
         this.cometCorrections.set(0);
         this.lastGlidePos = null;
      }

      if (this.cometCorrections.getAndSet(0) > 0) {
         // A correction farther than 64 blocks (a command or portal) is a teleport, not a setback.
         boolean teleport = this.lastGlidePos == null || player.position().distanceToSqr(this.lastGlidePos) > 64.0 * 64.0;
         this.cometFlight.setback(teleport);
         this.cometSetbackTicks.addLast(this.clientTicks);
         this.lastGlidePos = null;
      }

      // As in Comet and VolytraFly, only a real wall collision costs speed: keep what got through.
      if (this.lastGlidePos != null && player.horizontalCollision) {
         this.cometFlight.collided(player.getX() - this.lastGlidePos.x, player.getZ() - this.lastGlidePos.z);
      }

      Minecraft minecraft = this.minecraft();
      int forward = (minecraft.options.keyUp.isDown() ? 1 : 0) - (minecraft.options.keyDown.isDown() ? 1 : 0);
      int right = (minecraft.options.keyRight.isDown() ? 1 : 0) - (minecraft.options.keyLeft.isDown() ? 1 : 0);
      // Comet's boost key: the physical Sprint key while playing (no screen open).
      // Lets the server establish a glide from a fresh elytra swap before Sprint applies its boost.
      this.observeBoostDelay(player);
      boolean sprintHeld = this.boostKeyHeld();
      boolean boostKey = this.boostDelay.ready() && sprintHeld;

      Motion output = this.cometFlight.step(new CometHorizontalFlight.Settings(
         this.cometAcceleration.getValue(), this.cometBraking.getValue(),
         this.adaptiveTurning.getValue() ? this.cometTurnRate.getValue() : 0.0,
         this.withRubberbandCap(this.customSpeeds.getValue() ? this.baseHorizontalSpeed.getValue() / 20.0 : this.horizontalSpeed.getValue()),
         this.customSpeeds.getValue(),
         this.withRubberbandCap(this.boostHorizontalCap()),
         this.cometBoostRampStep.getValue(),
         Math.max(this.horizontalSpeed.getValue(), 1.0E-3)), forward, right, player.getYRot(), boostKey);
      this.velX = output.x();
      this.velZ = output.z();
      if (!this.customSpeeds.getValue()) this.cometState = "Volytra speed";
      else if (sprintHeld && !this.boostDelay.ready()) this.cometState = "Boost held (swap)";
      else if (this.cometFlight.boosting()) this.cometState = "Boost " + Math.round(this.cometFlight.boostSpeed() * 20.0);
      else if (sprintHeld) this.cometState = "Boost key, no boost";
      else this.cometState = "Cruise";
   }

   /**
    * Comet Motion's flight state for the module list, with server setbacks in the last five seconds;
    * null when not flying with Comet Motion.
    */
   public String cometStatus() {
      if (!this.cometFlying || this.cometState == null) return null;
      while (!this.cometSetbackTicks.isEmpty() && this.clientTicks - this.cometSetbackTicks.peekFirst() > 100) {
         this.cometSetbackTicks.pollFirst();
      }
      int setbacks = this.cometSetbackTicks.size();
      String status = this.cometState;
      if (this.rubberbandCapTicksLeft > 0) status += ", rubberband cap";
      return setbacks == 0 ? status : status + ", " + setbacks + (setbacks == 1 ? " setback" : " setbacks");
   }

   private double withRubberbandCap(double speed) {
      if (this.rubberbandCapTicksLeft > 0) speed = Math.min(speed, this.rubberbandCapSpeed);
      return Math.max(0.0, speed);
   }

   /** Holds the whole 3D move to {@code ceiling} blocks per tick, as Comet does while boosting. */
   private void applyTotalCeiling(double ceiling) {
      double speed = Math.sqrt(this.velX * this.velX + this.velY * this.velY + this.velZ * this.velZ);
      if (!(speed > ceiling)) return;
      double scale = ceiling / speed;
      this.velX *= scale;
      this.velY *= scale;
      this.velZ *= scale;
   }

   private void applyAdaptiveTurning() {
      if (!this.adaptiveTurning.getValue()) {
         this.lastHorizontalDirection = Vec3.ZERO;
         return;
      }

      double speed = Math.hypot(this.velX, this.velZ);
      if (speed < 1.0E-4) {
         this.lastHorizontalDirection = Vec3.ZERO;
         return;
      }

      Vec3 desired = new Vec3(this.velX / speed, 0.0, this.velZ / speed);
      if (this.lastHorizontalDirection.lengthSqr() < 1.0E-6) {
         this.lastHorizontalDirection = desired;
         return;
      }

      double dot = Math.max(-1.0, Math.min(1.0, this.lastHorizontalDirection.dot(desired)));
      double angle = Math.toDegrees(Math.acos(dot));
      if (angle > 135.0) {
         this.lastHorizontalDirection = desired;
         return;
      }

      double configuredTurnRate = this.adaptiveTurnRate.getValue();
      double speedRatio = Math.min(1.0, speed / 5.9);
      double maxTurn = Math.min(90.0, configuredTurnRate * (1.0 + speedRatio));
      if (angle <= maxTurn) {
         this.lastHorizontalDirection = desired;
         return;
      }

      double cross = this.lastHorizontalDirection.x * desired.z - this.lastHorizontalDirection.z * desired.x;
      if (Math.abs(cross) < 1.0E-6) {
         this.lastHorizontalDirection = desired;
         return;
      }

      Vec3 turned = rotateHorizontal(this.lastHorizontalDirection, Math.signum(cross) * maxTurn);
      this.lastHorizontalDirection = turned;
      this.velX = turned.x * speed;
      this.velZ = turned.z * speed;
   }

   private void applyCustomVerticalSpeedCap() {
      if (!this.customSpeeds.getValue()) return;

      double cap = this.effectiveVerticalSpeedCap();
      if (Math.abs(this.velY) > cap) {
         double brakingCap = this.cometMotion.getValue()
            ? Math.max(cap, Math.abs(this.velY) - this.cometBraking.getValue())
            : cap;
         this.velY = Math.copySign(brakingCap, this.velY);
      }
   }

   private void applySpeedCap3d() {
      if (!this.speedCap3d.getValue()) return;

      double cap = this.maxSpeed3d.getValue() / 20.0;
      if (cap <= 0.0) {
         this.velX = 0.0;
         this.velY = 0.0;
         this.velZ = 0.0;
         return;
      }

      double speed = Math.sqrt(this.velX * this.velX + this.velY * this.velY + this.velZ * this.velZ);
      if (speed <= cap || speed < 1.0E-6) return;

      double scale = cap / speed;
      this.velX *= scale;
      this.velY *= scale;
      this.velZ *= scale;
   }

   @Override
   public void handlePrePlayerTick(LocalPlayerPreTickEvent event) {
      LocalPlayer player = this.minecraft().player;
      if (player == null) return;
      this.clientTicks++;
      this.smartLandingSwap(player);
      this.observeBoostDelay(player);
   }

   @Override
   public void handlePlayerTick(LocalPlayerTickEvent event) {
      if (this.rubberbandCapTicksLeft > 0) this.rubberbandCapTicksLeft--;

      Minecraft minecraft = this.minecraft();
      LocalPlayer player = minecraft.player;
      if (player == null) return;

      if (this.instaDrop.getValue() && player.isFallFlying()) {
         player.setDeltaMovement(Vec3.ZERO);
         player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, player.horizontalCollision));
      }

      this.smartLandingSwap(player);

      if (this.autoReplenish.getValue() && this.canManageInventory(player)) {
         int slot = InventoryUtils.findInventorySlot(stack -> stack.is(Items.FIREWORK_ROCKET), true);
         if (slot >= 0) {
            InventoryUtils.swapWithHotbarSlot(InventoryUtils.toMenuSlot(slot), this.replenishSlot.getValue() - 1);
         }
      }

      if (this.replace.getValue() && this.canManageInventory(player)) {
         ItemStack chestStack = player.getItemBySlot(EquipmentSlot.CHEST);
         if (chestStack.is(Items.ELYTRA)) {
            int remaining = chestStack.getMaxDamage() - chestStack.getDamageValue();
            if (remaining <= this.replaceDurability.getValue()) {
               int slot = InventoryUtils.findInventorySlot(stack ->
                  stack.is(Items.ELYTRA) && stack.getMaxDamage() - stack.getDamageValue() > this.replaceDurability.getValue(), false);
               if (slot >= 0) {
                  // InventoryMenu slot 6 is the chest armour slot.
                  InventoryUtils.swapSlotsWithPickup(InventoryUtils.toMenuSlot(slot), 6);
               }
            }
         }
      }
   }

   /**
    * Comet's smart elytra swap. Called from {@code LocalPlayer.aiStep} just before vanilla checks a Jump press for
    * gliding: equipping here lets vanilla open the wings in the same attempt and send its own normal flight packet,
    * and holding Jump or ordinary jumps never equip wings.
    */
   public void prepareSmartTakeoff() {
      Minecraft minecraft = this.minecraft();
      LocalPlayer player = minecraft.player;
      if (!this.active() || !this.module.isToggled() || !this.smartElytra.getValue() || !this.canSmartSwap(player)
         || minecraft.screen != null || !minecraft.isWindowActive()
         || player.onGround() || player.isFallFlying() || player.isInWater() || player.isInLava()
         || player.isPassenger() || player.getAbilities().flying || player.hasEffect(MobEffects.LEVITATION)) return;
      if (gliderRemaining(player.getItemBySlot(EquipmentSlot.CHEST)) > 1) return;
      this.swapChest(player, true);
   }

   /** Comet's landing swap: back on the ground, wear the best chestplate instead of wings (or nothing). */
   private void smartLandingSwap(LocalPlayer player) {
      if (!this.smartElytra.getValue() || !this.canSmartSwap(player) || !player.onGround()) return;
      ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
      if (chest.isEmpty() || isGlider(chest)) this.swapChest(player, false);
   }

   private boolean canSmartSwap(LocalPlayer player) {
      Minecraft minecraft = this.minecraft();
      return player != null && minecraft.level != null && minecraft.gameMode != null && player.isAlive()
         && !player.isSpectator() && player.containerMenu == player.inventoryMenu
         && player.inventoryMenu.getCarried().isEmpty();
   }

   private void swapChest(LocalPlayer player, boolean glider) {
      // Slot permissions also cover a worn Curse of Binding item; never pick it up and strand it on the cursor.
      Slot chestSlot = player.inventoryMenu.getSlot(InventoryMenu.ARMOR_SLOT_START + 1);
      if (!chestSlot.mayPickup(player)) return;
      Inventory inventory = player.getInventory();
      List<SmartElytraSwap.Candidate> candidates = new ArrayList<>();
      for (int i = 0; i <= Inventory.INVENTORY_SIZE; i++) {
         int slot = i == Inventory.INVENTORY_SIZE ? Inventory.SLOT_OFFHAND : i;
         ItemStack stack = inventory.getItem(slot);
         if (stack.isEmpty() || isGlider(stack) != glider || !chestSlot.mayPlace(stack)
            || EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) continue;
         double[] armor = new double[2];
         if (!glider) stack.forEachModifier(EquipmentSlot.CHEST, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ARMOR)) armor[0] += modifier.amount();
            else if (attribute.equals(Attributes.ARMOR_TOUGHNESS)) armor[1] += modifier.amount();
         });
         int remaining = stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
         candidates.add(new SmartElytraSwap.Candidate(slot, glider, remaining, armor[0], armor[1]));
      }
      int slot = SmartElytraSwap.best(candidates, glider);
      if (slot < 0) return;
      MultiPlayerGameMode gameMode = this.minecraft().gameMode;
      int containerId = player.inventoryMenu.containerId;
      int chestMenuSlot = chestSlot.index;
      if (Inventory.isHotbarSlot(slot) || slot == Inventory.SLOT_OFFHAND) {
         // A single click exchanges both items, even with a completely full inventory.
         gameMode.handleInventoryMouseClick(containerId, chestMenuSlot, slot, ClickType.SWAP, player);
      } else {
         // Main inventory slots 9-35 share their index with the inventory menu.
         gameMode.handleInventoryMouseClick(containerId, slot, 0, ClickType.PICKUP, player);
         gameMode.handleInventoryMouseClick(containerId, chestMenuSlot, 0, ClickType.PICKUP, player);
         if (!player.inventoryMenu.getCarried().isEmpty()) {
            gameMode.handleInventoryMouseClick(containerId, slot, 0, ClickType.PICKUP, player);
         }
      }
      if (glider) this.boostDelay.swapped();
   }

   private void observeBoostDelay(LocalPlayer player) {
      this.boostDelay.observe(this.clientTicks, isGlider(player.getItemBySlot(EquipmentSlot.CHEST)), player.isFallFlying());
   }

   private static boolean isGlider(ItemStack stack) {
      return !stack.isEmpty() && stack.has(DataComponents.GLIDER);
   }

   /** Remaining durability of a glider (unbreakable ones never wear out), otherwise -1. */
   private static int gliderRemaining(ItemStack stack) {
      if (!isGlider(stack)) return -1;
      return stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
   }

   @Override
   public void handleIncomingPacket(PacketEvent.Receive event) {
      if (!(event.getPacket() instanceof ClientboundPlayerPositionPacket)) return;

      this.positionCorrections++;
      this.cometCorrections.incrementAndGet();
      Minecraft minecraft = this.minecraft();
      LocalPlayer player = minecraft.player;
      ClientLevel world = minecraft.level;
      boolean inFlight = player != null && world != null && player.isFallFlying();

      if (inFlight && this.rubberbandSpeedCap.getValue()) {
         double speedBps = Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z) * 20.0;
         double thresholdBps = this.rubberbandSpeedCapThreshold.getValue();
         double toleranceBps = thresholdBps * (RUBBERBAND_CAP_TOLERANCE_PERCENT / 100.0);

         if (speedBps >= thresholdBps && speedBps <= thresholdBps + toleranceBps) {
            long now = System.nanoTime();
            this.rubberbandQualifyingTimestamps.addLast(now);
            while (!this.rubberbandQualifyingTimestamps.isEmpty()
               && now - this.rubberbandQualifyingTimestamps.peekFirst() > RUBBERBAND_CAP_TRIGGER_WINDOW_NANOS) {
               this.rubberbandQualifyingTimestamps.pollFirst();
            }

            if (this.rubberbandQualifyingTimestamps.size() >= RUBBERBAND_CAP_TRIGGER_COUNT) {
               this.rubberbandCapTicksLeft = (int)Math.round(RUBBERBAND_CAP_DURATION_SECONDS * 20.0);
               this.rubberbandCapSpeed = thresholdBps / 20.0;
               this.rubberbandQualifyingTimestamps.clear();
            }
         }
      }

      if (inFlight) {
         double keptSpeed = rewindSpeedFor(this.acceleration, this.accelerationStep.getValue(), this.accelerationPlateau.getValue(), this.effectiveHorizontalStartSpeed(), SPEED_PRESERVATION_REWIND_TICKS);
         if (keptSpeed < this.acceleration) {
            this.acceleration = keptSpeed;
            this.atMaxSpeed = false;
            this.accelerationDelayTicks = 0;

            this.verticalAcceleration = rewindSpeedFor(this.verticalAcceleration, this.verticalAccelerationStep.getValue(), this.verticalAccelerationPlateau.getValue(), this.effectiveVerticalStartSpeed(), SPEED_PRESERVATION_REWIND_TICKS);
            this.atMaxVerticalSpeed = false;
            this.verticalAccelerationDelayTicks = 0;
         }
         return;
      }

      this.zeroAcceleration();
   }

   private void autoTakeoff(LocalPlayer player) {
      if (this.incrementJumpTimer) this.jumpTimer++;

      this.jumpPressed = this.minecraft().options.keyJump.isDown();

      // Smart Elytra owns the jump-to-flight path; don't let the older auto-takeoff
      // setting fire a second takeoff from the same jump.
      if (this.autoTakeOff.getValue() && !this.smartElytra.getValue() && this.jumpPressed) {
         if (!this.lastJumpPressed && !player.isFallFlying()) {
            this.jumpTimer = 0;
            this.incrementJumpTimer = true;
         }

         if (this.jumpTimer >= 8) {
            this.jumpTimer = 0;
            this.incrementJumpTimer = false;
            player.setJumping(false);
            player.setSprinting(true);
            player.jumpFromGround();
            player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
         }
      }

      this.lastJumpPressed = this.jumpPressed;
   }

   private void handleAutopilot(LocalPlayer player) {
      if (!player.isFallFlying()) return;

      if (!this.avoidanceSteering) {
         this.releaseMovementKeys();
         this.releaseVerticalKeys();
         if (this.autoPilot.getValue() && player.getY() > this.autoPilotMinimumHeight.getValue()) {
            this.setKey(this.minecraft().options.keyUp, true);
            this.lastForwardPressed = true;
         }
      }

      if (this.useFireworks.getValue()) {
         if (this.ticksLeft <= 0) {
            this.ticksLeft = (int)Math.round(this.autoPilotFireworkDelay.getValue() * 20.0);
            if (!this.useFirework(player)) return;
         }
         this.ticksLeft--;
      }
   }

   private boolean useFirework(LocalPlayer player) {
      Minecraft minecraft = this.minecraft();
      if (minecraft.gameMode == null) return false;

      if (player.getOffhandItem().is(Items.FIREWORK_ROCKET)) {
         minecraft.gameMode.useItem(player, InteractionHand.OFF_HAND);
         player.swing(InteractionHand.OFF_HAND);
         return true;
      }

      int slot = InventoryUtils.findHotbarSlot(Items.FIREWORK_ROCKET);
      if (slot < 0) return false;

      int previousSlot = InventoryUtils.getSelectedSlot();
      InventoryUtils.selectHotbarSlot(slot);
      minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND);
      player.swing(InteractionHand.MAIN_HAND);
      InventoryUtils.selectHotbarSlot(previousSlot);
      return true;
   }

   private void handleHorizontalSpeed() {
      boolean forwardPressed = false;
      boolean sidewaysPressed = false;

      if (this.minecraft().options.keyUp.isDown()) {
         this.velX += this.forward.x * this.acceleration * 10.0;
         this.velZ += this.forward.z * this.acceleration * 10.0;
         forwardPressed = true;
      } else if (this.minecraft().options.keyDown.isDown()) {
         this.velX -= this.forward.x * this.acceleration * 10.0;
         this.velZ -= this.forward.z * this.acceleration * 10.0;
         forwardPressed = true;
      }

      if (this.minecraft().options.keyRight.isDown()) {
         this.velX += this.right.x * this.acceleration * 10.0;
         this.velZ += this.right.z * this.acceleration * 10.0;
         sidewaysPressed = true;
      } else if (this.minecraft().options.keyLeft.isDown()) {
         this.velX -= this.right.x * this.acceleration * 10.0;
         this.velZ -= this.right.z * this.acceleration * 10.0;
         sidewaysPressed = true;
      }

      if (forwardPressed && sidewaysPressed) {
         double diagonal = 1.0 / Math.sqrt(2.0);
         this.velX *= diagonal;
         this.velZ *= diagonal;
      }

   }

   private void handleVerticalSpeed() {
      if (this.minecraft().options.keyJump.isDown()) {
         this.velY += 0.5 * this.verticalAcceleration;
      } else if (this.minecraft().options.keyShift.isDown()) {
         this.velY -= 0.5 * this.verticalAcceleration;
      }
   }

   private void handleFallMultiplier() {
      if (this.velY < 0.0) {
         this.velY *= this.fallMultiplier.getValue();
      } else if (this.velY > 0.0) {
         this.velY = 0.0;
      }
   }

   private void handleAntiSlam(ClientLevel world, LocalPlayer player) {
      if (!this.antiSlam.getValue() || this.velY >= 0.0) return;

      double currentSpeed = -this.velY;
      double minSpeed = this.antiSlamMinSpeed.getValue();
      if (currentSpeed <= minSpeed) return;

      double startDist = this.antiSlamDistance.getValue();
      double minDist = Math.min(this.antiSlamMinDistance.getValue(), startDist);

      double distanceToGround = this.distanceToGroundBelow(world, player, startDist);
      if (distanceToGround >= startDist) return;

      double allowedSpeed;
      if (distanceToGround <= minDist) {
         allowedSpeed = minSpeed;
      } else {
         double t = (distanceToGround - minDist) / Math.max(startDist - minDist, 0.0001);
         allowedSpeed = minSpeed + t * (currentSpeed - minSpeed);
      }

      if (allowedSpeed < currentSpeed) {
         this.velY = -allowedSpeed;
      }
   }

   private double distanceToGroundBelow(ClientLevel world, LocalPlayer player, double maxDistance) {
      AABB box = player.getBoundingBox();
      double y = player.getY();
      double[] xs = {box.minX, (box.minX + box.maxX) / 2.0, box.maxX};
      double[] zs = {box.minZ, (box.minZ + box.maxZ) / 2.0, box.maxZ};
      double nearest = maxDistance;

      for (double x : xs) {
         for (double z : zs) {
            Vec3 start = new Vec3(x, y, z);
            Vec3 end = start.add(0.0, -maxDistance, 0.0);
            BlockHitResult hit = world.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) continue;

            double distance = y - hit.getLocation().y;
            if (distance < nearest) nearest = distance;
         }
      }
      return nearest;
   }

   private void handleMaxHeight(LocalPlayer player) {
      if (!this.limitMaxHeight.getValue() || this.velY <= 0.0) return;

      double limit = this.maxHeight.getValue();
      double currentY = player.getY();
      if (currentY >= limit) {
         this.velY = 0.0;
      } else if (currentY + this.velY > limit) {
         this.velY = limit - currentY;
      }
   }

   private boolean isMovementKeyPressed() {
      return this.minecraft().options.keyUp.isDown()
         || this.minecraft().options.keyDown.isDown()
         || this.minecraft().options.keyLeft.isDown()
         || this.minecraft().options.keyRight.isDown();
   }

   private void resetRampOnCollision(LocalPlayer player) {
      if (this.lastGlidePos == null) return;
      if (!player.horizontalCollision) return;
      if (this.lastGlideIntendedSpeed < 1.0E-3) return;

      Vec3 now = player.position();
      double moved = Math.hypot(now.x - this.lastGlidePos.x, now.z - this.lastGlidePos.z);
      if (moved >= this.lastGlideIntendedSpeed - 1.0E-3) return;

      double kept = Math.max(this.effectiveHorizontalStartSpeed(), moved);
      if (kept < this.acceleration) this.acceleration = kept;
      double cap = this.effectiveHorizontalSpeedCap();
      this.atMaxSpeed = this.acceleration >= cap;
      this.accelerationDelayTicks = 0;
   }

   private void handleAcceleration() {
      if (!this.isMovementKeyPressed()) {
         this.resetHorizontalAcceleration();
         return;
      }

      double cap = this.effectiveHorizontalSpeedCap();
      if (this.acceleration > cap) {
         this.acceleration = cap;
         this.atMaxSpeed = true;
         this.accelerationDelayTicks = 0;
         return;
      }
      if (this.atMaxSpeed && this.acceleration >= cap) return;
      if (this.acceleration < cap) this.atMaxSpeed = false;

      if (this.accelerationDelayTicks < this.accelerationDelay.getValue()) {
         this.accelerationDelayTicks++;
         return;
      }

      this.acceleration = nextSpeedFor(this.acceleration, this.accelerationStep.getValue(), this.accelerationPlateau.getValue(), cap);
      if (this.acceleration >= cap) {
         this.atMaxSpeed = true;
      }
   }

   private static double curveGain(double currentSpeed, double step, double plateau) {
      if (plateau <= 0.0) return 0.0;
      double remainingToPlateau = Math.max(0.0, plateau - currentSpeed);
      return step * (remainingToPlateau / plateau);
   }

   private static double nextSpeedFor(double currentSpeed, double step, double plateau, double cap) {
      return Math.min(currentSpeed + curveGain(currentSpeed, step, plateau), cap);
   }

   private static double rewindSpeedFor(double currentSpeed, double step, double plateau, double floor, int ticks) {
      double speed = currentSpeed;
      for (int i = 0; i < ticks && speed > floor; i++) {
         speed = Math.max(floor, speed - curveGain(speed, step, plateau));
      }
      return speed;
   }

   private void zeroAcceleration() {
      this.resetHorizontalAcceleration();
      this.resetVerticalAcceleration();
   }

   private void resetVerticalAcceleration() {
      this.atMaxVerticalSpeed = false;
      this.verticalAccelerationDelayTicks = 0;
      this.verticalAcceleration = this.effectiveVerticalStartSpeed();
   }

   private void resetHorizontalAcceleration() {
      this.atMaxSpeed = false;
      this.accelerationDelayTicks = 0;
      this.acceleration = this.effectiveHorizontalStartSpeed();
   }

   /** The boost key (Sprint unless rebound) held physically while playing, with no screen open. */
   private boolean boostKeyHeld() {
      Minecraft minecraft = this.minecraft();
      return minecraft.screen == null && this.boostKey.isPhysicallyDown(minecraft.options.keySprint);
   }

   /** Boost H Speed in blocks/tick; at the slider's top ("Volytra Max") it is Volytra's own Horizontal Speed. */
   private double boostHorizontalCap() {
      return this.boostHorizontalSpeed.isAtMaximum()
         ? this.horizontalSpeed.getValue()
         : Math.min(this.boostHorizontalSpeed.getValue(), BOOST_H_NUMERIC_MAX) / 20.0;
   }

   private boolean boostActive() {
      return this.customSpeeds.getValue() && this.boostKeyHeld();
   }

   private double effectiveHorizontalSpeedCap() {
      double cap = this.customSpeeds.getValue()
         ? (this.boostActive() ? this.boostHorizontalCap() : this.baseHorizontalSpeed.getValue() / 20.0)
         : this.horizontalSpeed.getValue();
      if (this.rubberbandCapTicksLeft > 0) cap = Math.min(cap, this.rubberbandCapSpeed);
      return Math.max(0.0, cap);
   }

   private double effectiveVerticalSpeedCap() {
      double cap = this.customSpeeds.getValue()
         ? (this.boostActive() ? this.boostVerticalSpeed.getValue() : this.baseVerticalSpeed.getValue()) / 20.0
         : this.verticalSpeed.getValue();
      return Math.max(0.0, cap);
   }

   private double effectiveHorizontalStartSpeed() {
      return this.customSpeeds.getValue()
         ? Math.min(this.startSpeed.getValue(), this.effectiveHorizontalSpeedCap())
         : this.startSpeed.getValue();
   }

   private double effectiveVerticalStartSpeed() {
      return this.customSpeeds.getValue()
         ? Math.min(this.verticalStartSpeed.getValue(), this.effectiveVerticalSpeedCap())
         : this.verticalStartSpeed.getValue();
   }

   private void handleVerticalAcceleration() {
      boolean movingUp = this.minecraft().options.keyJump.isDown();
      boolean movingDown = this.minecraft().options.keyShift.isDown();

      if (!movingUp && !movingDown) {
         this.resetVerticalAcceleration();
         return;
      }

      if (movingUp && !this.accelerateUpward.getValue()) {
         this.resetVerticalAcceleration();
         return;
      }

      double cap = this.effectiveVerticalSpeedCap();
      if (this.cometMotion.getValue()) {
         if (this.verticalAcceleration > cap) {
            this.verticalAcceleration = Math.max(cap, this.verticalAcceleration - this.cometBraking.getValue());
            this.atMaxVerticalSpeed = this.verticalAcceleration >= cap;
            return;
         }
         if (this.verticalAccelerationDelayTicks < this.accelerationDelay.getValue()) {
            this.verticalAccelerationDelayTicks++;
            return;
         }
         if (this.verticalAcceleration < cap) {
            this.verticalAcceleration = Math.min(cap, this.verticalAcceleration + this.cometAcceleration.getValue());
         }
         this.atMaxVerticalSpeed = this.verticalAcceleration >= cap;
         return;
      }

      if (this.verticalAcceleration > cap) {
         this.verticalAcceleration = cap;
         this.atMaxVerticalSpeed = true;
         this.verticalAccelerationDelayTicks = 0;
         return;
      }
      if (this.atMaxVerticalSpeed && this.verticalAcceleration >= cap) return;
      if (this.verticalAcceleration < cap) this.atMaxVerticalSpeed = false;

      if (this.verticalAccelerationDelayTicks < this.accelerationDelay.getValue()) {
         this.verticalAccelerationDelayTicks++;
         return;
      }

      this.verticalAcceleration = nextSpeedFor(this.verticalAcceleration, this.verticalAccelerationStep.getValue(), this.verticalAccelerationPlateau.getValue(), cap);
      if (this.verticalAcceleration >= cap) {
         this.atMaxVerticalSpeed = true;
      }
   }

   private void updatePlayerAvoidance(LocalPlayer player) {
      if (!this.playerAvoidance.getValue()) {
         this.releaseAvoidance();
         this.releaseVerticalStep();
         return;
      }

      if (this.isMovementKeyPhysicallyPressed() || this.isKeyPhysicallyPressed(this.minecraft().options.keyShift)) {
         this.releaseAvoidance();
         this.releaseVerticalStep();
         return;
      }

      Vec3 away = this.findAvoidanceAwayVector(player);
      if (away == null) {
         this.releaseAvoidance();
         this.releaseVerticalStep();
         return;
      }

      if (!player.isFallFlying()) {
         this.releaseAvoidance();
         this.releaseVerticalStep();
         return;
      }

      if (this.verticalStepActive) {
         this.avoidanceSteering = true;
         this.stepVerticalStep(player);
         return;
      }

      this.steerTowardsDirection(player, away);
      this.avoidanceSteering = true;
      this.updateAvoidanceStuckDetection(player);
   }

   private void steerTowardsDirection(LocalPlayer player, Vec3 dir) {
      double targetYaw = Math.toDegrees(Math.atan2(-dir.x, dir.z));
      double relative = wrapDegrees(targetYaw - player.getYRot());
      int octant = ((int)Math.round(relative / 45.0) % 8 + 8) % 8;

      this.setKey(this.minecraft().options.keyUp, octant == 0 || octant == 1 || octant == 7);
      this.setKey(this.minecraft().options.keyDown, octant == 3 || octant == 4 || octant == 5);
      this.setKey(this.minecraft().options.keyRight, octant == 1 || octant == 2 || octant == 3);
      this.setKey(this.minecraft().options.keyLeft, octant == 5 || octant == 6 || octant == 7);
   }

   private Vec3 findAvoidanceAwayVector(LocalPlayer player) {
      ClientLevel world = this.minecraft().level;
      if (world == null) return null;

      double[] acc = {0.0, 0.0};
      boolean foundThreat = false;

      for (AbstractClientPlayer other : world.players()) {
         if (other == player) continue;
         if (this.avoidanceIgnoreFriends.getValue() && ClientContext.socialRegistry.isFriendPlayer(other)) continue;
         if (this.accumulateThreat(player, other.position(), this.avoidanceRadius.getValue(), acc)) foundThreat = true;
      }

      if (this.avoidWitherSkulls.getValue() || this.avoidArrows.getValue()) {
         for (Entity entity : world.entitiesForRendering()) {
            if (this.avoidWitherSkulls.getValue() && entity instanceof WitherSkull) {
               if (this.accumulateThreat(player, entity.position(), this.witherSkullRadius.getValue(), acc)) foundThreat = true;
            } else if (this.avoidArrows.getValue() && entity instanceof Arrow) {
               if (this.accumulateThreat(player, entity.position(), this.arrowRadius.getValue(), acc)) foundThreat = true;
            }
         }
      }

      if (this.avoidBlocks.getValue() && this.isBlockAvoidanceTriggered(player)) {
         if (this.accumulateNearbyBlockThreats(player, this.blockAvoidanceRadius.getValue(), acc)) foundThreat = true;
      }

      if (!foundThreat) return null;

      Vec3 direction = new Vec3(acc[0], 0.0, acc[1]);
      if (direction.lengthSqr() == 0.0) return null;
      direction = direction.normalize();

      if (this.avoidanceLateral.getValue()) {
         direction = this.lateralize(player, direction);
      } else {
         this.avoidanceLateralDir = null;
      }

      return direction;
   }

   private boolean isBlockAvoidanceTriggered(LocalPlayer player) {
      ClientLevel world = this.minecraft().level;
      if (world == null) return false;

      Vec3 selfPos = player.position();
      double radius = this.avoidanceRadius.getValue();
      if (radius > 0.0) {
         double radiusSq = radius * radius;
         for (AbstractClientPlayer other : world.players()) {
            if (other == player) continue;
            if (this.avoidanceIgnoreFriends.getValue() && ClientContext.socialRegistry.isFriendPlayer(other)) continue;
            if (selfPos.distanceToSqr(other.position()) < radiusSq) return true;
         }
      }

      double skullRadius = this.witherSkullRadius.getValue();
      if (this.avoidWitherSkulls.getValue() && skullRadius > 0.0) {
         double skullRadiusSq = skullRadius * skullRadius;
         for (Entity entity : world.entitiesForRendering()) {
            if (entity instanceof WitherSkull && selfPos.distanceToSqr(entity.position()) < skullRadiusSq) return true;
         }
      }

      return false;
   }

   private Vec3 lateralize(LocalPlayer player, Vec3 away) {
      Vec3 perpA = new Vec3(-away.z, 0.0, away.x);
      Vec3 perpB = new Vec3(away.z, 0.0, -away.x);

      boolean sideA;
      if (this.avoidanceLateralDir != null) {
         sideA = this.avoidanceLateralDir.dot(perpA) >= this.avoidanceLateralDir.dot(perpB);
      } else {
         Vec3 velocity = player.getDeltaMovement();
         Vec3 horizontalVelocity = new Vec3(velocity.x, 0.0, velocity.z);
         sideA = !(horizontalVelocity.lengthSqr() > 1.0E-4 && horizontalVelocity.dot(perpB) > horizontalVelocity.dot(perpA));
      }

      double baseAngle = sideA ? 90.0 : -90.0;
      Vec3 optionA = rotateHorizontal(away, baseAngle - 10.0);
      Vec3 optionB = rotateHorizontal(away, baseAngle + 10.0);
      Vec3 chosen = optionA.dot(away) >= optionB.dot(away) ? optionA : optionB;

      this.avoidanceLateralDir = chosen;
      return chosen;
   }

   private static Vec3 rotateHorizontal(Vec3 vector, double degrees) {
      double radians = Math.toRadians(degrees);
      double cos = Math.cos(radians);
      double sin = Math.sin(radians);
      return new Vec3(vector.x * cos - vector.z * sin, 0.0, vector.x * sin + vector.z * cos);
   }

   private boolean accumulateThreat(LocalPlayer player, Vec3 threatPos, double radius, double[] acc) {
      if (radius <= 0.0) return false;

      double distanceSq = player.position().distanceToSqr(threatPos);
      if (distanceSq >= radius * radius || distanceSq == 0.0) return false;

      Vec3 awayFromThreat = player.position().subtract(threatPos);
      awayFromThreat = new Vec3(awayFromThreat.x, 0.0, awayFromThreat.z);
      if (awayFromThreat.lengthSqr() == 0.0) return false;

      double weight = 1.0 - (Math.sqrt(distanceSq) / radius);
      awayFromThreat = awayFromThreat.normalize().scale(weight);
      acc[0] += awayFromThreat.x;
      acc[1] += awayFromThreat.z;
      return true;
   }

   private boolean accumulateNearbyBlockThreats(LocalPlayer player, double radius, double[] acc) {
      ClientLevel world = this.minecraft().level;
      if (world == null || radius <= 0.0) return false;

      boolean foundThreat = false;
      BlockPos center = player.blockPosition();
      int r = (int)Math.ceil(radius);
      BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

      for (int dx = -r; dx <= r; dx++) {
         for (int dy = 0; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
               pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
               var state = world.getBlockState(pos);
               boolean isCobweb = state.is(Blocks.COBWEB);
               if (!isCobweb && state.getCollisionShape(world, pos).isEmpty()) continue;

               Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
               if (this.accumulateThreat(player, blockCenter, radius, acc)) foundThreat = true;
            }
         }
      }
      return foundThreat;
   }

   private void updateAvoidanceStuckDetection(LocalPlayer player) {
      this.avoidanceStuckTicksCount = player.horizontalCollision ? this.avoidanceStuckTicksCount + 1 : 0;
      if (this.avoidanceStuckTicksCount < AVOIDANCE_STUCK_TICKS) return;
      this.beginVerticalStep(player);
   }

   private void beginVerticalStep(LocalPlayer player) {
      boolean upClear = this.hasVerticalClearance(player, 1.0);
      boolean downClear = this.hasVerticalClearance(player, -1.0);

      if (upClear) {
         this.verticalStepUp = true;
      } else if (downClear) {
         this.verticalStepUp = false;
      } else {
         this.avoidanceStuckTicksCount = 0;
         return;
      }

      this.verticalStepActive = true;
      this.verticalStepStartY = player.getY();
      this.verticalStepTicks = 0;
      this.avoidanceStuckTicksCount = 0;
      this.releaseHorizontalKeys();
   }

   private boolean hasVerticalClearance(LocalPlayer player, double dy) {
      ClientLevel world = this.minecraft().level;
      if (world == null) return false;
      AABB box = player.getBoundingBox().move(0.0, dy, 0.0);
      return !world.getBlockCollisions(player, box).iterator().hasNext();
   }

   private void stepVerticalStep(LocalPlayer player) {
      this.verticalStepTicks++;
      double traveled = Math.abs(player.getY() - this.verticalStepStartY);
      if (traveled >= 1.0 || this.verticalStepTicks > VERTICAL_STEP_TIMEOUT_TICKS) {
         this.releaseVerticalStep();
         return;
      }

      if (!this.isKeyPhysicallyPressed(this.minecraft().options.keyJump)) {
         this.setKey(this.minecraft().options.keyJump, this.verticalStepUp);
      }
      if (!this.isKeyPhysicallyPressed(this.minecraft().options.keyShift)) {
         this.setKey(this.minecraft().options.keyShift, !this.verticalStepUp);
      }
   }

   private void releaseVerticalStep() {
      this.avoidanceStuckTicksCount = 0;

      if (this.verticalStepActive) {
         if (!this.isKeyPhysicallyPressed(this.minecraft().options.keyJump)) this.setKey(this.minecraft().options.keyJump, false);
         if (!this.isKeyPhysicallyPressed(this.minecraft().options.keyShift)) this.setKey(this.minecraft().options.keyShift, false);
      }

      this.verticalStepActive = false;
      this.verticalStepTicks = 0;
   }

   private void releaseHorizontalKeys() {
      if (!this.isKeyPhysicallyPressed(this.minecraft().options.keyUp)) this.setKey(this.minecraft().options.keyUp, false);
      if (!this.isKeyPhysicallyPressed(this.minecraft().options.keyDown)) this.setKey(this.minecraft().options.keyDown, false);
      if (!this.isKeyPhysicallyPressed(this.minecraft().options.keyLeft)) this.setKey(this.minecraft().options.keyLeft, false);
      if (!this.isKeyPhysicallyPressed(this.minecraft().options.keyRight)) this.setKey(this.minecraft().options.keyRight, false);
   }

   private void releaseMovementKeys() {
      this.releaseHorizontalKeys();
   }

   private void releaseVerticalKeys() {
      if (!this.isKeyPhysicallyPressed(this.minecraft().options.keyJump)) this.setKey(this.minecraft().options.keyJump, false);
      if (!this.isKeyPhysicallyPressed(this.minecraft().options.keyShift)) this.setKey(this.minecraft().options.keyShift, false);
   }

   private void releaseAllForcedKeys() {
      this.releaseHorizontalKeys();
      this.releaseVerticalKeys();
   }

   private void releaseAvoidance() {
      this.avoidanceLateralDir = null;
      if (!this.avoidanceSteering) return;
      this.releaseHorizontalKeys();
      this.avoidanceSteering = false;
   }

   private boolean isMovementKeyPhysicallyPressed() {
      return this.isKeyPhysicallyPressed(this.minecraft().options.keyUp)
         || this.isKeyPhysicallyPressed(this.minecraft().options.keyDown)
         || this.isKeyPhysicallyPressed(this.minecraft().options.keyLeft)
         || this.isKeyPhysicallyPressed(this.minecraft().options.keyRight);
   }

   private boolean isKeyPhysicallyPressed(KeyMapping mapping) {
      return KeyUtils.isPhysicallyDown(mapping);
   }

   private void setKey(KeyMapping mapping, boolean down) {
      mapping.setDown(down);
   }

   private boolean canManageInventory(LocalPlayer player) {
      return this.minecraft().screen == null && player.containerMenu instanceof InventoryMenu;
   }

   private Minecraft minecraft() {
      return ClientAccess.argusclientMinecraft;
   }

   private static double wrapDegrees(double degrees) {
      double wrapped = degrees % 360.0;
      if (wrapped >= 180.0) wrapped -= 360.0;
      if (wrapped < -180.0) wrapped += 360.0;
      return wrapped;
   }
}
