package dev.argus.client.modules.movement.elytratrip;

import dev.argus.client.modules.movement.GameScreens;
import dev.argus.client.ArgusClient;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.integrations.baritone.BaritoneBridge;
import dev.argus.client.NamedFeature;
import dev.argus.client.modules.movement.BaritoneElytraHandoff;
import dev.argus.client.modules.movement.BaritoneTakeoff;
import dev.argus.client.modules.movement.CrawlOut;
import dev.argus.client.modules.movement.ElytraFlightRecovery;
import dev.argus.client.modules.movement.ElytraLavaSafety;
import dev.argus.client.modules.movement.ElytraRouteCheck;
import dev.argus.client.modules.movement.ElytraRouteRenderer;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.movement.ElytraEscapeRun;
import dev.argus.client.utils.movement.ElytraPathAim;
import dev.argus.client.utils.network.PacketSender;
import java.awt.Color;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ElytraTrip's own flight: hands the destination to Baritone's elytra pathfinder, takes off, and powers the glide at a set
 * speed along Baritone's heading so no fireworks are needed. The steering is BaritoneElytra's, copied, with its own state.
 */
public final class TripFlight {
   private static final String LANDING = "Elytra - Landing";
   private static final int HEADING_GAP_TICKS = 20;
   private static final double HEADROOM = 3.0;
   private static final double CLEARANCE_STEP = 0.5;
   private static final double MAX_CLEARANCE_DISTANCE = 96.0;
   /** How long Baritone gets to pick up a flyTo before it is sent again. */
   private static final int LAUNCH_RETRY_TICKS = 60;

   private static volatile boolean flying;

   public enum Landing { NONE, ARRIVED, SHORT }

   private final Minecraft mc = Minecraft.getInstance();
   private final BaritoneBridge baritone;
   private final NamedFeature owner;
   private final Supplier<Float> speed;
   private final Supplier<Boolean> strictRoute;
   private final BaritoneTakeoff takeoff = new BaritoneTakeoff("ElytraTrip");
   private BlockPos destination;
   private boolean held;
   private boolean launched;
   private boolean sawLanding;
   private int launchTick;
   private float[] lastHeading;
   private int lastHeadingTick;
   private int nextBlockedLog;
   private String problem = "";
   private final Supplier<Float> strictTolerance;
   private final ElytraFlightRecovery recovery = new ElytraFlightRecovery("ElytraTrip");
   private final ElytraLavaSafety lavaSafety = new ElytraLavaSafety();
   private final ElytraRouteCheck routeCheck = new ElytraRouteCheck();
   private final ElytraEscapeRun escape = new ElytraEscapeRun();
   private final CrawlOut crawl = new CrawlOut("ElytraTrip");
   private boolean crawlPaused;
   private boolean crawlWarned;
   private Supplier<Integer> escapeDistance = () -> 48;
   private Supplier<Boolean> flyThroughLava = () -> false;
   private Supplier<Boolean> thinLavaOnly = () -> true;
   private Supplier<Boolean> drinkFireResistance = () -> false;
   private Supplier<Float> lavaMinHealth = () -> 0.0F;
   private java.util.function.Consumer<String> say = text -> { };
   private boolean lavaRoutingWarned;
   private boolean lavaWarned;
   /** After walking out of somewhere stuck, no take-off before this tick, so it really goes somewhere else first. */
   private int walkUntil;
   /** Ticks walked out of a stuck place before trying to take off again. */
   private static final int WALK_OUT_TICKS = 100;

   public TripFlight(BaritoneBridge baritone, NamedFeature owner, Supplier<Float> speed, Supplier<Boolean> strictRoute,
      Supplier<Float> strictTolerance) {
      this.baritone = baritone;
      this.owner = owner;
      this.speed = speed;
      this.strictRoute = strictRoute;
      this.strictTolerance = strictTolerance;
      // Baritone's chat says when its route has run out. The listener stays for the game and only acts while flying.
      baritone.addMessageListener(text -> flying && launched && recovery.onBaritoneMessage(text));
   }

   /** The trip's lava settings: route and fly through lava, keep lava seas solid, drink Fire Resistance, and the health below which lava is avoided. */
   public void lavaSettings(Supplier<Boolean> flyThroughLava, Supplier<Boolean> thinLavaOnly, Supplier<Boolean> drinkFireResistance,
      Supplier<Float> lavaMinHealth) {
      this.flyThroughLava = flyThroughLava;
      this.thinLavaOnly = thinLavaOnly;
      this.drinkFireResistance = drinkFireResistance;
      this.lavaMinHealth = lavaMinHealth;
   }

   /** Blocks to run towards the destination when Baritone finds no way out of somewhere, before taking off again. */
   public void escapeSettings(Supplier<Integer> escapeDistance) {
      this.escapeDistance = escapeDistance;
   }

   /** Where to say things worth a line in chat, as BaritoneElytra does. */
   public void messages(java.util.function.Consumer<String> say) {
      this.say = say;
   }

   /** True once the wings have stayed open long enough since the last take-off to count as a real flight. */
   public boolean glidedSteadily() {
      return takeoff.stable();
   }

   private void updateLavaRouting() {
      baritone.setLavaRouting(!avoidLava(), thinLavaOnly.get());
   }

   /** True when lava is to be kept out of: "Fly through lava" is off, or health has made this trip avoid it. */
   private boolean avoidLava() {
      return !flyThroughLava.get() || lavaSafety.avoidLava();
   }

   /** True while a trip is flying; ElytraFly and BaritoneElytra stand aside. */
   public static boolean isFlying() {
      return flying;
   }

   public static boolean arrived(double remaining, int arriveWithin, boolean baritoneActive) {
      return !baritoneActive && remaining <= arriveWithin;
   }

   public void begin(BlockPos destination) {
      this.destination = destination.immutable();
      this.launched = false;
      this.sawLanding = false;
      this.lastHeading = null;
      this.held = false;
      this.problem = "";
      this.walkUntil = 0;
      this.recovery.reset();
      this.lavaSafety.reset();
      this.routeCheck.reset();
      this.escape.reset();
      this.lavaRoutingWarned = false;
      this.lavaWarned = false;
      baritone.setRocketless(true);
      updateLavaRouting();
      flying = true;
   }

   public void end() {
      if (baritone.isAvailable()) {
         if (held) baritone.releasePause(owner);
         LocalPlayer player = mc.player;
         if (baritone.isElytraActive() && (player == null || !player.isFallFlying())) baritone.cancelPathing();
         baritone.setRocketless(false);
         baritone.setTightSpaces(false);
         baritone.setLavaRouting(false, true);
      }
      lavaSafety.stopDrinking();
      escape.cancel();
      if (crawlPaused && baritone.isAvailable()) baritone.releasePause(owner);
      crawlPaused = false;
      crawl.reset();
      held = false;
      launched = false;
      flying = false;
      destination = null;
   }

   /** Stop steering and hover in place; Baritone is paused until {@link #release}. */
   public void hold() {
      if (held) return;
      held = true;
      lavaSafety.stopDrinking();
      baritone.requestPause(owner);
   }

   public void release() {
      if (!held) return;
      held = false;
      baritone.releasePause(owner);
   }

   public boolean held() {
      return held;
   }

   /** Something Baritone refused, for the status line, or "". */
   public String problem() {
      return problem;
   }

   public boolean isLanding() {
      return LANDING.equals(baritone.elytraStatus());
   }

   public boolean baritoneActive() {
      return baritone.isElytraActive();
   }

   public double remaining(LocalPlayer player) {
      if (destination == null) return 0;
      return BaritoneElytraHandoff.horizontalDistance(player.getX(), player.getZ(), destination.getX() + 0.5, destination.getZ() + 0.5);
   }

   /** Forget the current Baritone flight, so the next tick sends flyTo again (after a teleport or reconnect). */
   public void relaunch() {
      launched = false;
      sawLanding = false;
   }

   /**
    * Once per tick while the trip is flying: (re)launches Baritone's elytra process and takes off. Returns how a finished
    * flight ended: arrived within {@code arriveWithin}, or landed short.
    */
   public Landing tick(LocalPlayer player, int arriveWithin) {
      if (destination == null || held) return Landing.NONE;
      if (crawlOut(player)) return Landing.NONE;
      if (escape.active()) {
         tickEscape(player);
         return Landing.NONE;
      }
      if (launched && isLanding()) sawLanding = true;
      if (launched && !baritone.isElytraActive() && player.tickCount - launchTick > LAUNCH_RETRY_TICKS) {
         launched = false;
         if (sawLanding || player.onGround()) {
            sawLanding = false;
            return arrived(remaining(player), arriveWithin, false) ? Landing.ARRIVED : Landing.SHORT;
         }
      }
      if (!launched) {
         if (arrived(remaining(player), arriveWithin, false)) return Landing.ARRIVED;
         launch(player);
         return Landing.NONE;
      }
      updateLavaRouting();
      baritone.setTightSpaces(recovery.tightSpaces(player));
      if (recovery.takeEscapeDue() && startEscape(player, "Baritone finds no way out of here, even through tight spaces.")) return Landing.NONE;
      BaritoneTakeoff.Result result = takeoff.tick(player, baritone, true, flyThroughLava.get(), text -> problem = text);
      if (result == BaritoneTakeoff.Result.NO_WAY_OUT) {
         problem = "No way out of the liquid nearby; walking towards the destination.";
         baritone.cancelPathing();
         baritone.walkToXZ(destination.getX(), destination.getZ());
         launched = false;
         walkUntil = player.tickCount + WALK_OUT_TICKS;
         return Landing.NONE;
      }
      if (takeoff.stable()) escape.glided();
      boolean wasAvoiding = lavaSafety.avoidLava();
      lavaSafety.tick(player, baritone.elytraRoute(), flyThroughLava.get(), drinkFireResistance.get(), lavaMinHealth.get(),
         text -> problem = text);
      if (lavaSafety.avoidLava() != wasAvoiding) updateLavaRouting();
      if (takeoff.stable() && !lavaRoutingWarned && flyThroughLava.get() && !baritone.lavaRoutingWorks()) {
         // Baritone has written terrain by the time the player is gliding, so the patch has had its chance to run.
         lavaRoutingWarned = true;
         say.accept("This Baritone build can't route through lava, so routes still go round it. Meteor's baritone-meteor build can.");
      }
      if (!lavaWarned && player.isInLava() && !player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)) {
         lavaWarned = true;
         say.accept("Flying through lava without Fire Resistance. Turn off \"Fly through lava\" to route around it.");
      }
      if (result == BaritoneTakeoff.Result.TIMED_OUT) {
         // Send the flight again from scratch rather than wait on a pathfinder that never answered.
         problem = "Baritone couldn't find a flight path; trying again.";
         baritone.cancelPathing();
         launched = false;
         return Landing.NONE;
      }
      if (baritone.isElytraSteering() && routeCheck.replanDue(player, baritone.elytraRoute(), avoidLava())) {
         // Baritone planned through terrain it hadn't packed or predicted wrong; the loaded world says otherwise.
         ArgusClient.LOGGER.info("ElytraTrip: route ahead runs through {} at {}; REPLAN", avoidLava() ? "a block or lava" : "a block",
            player.blockPosition());
         replanAround();
      } else if (baritone.isElytraSteering()) recover(player);
      return Landing.NONE;
   }

   /** Re-plans a flight that is stuck, getting no closer, or whose route Baritone gave up on short of the destination. */
   private void recover(LocalPlayer player) {
      boolean usable = player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA) || TripInventory.bestElytraMenuSlot(player) >= 0;
      switch (recovery.tick(player, destination, remaining(player), sawLanding || isLanding(), usable)) {
         case REPLAN -> replan(false);
         case REBUILD -> replan(true);
         case UNSTICK -> {
            if (startEscape(player, "Not getting anywhere from here.")) return;
            if (player.isFallFlying()) {
               // Baritone's own glide for a moment takes the player somewhere new to plan from.
               recovery.coast(player.tickCount);
               replan(true);
            } else {
               problem = "Not getting anywhere; walking a little before taking off again.";
               baritone.cancelPathing();
               baritone.walkToXZ(destination.getX(), destination.getZ());
               launched = false;
               walkUntil = player.tickCount + WALK_OUT_TICKS;
            }
         }
         case NONE -> {
         }
      }
   }

   private void replan(boolean fresh) {
      if (recovery.firstWarning()) say.accept("Baritone's route isn't getting anywhere, so it's asking for another.");
      try {
         if (fresh) baritone.flyFresh(destination);
         else baritone.flyTo(destination);
         problem = "Re-planning a route that wasn't getting anywhere.";
      } catch (IllegalArgumentException e) {
         problem = "Baritone won't fly from here: " + e.getMessage();
         launched = false;
         return;
      }
      recovery.routeStarted();
      lastHeading = null;
   }

   /**
    * A new route because the old one runs through a block or lava. Baritone repacks the loaded chunks for it, and the stuck
    * watch keeps counting so a place no route gets out of still ends in walking out. Not a fresh pathfinder: tearing that
    * down cancels whatever route it's still working out, which Baritone reports as "Failed to compute path to destination".
    */
   private void replanAround() {
      try {
         baritone.flyTo(destination);
         problem = "Baritone's route ran into a block or lava; asking for another.";
      } catch (IllegalArgumentException e) {
         problem = "Baritone won't fly from here: " + e.getMessage();
         launched = false;
         return;
      }
      recovery.routeReplaced();
      lastHeading = null;
   }

   /**
    * Lands if need be, runs the escape distance towards the destination, and takes off again from there. False when it
    * can't: too many runs in a row already, or lava rather than ground below.
    */
   private boolean startEscape(LocalPlayer player, String reason) {
      if (!escape.canRun() || !ElytraRouteCheck.safeToLand(player)) return false;
      int distance = escapeDistance.get();
      baritone.cancelPathing();
      escape.start(player.getX(), player.getZ(), destination.getX() + 0.5, destination.getZ() + 0.5, distance, player.tickCount);
      launched = false;
      problem = reason + " Running " + distance + " blocks and taking off again.";
      say.accept(problem);
      ArgusClient.LOGGER.info("ElytraTrip: escape run from {} towards {}, {}: {}", player.blockPosition(), escape.targetX(), escape.targetZ(),
         reason);
      return true;
   }

   /**
    * Stuck crawling under a ceiling, where Baritone can't walk: pauses Baritone and crawls to room to stand. True while
    * doing that, so nothing else runs this tick.
    */
   private boolean crawlOut(LocalPlayer player) {
      switch (crawl.tick(player)) {
         case CRAWLING -> {
            if (!crawlPaused) {
               crawlPaused = true;
               baritone.requestPause(owner);
               problem = "Stuck crawling; crawling out to room to stand.";
            }
            return true;
         }
         case NO_WAY -> {
            releaseCrawlPause();
            if (!crawlWarned) {
               crawlWarned = true;
               problem = "Stuck crawling with nowhere to stand nearby. Break the block above you or move out by hand.";
               say.accept(problem);
            }
            return false;
         }
         default -> {
            releaseCrawlPause();
            crawlWarned = false;
            return false;
         }
      }
   }

   private void releaseCrawlPause() {
      if (crawlPaused) {
         crawlPaused = false;
         baritone.releasePause(owner);
      }
   }

   private void tickEscape(LocalPlayer player) {
      boolean grounded = player.onGround() || player.isInWater() || player.isInLava();
      switch (escape.tick(player.getX(), player.getZ(), grounded, baritone.isPathingOrCalculating(), player.tickCount)) {
         case WALK -> baritone.walkToXZ(escape.targetX(), escape.targetZ());
         case DONE -> {
            baritone.cancelPathing();
            ArgusClient.LOGGER.info("ElytraTrip: escape run ended at {}; taking off again", player.blockPosition());
            // The next tick launches from here.
            launched = false;
            walkUntil = 0;
            problem = "";
         }
         case NONE -> {
         }
      }
   }

   private void launch(LocalPlayer player) {
      if (player.tickCount < walkUntil && player.tickCount + WALK_OUT_TICKS >= walkUntil) return;
      boolean fluid = player.isInWater() || player.isInLava();
      if (!player.isFallFlying() && !player.onGround() && !fluid) {
         // Mid-air after a /home or a rejoin: open the elytra on the way down, then hand the flight to Baritone.
         if (player.getDeltaMovement().y >= 0.0 || !player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)
            ) return;
         PacketSender.sendStartFallFlying();
         player.startFallFlying();
         return;
      }
      // In a liquid the take-off swims out to open sky itself.
      if (!player.isFallFlying() && !fluid) {
         boolean openAbove = mc.level.noCollision(player, player.getBoundingBox().expandTowards(0.0, HEADROOM, 0.0));
         if (!openAbove) {
            problem = "Something is above you; move to open sky.";
            return;
         }
      }
      if (!player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
         int slot = TripInventory.bestElytraMenuSlot(player);
         if (slot >= 0 && GameScreens.inventoryFree(mc, player)) InventoryUtils.swapSlotsWithPickup(slot, TripInventory.CHEST_SLOT);
         return;
      }
      try {
         baritone.flyTo(destination);
         problem = "";
      } catch (IllegalArgumentException e) {
         problem = "Baritone won't fly there: " + e.getMessage();
         return;
      }
      launched = true;
      sawLanding = false;
      launchTick = player.tickCount;
      takeoff.reset(player.isFallFlying());
      lastHeading = null;
   }

   /** Puts the best spare elytra on in flight, not while an inventory or container is open. True if it swapped. */
   public boolean swapElytra(LocalPlayer player) {
      if (!GameScreens.inventoryFree(mc, player)) return false;
      int slot = TripInventory.bestElytraMenuSlot(player);
      if (slot < 0) return false;
      InventoryUtils.swapSlotsWithPickup(slot, TripInventory.CHEST_SLOT);
      return true;
   }

   /** Draws Baritone's route while this trip's flight is running. */
   public void renderRoute(WorldRenderEvent event, Color color) {
      if (destination == null || !launched) return;
      ElytraRouteRenderer.render(event, baritone.elytraPath(), mc.getEntityRenderDispatcher().camera.position(), color);
   }

   /** Powered glide along Baritone's heading; a hover while held. */
   public void onMove(LocalPlayerMoveEvent event) {
      LocalPlayer player = mc.player;
      if (crawlPaused && player != null && !held) {
         crawl.onMove(event, player);
         return;
      }
      if (destination == null || player == null || event.getMoverType() != MoverType.SELF || !player.isFallFlying() || player.isInWater()
         || player.isPassenger()) {
         return;
      }
      if (held) {
         event.setVelocity(Vec3.ZERO);
         player.setDeltaMovement(Vec3.ZERO);
         return;
      }
      if (escape.landing()) {
         // Coming down slowly to run somewhere else; straight down was checked to be ground, not lava.
         event.setVelocity(ElytraRouteCheck.DESCENT);
         player.setDeltaMovement(ElytraRouteCheck.DESCENT);
         return;
      }
      if (!launched || isLanding()) return;
      // Without "Fly through lava", lava is something to get out of; the take-off swims out of it.
      if (player.isInLava() && !flyThroughLava.get()) return;
      // #pause or something else has Baritone, or it's gliding by itself to get unstuck: leave the player to it.
      if (!baritone.isElytraSteering() || recovery.coasting(player.tickCount)) return;
      if (strictRoute.get() && !takeoff.climbing()) {
         // Strict route: keep the body on Baritone's route, holding still rather than cutting a corner.
         Vec3 move = recovery.strictMove(player, baritone.elytraRoute(), speed.get(), strictTolerance.get(), avoidLava());
         if (move != null && move.lengthSqr() < 1.0E-8) {
            // Held still under lava: swim out instead of staying in it.
            Vec3 out = takeoff.lavaWayOut(player);
            if (out != null) move = out;
         }
         if (move != null) {
            event.setVelocity(move);
            player.setDeltaMovement(move);
            return;
         }
      }
      // Climbs clear of the floor after take-off, then flies Baritone's heading or its route, and towards the destination
      // when it has neither yet, so the glide is always powered and never needs a firework.
      float[] heading = takeoff.glideHeading(player, heading(player), destination, baritone.elytraStatus());
      double[] velocity = BaritoneElytraHandoff.velocity(speed.get(), heading[0], heading[1]);
      Vec3 motion = new Vec3(velocity[0], velocity[1], velocity[2]);
      boolean keepOutOfLava = avoidLava() && !player.isInLava();
      if (!ElytraRouteCheck.clearAhead(player, motion, keepOutOfLava)) {
         // Never power the glide into a block: along the route if there's a clear way, else hold still for a new route.
         float[] aim = routeAim(player, baritone.elytraRoute());
         Vec3 other = null;
         if (aim != null) {
            double[] v = BaritoneElytraHandoff.velocity(speed.get(), aim[0], aim[1]);
            other = new Vec3(v[0], v[1], v[2]);
         }
         motion = other != null && ElytraRouteCheck.clearAhead(player, other, keepOutOfLava) ? other : Vec3.ZERO;
         if (motion == Vec3.ZERO) {
            // Nowhere clear under lava: swim out to open air rather than hold still in it.
            Vec3 out = takeoff.lavaWayOut(player);
            if (out != null) motion = out;
         }
         if (player.tickCount >= nextBlockedLog) {
            nextBlockedLog = player.tickCount + 100;
            ArgusClient.LOGGER.info("ElytraTrip: heading yaw {}, pitch {} runs into a block at {}; {}", Math.round(heading[0]),
               Math.round(heading[1]), player.blockPosition(), motion == Vec3.ZERO ? "holding still" : "following the route instead");
         }
      }
      event.setVelocity(motion);
      player.setDeltaMovement(motion);
   }

   private float[] heading(LocalPlayer player) {
      float[] own = baritone.elytraHeading();
      List<? extends BlockPos> route = baritone.elytraRoute();
      int tick = player.tickCount;
      if (strictRoute.get()) {
         float[] aim = routeAim(player, route);
         return aim != null ? aim : own;
      }
      if (own == null) {
         if (lastHeading != null && tick >= lastHeadingTick && tick - lastHeadingTick <= HEADING_GAP_TICKS) return lastHeading;
         return routeAim(player, route);
      }
      lastHeading = own;
      lastHeadingTick = tick;
      if (ElytraPathAim.turnedBack(player.getX(), player.getY(), player.getZ(), own[0], own[1], route)) {
         float[] aim = routeAim(player, route);
         if (aim != null) return aim;
      }
      return own;
   }

   private float[] routeAim(LocalPlayer player, List<? extends BlockPos> route) {
      ElytraPathAim.Aim aim = ElytraPathAim.aimClear(player.getX(), player.getY(), player.getZ(), route, node -> clearTo(player, node));
      return aim == null ? null : new float[]{aim.yaw(), aim.pitch()};
   }

   private boolean clearTo(LocalPlayer player, BlockPos node) {
      Level level = mc.level;
      Vec3 way = Vec3.atCenterOf(node).subtract(player.position());
      double distance = way.length();
      if (distance > MAX_CLEARANCE_DISTANCE) return false;
      AABB body = player.getBoundingBox();
      int steps = Math.max(1, (int)Math.ceil(distance / CLEARANCE_STEP));
      for (int i = 1; i <= steps; i++) {
         AABB moved = body.move(way.scale((double)i / steps));
         if (!level.hasChunkAt(BlockPos.containing(moved.getCenter())) || !ElytraRouteCheck.fits(player, body, moved)) return false;
         if (avoidLava() && ElytraRouteCheck.touchesLava(level, moved)) return false;
      }
      return true;
   }
}
