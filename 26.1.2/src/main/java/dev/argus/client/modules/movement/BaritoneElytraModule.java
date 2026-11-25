package dev.argus.client.modules.movement;

import dev.argus.client.ArgusClient;
import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.LocalPlayerPreTickEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.integrations.baritone.BaritoneBridge;
import dev.argus.client.integrations.baritone.BaritoneBridge.WalkingTrip;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.modules.movement.BaritoneElytraHandoff.End;
import dev.argus.client.modules.movement.BaritoneElytraHandoff.Refusal;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.chat.ChatUtils;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.movement.ElytraEscapeRun;
import dev.argus.client.utils.movement.ElytraPathAim;
import java.awt.Color;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Turns a long Baritone #goto into a Baritone elytra flight, powers it at one set speed along Baritone's heading so no
 * fireworks are needed, and walks the rest after landing. Ported and cut down from Comet's Baritone Elytra.
 */
public class BaritoneElytraModule extends Module {
   private static final String LANDING = "Elytra - Landing";
   /** Ticks Baritone's last heading is kept when it gives none, before the route takes over. */
   private static final int HEADING_GAP_TICKS = 20;
   /** How often a goal refused for a reason that can clear up is checked again. */
   private static final int RECHECK_TICKS = 10;
   /** Room needed above the player to jump and open the elytra. */
   private static final double HEADROOM = 3.0;
   /** Chest armour slot in the player's inventory menu. */
   private static final int CHEST_MENU_SLOT = 6;
   /** Step for the straight-line clearance check along the route, smaller than the player's hitbox. */
   private static final double CLEARANCE_STEP = 0.5;
   /** Route nodes further than this aren't checked for a clear way, to bound the work per tick. */
   private static final double MAX_CLEARANCE_DISTANCE = 96.0;
   /** True while a flight this module manages is in progress; ElytraFly stands aside for it. */
   private static volatile boolean controllingFlight;

   private final Setting<Integer> minDistanceSetting = this.add(new NumberSetting<>("MinDistance", 300, 32, 2000).withUnit("m"));
   private final Setting<Float> speedSetting = this.add(new NumberSetting<>("Speed", 40.0F, 5.0F, 120.0F).withUnit("b/s"));
   private final Setting<Boolean> strictRouteSetting = this.add(new BooleanSetting("StrictRoute", false).markVisibilityDependency());
   private final Setting<Float> strictToleranceSetting = this.add(new NumberSetting<>("StrictTolerance", 0.2F, 0.05F, 4.0F,
      value -> this.strictRouteSetting.getValue()).withUnit("m"));
   private final Setting<Boolean> takeOffSetting = this.add(new BooleanSetting("TakeOff", true));
   private final Setting<Integer> escapeDistanceSetting = this.add(new NumberSetting<>("EscapeDistance", 48, 8, 512).withUnit("m"));
   private final Setting<Boolean> equipSetting = this.add(new BooleanSetting("EquipElytra", true));
   private final Setting<Boolean> flyThroughLavaSetting = this.add(new BooleanSetting("FlyThroughLava", true).markVisibilityDependency());
   private final Setting<Boolean> thinLavaOnlySetting = this.add(new BooleanSetting("ThinLavaOnly", true, value -> this.flyThroughLavaSetting.getValue()));
   private final Setting<Boolean> drinkFireResistanceSetting = this.add(new BooleanSetting("DrinkFireResistance", true));
   private final Setting<Float> lavaMinHealthSetting = this.add(new NumberSetting<>("LavaMinHealth", 14.0F, 0.0F, 20.0F,
      value -> this.flyThroughLavaSetting.getValue()));
   private final Setting<Boolean> showRouteSetting = this.add(new BooleanSetting("ShowRoute", true).markVisibilityDependency());
   private final Setting<Color> routeColorSetting = this.add(new ColorSetting("RouteColor", new Color(80, 200, 255, 220), color -> this.showRouteSetting.getValue()));
   private final BaritoneTakeoff takeoff = new BaritoneTakeoff("BaritoneElytra");
   private final ElytraFlightRecovery recovery = new ElytraFlightRecovery("BaritoneElytra");
   private boolean listening;
   private final ElytraLavaSafety lavaSafety = new ElytraLavaSafety();
   private final ElytraRouteCheck routeCheck = new ElytraRouteCheck();
   private final ElytraEscapeRun escape = new ElytraEscapeRun();
   private final CrawlOut crawl = new CrawlOut("BaritoneElytra");
   /** Baritone is paused by this module while it crawls the player out. */
   private boolean crawlPaused;
   private boolean crawlWarned;
   private int nextBlockedLog;
   private boolean lavaRoutingWarned;
   private boolean lavaWarned;
   /** Take-offs again after landing short, since the last one that got into a steady glide. */
   private int relaunches;
   /** Landing short is followed by a new take-off at most this many times in a row. */
   private static final int MAX_RELAUNCHES = 3;

   private BaritoneBridge baritone;
   /** The trip being flown, or null. */
   private WalkingTrip trip;
   private boolean sawLanding;
   /** Goal that is walked to the end and not checked again. */
   private Object settled;
   /** Goal refused for a reason that can clear up; checked again every {@link #RECHECK_TICKS}. */
   private Object waiting;
   private Refusal waitingReason;
   private int recheck;
   private float[] lastHeading;
   private int lastHeadingTick;

   public BaritoneElytraModule() {
      super("BaritoneElytra", "Flies long Baritone #goto trips with an elytra at a set speed, no fireworks needed, then walks the rest.",
         ModuleCategory.MOVEMENT);
   }

   /** True while this module, or an ElytraTrip, is flying a Baritone trip, so ElytraFly doesn't fight its steering. */
   public static boolean isControllingFlight() {
      return controllingFlight || dev.argus.client.modules.movement.elytratrip.TripFlight.isFlying();
   }

   @Override
   public String getInfo() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (this.trip == null || player == null) {
         return null;
      }
      if (this.sawLanding) {
         return "Landing";
      }
      return String.valueOf((int)this.remaining(player));
   }

   @Override
   public void onEnable() {
      this.reset();
      this.baritone = ClientContext.baritoneBridge;
      if (this.baritone == null || !this.baritone.isAvailable()) {
         this.message("Baritone isn't installed.");
         this.disable();
         return;
      }
      this.baritone.setRocketless(true);
      if (!this.listening) {
         // Baritone's chat says when its route has run out; the listener stays for the game and only acts during a trip.
         this.listening = true;
         this.baritone.addMessageListener(text -> this.trip != null && this.recovery.onBaritoneMessage(text));
      }
   }

   @Override
   public void onDisable() {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      try {
         if (this.trip != null && this.baritone != null && player != null && this.baritone.isElytraActive()) {
            // Cancelling mid-air would leave the player gliding with nobody steering.
            if (player.isFallFlying()) {
               this.message("Baritone keeps flying; #stop cancels it.");
            } else {
               this.baritone.cancelPathing();
            }
         }
      } catch (LinkageError | RuntimeException e) {
         ArgusClient.LOGGER.error("Stopping Baritone's elytra flight failed", e);
      } finally {
         if (this.baritone != null && this.baritone.isAvailable()) {
            this.releaseCrawlPause();
            this.baritone.setRocketless(false);
            this.baritone.setTightSpaces(false);
            this.baritone.setLavaRouting(false, true);
         }
         this.reset();
         this.baritone = null;
      }
   }

   private void reset() {
      this.lavaSafety.stopDrinking();
      this.escape.reset();
      this.crawl.reset();
      this.crawlWarned = false;
      this.lavaRoutingWarned = false;
      this.trip = null;
      controllingFlight = false;
      this.sawLanding = false;
      this.settled = this.waiting = null;
      this.waitingReason = null;
      this.recheck = 0;
      this.lastHeading = null;
   }

   @EventHandler
   private void onPreTick(LocalPlayerPreTickEvent event) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (player == null || ClientAccess.argusclientMinecraft.level == null || this.baritone == null) {
         return;
      }
      try {
         if (this.crawlOut(player)) {
            return;
         }
         if (this.trip != null) {
            this.tickFlight(player);
         } else {
            this.tickGround(player);
         }
      } catch (LinkageError | RuntimeException e) {
         ArgusClient.LOGGER.error("Handing Baritone's trip to its elytra flight failed", e);
         this.message("Baritone didn't accept the elytra hand-off (" + e + "). Turning off; see the log.");
         this.disable();
      }
   }

   /**
    * Stuck crawling under a ceiling during a trip, where Baritone can't walk: pauses Baritone and crawls to room to stand.
    * True while doing that, so nothing else runs this tick.
    */
   private boolean crawlOut(LocalPlayer player) {
      if (this.trip == null && this.baritone.walkingTrip() == null) {
         this.releaseCrawlPause();
         return false;
      }
      switch (this.crawl.tick(player)) {
         case CRAWLING -> {
            if (!this.crawlPaused) {
               this.crawlPaused = true;
               this.baritone.requestPause(this);
               this.message("Stuck crawling; crawling out to room to stand.");
            }
            return true;
         }
         case NO_WAY -> {
            this.releaseCrawlPause();
            if (!this.crawlWarned) {
               this.crawlWarned = true;
               this.message("Stuck crawling with nowhere to stand nearby. Break the block above you or move out by hand.");
            }
            return false;
         }
         default -> {
            this.releaseCrawlPause();
            this.crawlWarned = false;
            return false;
         }
      }
   }

   private void releaseCrawlPause() {
      if (this.crawlPaused) {
         this.crawlPaused = false;
         this.baritone.releasePause(this);
      }
   }

   private void tickGround(LocalPlayer player) {
      WalkingTrip walk = this.baritone.walkingTrip();
      if (walk == null || walk.goal() == this.settled) {
         return;
      }
      if (walk.goal() == this.waiting && ++this.recheck < RECHECK_TICKS) {
         return;
      }
      this.recheck = 0;
      Refusal refusal = this.refusal(player, walk);
      if (refusal == null) {
         this.launch(player, walk);
         return;
      }
      if (refusal.settled) {
         this.settled = walk.goal();
      } else if (walk.goal() == this.waiting && refusal == this.waitingReason) {
         return;
      } else {
         this.waiting = walk.goal();
         this.waitingReason = refusal;
      }
      this.report(refusal);
   }

   private Refusal refusal(LocalPlayer player, WalkingTrip walk) {
      int durability = elytraDurability(player.getItemBySlot(EquipmentSlot.CHEST));
      if (this.equipSetting.getValue()) {
         int slot = this.bestElytraMenuSlot(player);
         if (slot >= 0) {
            durability = Math.max(durability, elytraDurability(player.inventoryMenu.getSlot(slot).getItem()));
         }
      }
      boolean openAbove = ClientAccess.argusclientMinecraft.level.noCollision(player, player.getBoundingBox().expandTowards(0.0, HEADROOM, 0.0));
      // The take-off swims the player out of a liquid itself, so a liquid counts as somewhere to take off from.
      boolean fluid = player.isInWater() || player.isInLava();
      return BaritoneElytraHandoff.check(this.remaining(player, walk), this.minDistanceSetting.getValue(), this.baritone.isElytraSupported(),
         durability, this.baritone.elytraMinimumDurability(), player.isFallFlying(), player.onGround() || fluid, openAbove || fluid);
   }

   private void report(Refusal refusal) {
      switch (refusal) {
         case UNSUPPORTED -> this.message("Baritone has no elytra pathfinder for this system. Walking.");
         case NO_ELYTRA -> this.message("No elytra with at least " + this.baritone.elytraMinimumDurability()
            + " durability (Baritone's elytraMinimumDurability). Walking.");
         case COVERED -> this.message("Something is above you. Walking until there's room to take off.");
         case SHORT, AIRBORNE -> {
         }
      }
   }

   private void launch(LocalPlayer player, WalkingTrip walk) {
      this.equipElytra(player);
      try {
         this.baritone.flyTo(walk.target());
      } catch (IllegalArgumentException e) {
         this.settled = walk.goal();
         this.message("Baritone won't fly there: " + e.getMessage() + " Walking instead.");
         return;
      }
      this.trip = walk;
      controllingFlight = true;
      this.sawLanding = false;
      this.lastHeading = null;
      this.waiting = null;
      this.takeoff.reset(player.isFallFlying());
      this.recovery.reset();
      this.relaunches = 0;
      this.lavaSafety.reset();
      this.routeCheck.reset();
      // Escape runs aren't reset here: taking off after one comes through here too, and only a steady glide shows it worked.
      this.lavaWarned = false;
      this.updateLavaRouting();
      ArgusClient.LOGGER.info("BaritoneElytra: flying to {}, {} blocks away", walk.target(), (int)this.remaining(player));
      this.message("Flying to " + walk.target().getX() + ", " + walk.target().getZ() + ", " + (int)this.remaining(player) + " blocks away.");
   }

   private void tickFlight(LocalPlayer player) {
      if (this.escape.active()) {
         this.tickEscape(player);
         return;
      }
      if (LANDING.equals(this.baritone.elytraStatus())) {
         this.sawLanding = true;
      }
      if (!this.baritone.isElytraActive()) {
         this.finish(player);
         return;
      }
      this.updateLavaRouting();
      this.baritone.setTightSpaces(this.recovery.tightSpaces(player));
      if (this.recovery.takeEscapeDue() && this.startEscape(player, "Baritone finds no way out of here, even through tight spaces.")) {
         return;
      }
      BaritoneTakeoff.Result takeoffResult = this.takeoff.tick(player, this.baritone, this.takeOffSetting.getValue(),
         this.flyThroughLavaSetting.getValue(), this::message);
      if (takeoffResult == BaritoneTakeoff.Result.NO_WAY_OUT) {
         this.walkOut(player, "No way out of the liquid nearby.");
         return;
      }
      if (takeoffResult == BaritoneTakeoff.Result.TIMED_OUT) {
         WalkingTrip walk = this.trip;
         this.trip = null;
         controllingFlight = false;
         this.settled = walk.goal();
         this.message("Baritone couldn't find a flight path. Walking instead.");
         this.baritone.cancelPathing();
         this.baritone.walk(walk);
         return;
      }
      if (this.takeoff.stable()) {
         // A real glide since the last take-off: a later landing short is a new problem, not the same one again.
         this.relaunches = 0;
         this.escape.glided();
         if (!this.lavaRoutingWarned && this.flyThroughLavaSetting.getValue() && !this.baritone.lavaRoutingWorks()) {
            // Baritone has written terrain by the time the player is gliding, so the patch has had its chance to run.
            this.lavaRoutingWarned = true;
            this.message("This Baritone build can't route through lava, so routes still go round it. Meteor's baritone-meteor build can.");
         }
      }
      boolean wasAvoiding = this.lavaSafety.avoidLava();
      this.lavaSafety.tick(player, this.baritone.elytraRoute(), this.flyThroughLavaSetting.getValue(), this.drinkFireResistanceSetting.getValue(),
         this.lavaMinHealthSetting.getValue(), this::message);
      if (this.lavaSafety.avoidLava() != wasAvoiding) {
         this.updateLavaRouting();
      }
      if (!this.lavaWarned && player.isInLava() && !player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)) {
         this.lavaWarned = true;
         this.message("Flying through lava without Fire Resistance. Turn off FlyThroughLava to route around it.");
      }
      if (!this.baritone.isElytraSteering()) {
         return;
      }
      boolean landing = this.sawLanding || LANDING.equals(this.baritone.elytraStatus());
      if (!landing && this.routeCheck.replanDue(player, this.baritone.elytraRoute(), this.avoidLava())) {
         // Baritone planned through terrain it hadn't packed or predicted wrong; the loaded world says otherwise.
         ArgusClient.LOGGER.info("BaritoneElytra: route ahead runs through a block{} at {}; REPLAN", this.avoidLava() ? " or lava" : "",
            player.blockPosition());
         this.replanAround(player);
         return;
      }
      switch (this.recovery.tick(player, this.trip.target(), this.remaining(player), landing, this.hasUsableElytra(player))) {
         case REPLAN -> this.replan(player, false);
         case REBUILD -> this.replan(player, true);
         case UNSTICK -> this.unstick(player);
         case NONE -> {
         }
      }
   }

   /** Lava is routed through only when FlyThroughLava is on and health hasn't made this trip avoid it. */
   private void updateLavaRouting() {
      this.baritone.setLavaRouting(!this.avoidLava(), this.thinLavaOnlySetting.getValue());
   }

   /** True when lava is to be kept out of: FlyThroughLava is off, or health has made this trip avoid it. */
   private boolean avoidLava() {
      return !this.flyThroughLavaSetting.getValue() || this.lavaSafety.avoidLava();
   }

   /** Takes off again for the same trip after Baritone landed short of it. False when Baritone won't fly from here. */
   private boolean relaunch(LocalPlayer player, WalkingTrip walk, double remaining) {
      this.equipElytra(player);
      try {
         this.baritone.flyTo(walk.target());
      } catch (IllegalArgumentException e) {
         this.message("Baritone won't fly from here: " + e.getMessage());
         return false;
      }
      this.relaunches++;
      this.trip = walk;
      controllingFlight = true;
      this.settled = null;
      this.sawLanding = false;
      this.lastHeading = null;
      this.takeoff.reset(player.isFallFlying());
      this.recovery.routeStarted();
      ArgusClient.LOGGER.info("BaritoneElytra: landed {} blocks short at {}; taking off again, try {}", (int)remaining, player.blockPosition(),
         this.relaunches);
      this.message("Landed " + (int)remaining + " blocks short; taking off again.");
      return true;
   }

   /** Asks Baritone for a new route from here; {@code fresh} starts its pathfinder over as well. */
   private void replan(LocalPlayer player, boolean fresh) {
      if (this.recovery.firstWarning()) {
         this.message("Baritone's route isn't getting anywhere, so it's asking for another. #stop cancels.");
      }
      try {
         if (fresh) {
            this.baritone.flyFresh(this.trip.target());
         } else {
            this.baritone.flyTo(this.trip.target());
         }
      } catch (IllegalArgumentException e) {
         this.walkOut(player, "Baritone won't fly from here: " + e.getMessage());
         return;
      }
      this.recovery.routeStarted();
      this.lastHeading = null;
   }

   /**
    * A new route because the old one runs through a block. Baritone repacks the loaded chunks for it, and the stuck watch
    * keeps counting so a place no route gets out of still ends in walking out. Not a fresh pathfinder: tearing that down
    * cancels whatever route it's still working out, which Baritone reports as "Failed to compute path to destination".
    */
   private void replanAround(LocalPlayer player) {
      try {
         this.baritone.flyTo(this.trip.target());
      } catch (IllegalArgumentException e) {
         this.walkOut(player, "Baritone won't fly from here: " + e.getMessage());
         return;
      }
      this.recovery.routeReplaced();
      this.lastHeading = null;
   }

   /** The last resort: in the air, Baritone's own glide for a moment from a new pathfinder; on the ground, walk on. */
   private void unstick(LocalPlayer player) {
      if (this.startEscape(player, "Not getting anywhere from here.")) {
         return;
      }
      if (!player.isFallFlying()) {
         this.walkOut(player, "Not getting anywhere from here.");
         return;
      }
      this.recovery.coast(player.tickCount);
      this.replan(player, true);
   }

   /**
    * Lands if need be, runs EscapeDistance blocks towards the destination, and takes off again from there. False when it
    * can't: too many runs in a row already, or lava rather than ground below.
    */
   private boolean startEscape(LocalPlayer player, String reason) {
      if (!this.escape.canRun() || !ElytraRouteCheck.safeToLand(player)) {
         return false;
      }
      int distance = this.escapeDistanceSetting.getValue();
      this.baritone.cancelPathing();
      this.escape.start(player.getX(), player.getZ(), this.trip.target().getX() + 0.5, this.trip.target().getZ() + 0.5, distance, player.tickCount);
      this.message(reason + " Running " + distance + " blocks and taking off again.");
      ArgusClient.LOGGER.info("BaritoneElytra: escape run from {} towards {}, {}: {}", player.blockPosition(), this.escape.targetX(),
         this.escape.targetZ(), reason);
      return true;
   }

   private void tickEscape(LocalPlayer player) {
      boolean grounded = player.onGround() || player.isInWater() || player.isInLava();
      switch (this.escape.tick(player.getX(), player.getZ(), grounded, this.baritone.isPathingOrCalculating(), player.tickCount)) {
         case WALK -> this.baritone.walkToXZ(this.escape.targetX(), this.escape.targetZ());
         case DONE -> {
            this.baritone.cancelPathing();
            ArgusClient.LOGGER.info("BaritoneElytra: escape run ended at {}; taking off again", player.blockPosition());
            WalkingTrip walk = this.trip;
            this.trip = null;
            controllingFlight = false;
            this.settled = null;
            // The ground loop takes off again from here, once there's room above.
            this.waiting = walk.goal();
            this.waitingReason = Refusal.COVERED;
            this.recheck = RECHECK_TICKS;
            this.baritone.walk(walk);
         }
         case NONE -> {
         }
      }
   }

   /** Stops the flight and walks the trip, taking off again as soon as there's room. */
   private void walkOut(LocalPlayer player, String reason) {
      this.lavaSafety.stopDrinking();
      this.escape.cancel();
      this.baritone.setTightSpaces(false);
      WalkingTrip walk = this.trip;
      this.trip = null;
      controllingFlight = false;
      this.baritone.cancelPathing();
      this.baritone.walk(walk);
      // Checked again while walking, so it takes off once there's open sky.
      this.waiting = walk.goal();
      this.waitingReason = Refusal.COVERED;
      this.recheck = 0;
      this.message(reason + " Walking until there's room to take off.");
      ArgusClient.LOGGER.info("BaritoneElytra: walking out from {}: {}", player.blockPosition(), reason);
   }

   private boolean hasUsableElytra(LocalPlayer player) {
      int durability = elytraDurability(player.getItemBySlot(EquipmentSlot.CHEST));
      if (this.equipSetting.getValue()) {
         int slot = this.bestElytraMenuSlot(player);
         if (slot >= 0) {
            durability = Math.max(durability, elytraDurability(player.inventoryMenu.getSlot(slot).getItem()));
         }
      }
      return durability >= 0 && durability >= this.baritone.elytraMinimumDurability();
   }

   private void finish(LocalPlayer player) {
      this.lavaSafety.stopDrinking();
      this.baritone.setTightSpaces(false);
      double remaining = this.remaining(player);
      End end = BaritoneElytraHandoff.end(player.isFallFlying(), this.sawLanding, remaining, this.minDistanceSetting.getValue());
      WalkingTrip finished = this.trip;
      this.trip = null;
      controllingFlight = false;
      this.settled = finished.goal();
      switch (end) {
         case WALK_REST -> {
            if (remaining > 2.0) {
               this.message("Landed. Walking the last " + (int)remaining + " blocks.");
               this.baritone.walk(finished);
            } else {
               this.message("Arrived.");
            }
         }
         case LANDED_SHORT -> {
            if (!player.isDeadOrDying() && this.relaunches < MAX_RELAUNCHES && this.hasUsableElytra(player) && this.relaunch(player, finished, remaining)) {
               return;
            }
            this.message("Landed " + (int)remaining + " blocks short. Baritone lands early when elytra durability runs low; #goto again to carry on.");
         }
         case STOPPED -> {
            if (player.isFallFlying()) {
               this.message("Flight stopped. You're gliding on your own.");
            }
         }
      }
   }

   @EventHandler(priority = -400)
   private void onMove(LocalPlayerMoveEvent event) {
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (this.crawlPaused && player != null) {
         this.crawl.onMove(event, player);
         return;
      }
      if (this.trip == null || this.baritone == null || player == null || event.getMoverType() != MoverType.SELF
         || !player.isFallFlying() || player.isInWater() || player.isPassenger()) {
         return;
      }
      try {
         if (this.escape.landing()) {
            // Coming down slowly to run somewhere else; straight down was checked to be ground, not lava.
            event.setVelocity(ElytraRouteCheck.DESCENT);
            player.setDeltaMovement(ElytraRouteCheck.DESCENT);
            return;
         }
         if (LANDING.equals(this.baritone.elytraStatus())) {
            // Baritone's landing is an unpowered glide down onto the spot it picked.
            return;
         }
         if (player.isInLava() && !this.flyThroughLavaSetting.getValue()) {
            // Without FlyThroughLava, lava is something to get out of; the take-off swims out of it.
            return;
         }
         if (!this.baritone.isElytraSteering() || this.recovery.coasting(player.tickCount)) {
            // #pause or something else has Baritone, or it's gliding by itself to get unstuck: leave the player to it.
            return;
         }
         if (this.strictRouteSetting.getValue() && !this.takeoff.climbing()) {
            // Strict route: keep the body on Baritone's route, holding still rather than cutting a corner.
            Vec3 move = this.recovery.strictMove(player, this.baritone.elytraRoute(), this.speedSetting.getValue(),
               this.strictToleranceSetting.getValue(), this.avoidLava());
            if (move != null && move.lengthSqr() < 1.0E-8) {
               // Held still under lava: swim out instead of staying in it.
               Vec3 out = this.takeoff.lavaWayOut(player);
               if (out != null) {
                  move = out;
               }
            }
            if (move != null) {
               event.setVelocity(move);
               player.setDeltaMovement(move);
               return;
            }
         }
         // Climbs clear of the floor after take-off, then flies Baritone's heading or its route, and towards the destination
         // when it has neither yet, so the glide is always powered and never needs a firework.
         float[] heading = this.takeoff.glideHeading(player, this.heading(player), this.trip.target(), this.baritone.elytraStatus());
         double[] velocity = BaritoneElytraHandoff.velocity(this.speedSetting.getValue(), heading[0], heading[1]);
         Vec3 motion = new Vec3(velocity[0], velocity[1], velocity[2]);
         boolean keepOutOfLava = this.avoidLava() && !player.isInLava();
         if (!ElytraRouteCheck.clearAhead(player, motion, keepOutOfLava)) {
            // Never power the glide into a block: along the route if there's a clear way, else hold still for a new route.
            float[] aim = this.routeAim(player, this.baritone.elytraRoute());
            Vec3 other = null;
            if (aim != null) {
               double[] v = BaritoneElytraHandoff.velocity(this.speedSetting.getValue(), aim[0], aim[1]);
               other = new Vec3(v[0], v[1], v[2]);
            }
            motion = other != null && ElytraRouteCheck.clearAhead(player, other, keepOutOfLava) ? other : Vec3.ZERO;
            if (motion == Vec3.ZERO) {
               // Nowhere clear under lava: swim out to open air rather than hold still in it.
               Vec3 out = this.takeoff.lavaWayOut(player);
               if (out != null) {
                  motion = out;
               }
            }
            if (player.tickCount >= this.nextBlockedLog) {
               this.nextBlockedLog = player.tickCount + 100;
               ArgusClient.LOGGER.info("BaritoneElytra: heading yaw {}, pitch {} runs into a block at {}; {}", Math.round(heading[0]),
                  Math.round(heading[1]), player.blockPosition(), motion == Vec3.ZERO ? "holding still" : "following the route instead");
            }
         }
         event.setVelocity(motion);
         player.setDeltaMovement(motion);
      } catch (LinkageError | RuntimeException e) {
         ArgusClient.LOGGER.error("Baritone Elytra's powered flight failed", e);
         this.message("Powered flight failed, so the rest of this trip is Baritone's unpowered glide. See the log.");
         controllingFlight = false;
         this.trip = null;
      }
   }

   @EventHandler
   private void onRender(WorldRenderEvent.Main event) {
      if (this.trip == null || this.baritone == null || !this.showRouteSetting.getValue()) {
         return;
      }
      try {
         ElytraRouteRenderer.render(event, this.baritone.elytraPath(),
            ClientAccess.argusclientMinecraft.getEntityRenderDispatcher().camera.position(), this.routeColorSetting.getValue());
      } catch (LinkageError | RuntimeException e) {
         ArgusClient.LOGGER.error("Drawing Baritone's elytra route failed", e);
         this.showRouteSetting.setValue(false);
      }
   }

   /**
    * Strict route: always along Baritone's route, as far ahead as there is a straight clear way, with Baritone's heading only
    * when there's no route to follow. Otherwise Baritone's heading, its last one through a short gap, and the route when
    * Baritone has none or points away from all of the route ahead.
    */
   private float[] heading(LocalPlayer player) {
      float[] own = this.baritone.elytraHeading();
      List<? extends BlockPos> route = this.baritone.elytraRoute();
      int tick = player.tickCount;
      if (this.strictRouteSetting.getValue()) {
         float[] aim = this.routeAim(player, route);
         return aim != null ? aim : own;
      }
      if (own == null) {
         if (this.lastHeading != null && tick >= this.lastHeadingTick && tick - this.lastHeadingTick <= HEADING_GAP_TICKS) {
            return this.lastHeading;
         }
         return this.routeAim(player, route);
      }
      this.lastHeading = own;
      this.lastHeadingTick = tick;
      if (ElytraPathAim.turnedBack(player.getX(), player.getY(), player.getZ(), own[0], own[1], route)) {
         float[] aim = this.routeAim(player, route);
         if (aim != null) {
            return aim;
         }
      }
      return own;
   }

   private float[] routeAim(LocalPlayer player, List<? extends BlockPos> route) {
      ElytraPathAim.Aim aim = ElytraPathAim.aimClear(player.getX(), player.getY(), player.getZ(), route, node -> this.clearTo(player, node));
      return aim == null ? null : new float[]{aim.yaw(), aim.pitch()};
   }

   /** Whether the player's whole hitbox can travel in a straight line to this route node through loaded, open space. */
   private boolean clearTo(LocalPlayer player, BlockPos node) {
      Level level = ClientAccess.argusclientMinecraft.level;
      Vec3 way = Vec3.atCenterOf(node).subtract(player.position());
      double distance = way.length();
      if (distance > MAX_CLEARANCE_DISTANCE) {
         return false;
      }
      AABB body = player.getBoundingBox();
      int steps = Math.max(1, (int)Math.ceil(distance / CLEARANCE_STEP));
      for (int i = 1; i <= steps; i++) {
         Vec3 offset = way.scale((double)i / steps);
         AABB moved = body.move(offset);
         if (!level.hasChunkAt(BlockPos.containing(moved.getCenter())) || !ElytraRouteCheck.fits(player, body, moved)
            || this.avoidLava() && ElytraRouteCheck.touchesLava(level, moved)) {
            return false;
         }
      }
      return true;
   }

   private void equipElytra(LocalPlayer player) {
      if (!this.equipSetting.getValue() || elytraDurability(player.getItemBySlot(EquipmentSlot.CHEST)) >= this.baritone.elytraMinimumDurability()
         && isElytra(player.getItemBySlot(EquipmentSlot.CHEST))) {
         return;
      }
      int slot = this.bestElytraMenuSlot(player);
      if (slot >= 0 && GameScreens.inventoryFree(ClientAccess.argusclientMinecraft, player)) {
         InventoryUtils.swapSlotsWithPickup(slot, CHEST_MENU_SLOT);
      }
   }

   /** Inventory-menu slot of the elytra with the most durability left, or -1. */
   private int bestElytraMenuSlot(LocalPlayer player) {
      int best = -1;
      int bestDurability = -1;
      for (int slot = 0; slot < 36; slot++) {
         int durability = elytraDurability(player.getInventory().getItem(slot));
         if (durability > bestDurability) {
            bestDurability = durability;
            best = InventoryUtils.toMenuSlot(slot);
         }
      }
      return best;
   }

   private static boolean isElytra(ItemStack stack) {
      return stack.is(Items.ELYTRA);
   }

   /** Remaining durability of a usable elytra, or -1 when the stack isn't one. */
   private static int elytraDurability(ItemStack stack) {
      if (!isElytra(stack)) {
         return -1;
      }
      return stack.getMaxDamage() - stack.getDamageValue();
   }

   private double remaining(LocalPlayer player) {
      return this.trip == null ? 0.0 : this.remaining(player, this.trip);
   }

   private double remaining(LocalPlayer player, WalkingTrip walk) {
      return BaritoneElytraHandoff.horizontalDistance(player.getX(), player.getZ(), walk.target().getX() + 0.5, walk.target().getZ() + 0.5);
   }

   private void message(String text) {
      ChatUtils.printChat(Component.literal(text), ChatUtils.moduleSignature(this));
   }
}
