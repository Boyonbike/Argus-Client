package dev.argus.client.modules.movement.elytratrip;

import com.google.gson.JsonObject;
import dev.argus.client.ArgusClient;
import dev.argus.client.ClientContext;
import dev.argus.client.chat.ChatTeleportState;
import dev.argus.client.events.KeyboardInputTickEvent;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.integrations.baritone.BaritoneBridge;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.movement.elytratrip.TripEmbeds.TripReport;
import dev.argus.client.modules.movement.elytratrip.TripPhases.EndReason;
import dev.argus.client.modules.movement.elytratrip.TripPhases.Event;
import dev.argus.client.modules.movement.elytratrip.TripPhases.Phase;
import dev.argus.client.modules.movement.elytratrip.TripSupplies.Supply;
import dev.argus.client.modules.movement.elytratrip.TripSupplies.SupplyCounts;
import dev.argus.client.modules.player.FreecamModule;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.level.Level;

/**
 * Runs a trip: decides from the phase which part is in charge each tick, turns events (death, disconnect, low supplies,
 * arrival) into phase changes, and reports each change to Discord and the state file.
 */
final class TripCoordinator {
   private static final long SAVE_EVERY_MS = 30_000;
   private static final long RESPAWN_CLICK_MS = 500;
   private static final int TELEPORT_RETRIES = 3;
   /** Lands short this many times in a row before refilling, in case the elytras are what's short. */
   private static final int SHORT_LANDINGS_BEFORE_REFILL = 2;
   /** With no station, takes off again after landing short this many times in a row before parking, as BaritoneElytra does. */
   private static final int SHORT_LANDINGS_WITHOUT_STATION = 3;

   private final ElytraTripModule module;
   private final Minecraft mc = Minecraft.getInstance();
   private final Path stateFile = FabricLoader.getInstance().getGameDir().resolve("argusclient/elytra-trip-state.json");

   private final TripTeleports teleports = new TripTeleports(TELEPORT_RETRIES);
   private final TripCheckpoint checkpoint;
   private final TripRefill refill;
   private final TripSurvival survival;
   private final TripConnection connection;
   private final TripDiscord discord;
   private final CompetingModules competing;
   private TripFlight flight;
   private TripInputLock inputLock;

   private TripPhases phases;
   private int destX;
   private int destZ;
   private int deaths;
   private int refills;
   private int disconnects;
   private long startedAt;
   private double startDistance = -1;
   private long lastProgressPost;
   private long lastSave;
   private long lastRespawnClick;
   private int landedShort;
   private boolean waitingForCooldown;
   private boolean wasDisconnected;
   private boolean flightBegun;
   private boolean testing;
   /** Low supplies were mentioned once on a trip with no station to refill from. */
   private boolean lowWithoutStationSaid;
   /** A refill already ran because of short landings; landing short again parks instead of looping. */
   private boolean shortRefillDone;
   /** The checkpoint success last counted as progress. */
   private long progressMark;
   /** Modules a trip left off before a crash, restored once every module has loaded. */
   private boolean crashRestorePending;
   /** The disconnect screen has shown since the trip logged out, so being in a world again is a new join. */
   private boolean loggedOutSeen;
   /** Kept off after a trip ends with a disconnect, so the client doesn't log back in; restored on the next join. */
   private static final java.util.Set<String> KEEP_OFF_UNTIL_JOIN = java.util.Set.of("AutoReconnect", "AutoLobby");

   TripCoordinator(ElytraTripModule module) {
      this.module = module;
      this.checkpoint = new TripCheckpoint(module.checkpointHome::getValue, () -> module.checkpointEvery.getValue() * 60_000L,
         System::currentTimeMillis, () -> module.isToggled() && running());
      this.checkpoint.onChange(this::save);
      this.refill = new TripRefill(module.stations(), teleports, checkpoint, module::limits, module.stationHome::getValue,
         module.checkpointHome::getValue, new TripRefill.Host() {
            @Override
            public String serverKey() {
               return module.serverKey();
            }

            @Override
            public boolean dumpJunk() {
               return module.dumpJunk.getValue();
            }

            @Override
            public TripLoadout loadout() {
               return module.savedLoadout();
            }

            @Override
            public void hold() {
               if (flight != null) flight.hold();
            }

            @Override
            public void sendCommand(String command) {
               if (mc.getConnection() != null) mc.getConnection().sendCommand(command);
            }
         });
      this.survival = new TripSurvival(module.eatAtHealth::getValue, module.eatAtHunger::getValue);
      this.connection = new TripConnection(() -> (long)(module.reconnectDelay.getValue() * 1000.0F), module.loginAccount::getValue,
         module.loginPassword::getValue, module.enterPortal::getValue, module.loginTimeout::getValue);
      this.discord = new TripDiscord(module.webhook::getValue, module::say);
      this.competing = new CompetingModules(this::moduleOn, this::setModule);
   }

   // ---- parts that need Baritone, made once it is there ------------------------------------------------------------

   private BaritoneBridge baritone() {
      return ClientContext.baritoneBridge;
   }

   private boolean ensureParts() {
      BaritoneBridge bridge = baritone();
      if (bridge == null || !bridge.isAvailable()) return false;
      if (flight == null) {
         flight = new TripFlight(bridge, module, module.speed::getValue, module.strictRoute::getValue,
            module.strictTolerance::getValue);
         flight.messages(module::say);
         flight.escapeSettings(module.escapeDistance::getValue);
         flight.lavaSettings(module.flyThroughLava::getValue, module.thinLavaOnly::getValue, module.drinkFireResistance::getValue,
            module.lavaMinHealth::getValue);
         inputLock = new TripInputLock(bridge, () -> {
            Module freecam = ClientContext.moduleRegistry == null ? null : ClientContext.moduleRegistry.getModule(FreecamModule.class);
            return freecam != null && freecam.isToggled();
         });
      }
      return true;
   }

   // ---- module lookup for CompetingModules -------------------------------------------------------------------------

   private Module named(String name) {
      if (ClientContext.moduleRegistry == null) return null;
      for (Object entry : ClientContext.moduleRegistry.getRegistry()) {
         if (entry instanceof Module candidate && candidate != module && candidate.getName().equalsIgnoreCase(name)) return candidate;
      }
      return null;
   }

   private Boolean moduleOn(String name) {
      Module found = named(name);
      return found != null && found.isToggled();
   }

   private void setModule(String name, Boolean on) {
      Module found = named(name);
      if (found == null) return;
      if (on) found.enable();
      else found.disable();
   }

   // ---- state -----------------------------------------------------------------------------------------------------

   private Phase phase() {
      return phases == null ? Phase.IDLE : phases.phase();
   }

   boolean running() {
      Phase phase = phase();
      return phase != Phase.IDLE && phase != Phase.ENDED;
   }

   boolean pausedOrParked() {
      return phase() == Phase.PAUSED || phase() == Phase.PARKED;
   }

   List<String> turnedOff() {
      return competing.turnedOff();
   }

   String startProblem() {
      if (running()) return "A trip is already running.";
      if (!ensureParts()) return "Baritone isn't installed.";
      if (mc.player == null || mc.level == null || mc.getCurrentServer() == null) return "Join the server first.";
      if (mc.level.dimension() != Level.NETHER) return "Not in the Nether.";
      if (module.stationHome.getValue().isBlank()) return "Station home name is empty.";
      if (module.checkpointHome.getValue().isBlank()) return "Checkpoint home name is empty.";
      return null;
   }

   /** True when this server has a tagged Elytras chest to refill from. A trip without one flies on what it carries. */
   private boolean hasStation() {
      String server = module.serverKey();
      return server != null && mc.player != null && !module.stations().chests(server, ChestRole.ELYTRAS, mc.player.position()).isEmpty();
   }

   // ---- user actions ----------------------------------------------------------------------------------------------

   void start(int x, int z) {
      String problem = startProblem();
      if (problem != null) {
         module.say(problem);
         return;
      }
      destX = x;
      destZ = z;
      phases = new TripPhases(module.deathLoopWindow.getValue() * 60_000L, module.maxReconnects.getValue());
      deaths = refills = disconnects = 0;
      landedShort = 0;
      shortRefillDone = false;
      lowWithoutStationSaid = false;
      testing = false;
      startedAt = System.currentTimeMillis();
      progressMark = checkpoint.lastSuccessAt();
      lastProgressPost = startedAt;
      connection.remember(mc.getCurrentServer());
      // Competing modules go off before the flight starts, so BaritoneElytra's own shutdown can't undo the trip's settings.
      List<String> off = competing.switchOff(CompetingModules.CANDIDATES);
      beginFlight();
      phases.on(Event.START, startedAt);
      startDistance = flight.remaining(mc.player);
      if (!off.isEmpty()) module.say("Turned off for this trip: " + String.join(", ", off) + ".");
      if (!hasStation()) module.say("No station is tagged, so this trip won't refill: it flies on what you carry and parks if it runs out of elytras.");
      ArgusClient.LOGGER.info("ElytraTrip: started to {}, {}, {} blocks away, station {}", x, z, (int)startDistance, hasStation());
      post(TripEmbeds.started(report(), hide()));
      save();
   }

   private void beginFlight() {
      flight.begin(new BlockPos(destX, 64, destZ));
      flightBegun = true;
      inputLock.lock();
   }

   void pause() {
      if (phases == null || !phases.phase().active()) return;
      Phase from = phases.phase();
      if (phases.on(Event.PAUSE, System.currentTimeMillis()) != Phase.PAUSED) return;
      hold();
      if (from == Phase.REFILLING || from == Phase.RETURNING) refill.abort();
      save();
   }

   void resume() {
      if (phases == null || !pausedOrParked()) return;
      if (!ensureParts()) {
         module.say("Baritone isn't installed.");
         return;
      }
      if (!flightBegun) {
         competing.switchOff(CompetingModules.CANDIDATES);
         connection.remember(mc.getCurrentServer());
         beginFlight();
      }
      Phase next = phases.on(Event.RESUME, System.currentTimeMillis());
      flight.release();
      enterPhase(next);
      save();
   }

   void stop() {
      if (!running()) return;
      phases.on(Event.STOP, System.currentTimeMillis());
      ended();
   }

   void testRefill() {
      if (running()) {
         module.say("Stop the trip first; Test refill runs from where you stand.");
         return;
      }
      if (module.serverKey() == null || mc.player == null) {
         module.say("Join the server and stand at the station first.");
         return;
      }
      testing = true;
      refill.startTest();
      module.say("Test refill: dump, restock and equip from here.");
   }

   void testDiscord() {
      discord.test();
   }

   // ---- lifecycle -------------------------------------------------------------------------------------------------

   void enabled() {
      TripState state = TripState.load(stateFile);
      competing.load(state.turnedOff);
      if (state.checkpoint != null) checkpoint.restore(state.checkpoint);
      if (state.learned != null) teleports.learned(state.learned);
      if (state.phases == null || state.phases.phase() == Phase.IDLE || state.phases.phase() == Phase.ENDED) {
         // Modules a trip turned off before a crash still come back.
         // Done on the first tick, once every module has loaded its config, so a later load can't switch them off again.
         crashRestorePending = !competing.turnedOff().isEmpty();
         return;
      }
      phases = TripPhases.restore(state.phases, module.deathLoopWindow.getValue() * 60_000L, module.maxReconnects.getValue());
      destX = state.destX;
      destZ = state.destZ;
      deaths = state.deaths;
      refills = state.refills;
      disconnects = state.disconnects;
      startedAt = state.startedAt;
      lastProgressPost = System.currentTimeMillis();
      refill.restoreAfterDeath(state.afterDeath);
      flightBegun = false;
      if (phases.phase() != Phase.PARKED && phases.phase() != Phase.PAUSED) {
         phases.suspend("The game restarted mid-trip.");
      }
   }

   void disabled() {
      if (running()) stop();
      testing = false;
      refill.abort();
   }

   // ---- ticks -----------------------------------------------------------------------------------------------------

   /** Every client tick while the module is on, in or out of a world. */
   void clientTick() {
      long now = System.currentTimeMillis();
      boolean disconnected = connection.disconnected();
      if (disconnected && !wasDisconnected) onDisconnected(now);
      wasDisconnected = disconnected;
      if (crashRestorePending) {
         crashRestorePending = false;
         competing.restore();
         save();
      }
      if (phase() == Phase.ENDED && disconnected) loggedOutSeen = true;
      if (phase() == Phase.ENDED && loggedOutSeen && !competing.turnedOff().isEmpty() && mc.player != null && mc.level != null && !disconnected) {
         // Back on a server after a trip that logged out: AutoReconnect and AutoLobby can come back now.
         competing.restore();
         save();
      }
      Phase current = phase();
      connection.tick(now, phases != null && phases.reconnectAllowed(), current.active() || current == Phase.RECONNECTING);
      if (phase() == Phase.RECONNECTING && connection.backInWorld() && mc.player != null && mc.player.isAlive()) backInWorld(now);
      if (running() && now - lastSave >= SAVE_EVERY_MS) save();
   }

   /** Every player tick, in a world. */
   void playerTick() {
      LocalPlayer player = mc.player;
      if (player == null) return;
      long now = System.currentTimeMillis();
      if (testing) {
         TripRefill.Status status = refill.tick();
         if (status != TripRefill.Status.RUNNING) {
            testing = false;
            module.say(status == TripRefill.Status.DONE ? "Test refill done. " + refill.detail()
               : status == TripRefill.Status.SHORT ? "Test refill: the station is short of " + list(refill.shortSupplies()) + ". " + refill.detail()
               : "Test refill failed: " + refill.failure());
         }
         return;
      }
      if (phases == null || flight == null) return;
      Phase phase = phases.phase();
      boolean lock = (phase == Phase.FLYING || phase == Phase.REFILLING || phase == Phase.RETURNING || phase == Phase.RESPAWNING)
         && !connection.inLobby();
      if (lock) inputLock.lock();
      else inputLock.unlock();
      try {
         switch (phase) {
            case FLYING -> flying(player, now);
            case REFILLING, RETURNING -> refilling(player, now);
            case RESPAWNING -> respawning(player, now);
            default -> {
            }
         }
      } catch (LinkageError | RuntimeException e) {
         ArgusClient.LOGGER.error("ElytraTrip tick failed", e);
         park("Something went wrong (" + e.getClass().getSimpleName() + "). See the log.");
      }
   }

   private void flying(LocalPlayer player, long now) {
      if (connection.inLobby()) return;
      survival.tick(player);
      survival.ensureTotem(player);
      checkpoint.tick(player.isFallFlying(), flight.isLanding());
      if (checkpoint.lastSuccessAt() > progressMark) {
         // A new checkpoint is progress: deaths and short landings before it don't count toward a loop.
         progressMark = checkpoint.lastSuccessAt();
         phases.on(Event.PROGRESS, now);
         shortRefillDone = false;
      }
      SupplyCounts counts = TripInventory.counts(player);
      SupplyLimits limits = module.limits();
      if (player.isFallFlying()) {
         switch (TripSupplies.swapAction(counts, limits, baritone().elytraMinimumDurability())) {
            case SWAP -> flight.swapElytra(player);
            case REFILL_NOW -> {
               if (!hasStation()) {
                  park("Out of usable elytras, and no station is tagged to refill from.");
                  return;
               }
               startRefill(EnumSet.of(Supply.ELYTRAS), false, now);
               return;
            }
            case NONE -> {
            }
         }
      }
      EnumSet<Supply> low = TripSupplies.belowMinimum(counts, limits);
      waitingForCooldown = false;
      if (!low.isEmpty() && !hasStation()) {
         if (!lowWithoutStationSaid) module.say("Low on " + list(low) + ", but no station is tagged to refill from. Carrying on.");
         lowWithoutStationSaid = true;
      } else if (!low.isEmpty()) {
         if (teleports.refillCanWait(counts.wornElytraDurability(), now)) {
            waitingForCooldown = true;
         } else {
            startRefill(low, false, now);
            return;
         }
      }
      // A steady glide since the last take-off: a later short landing is a new problem, not the same one again.
      if (flight.glidedSteadily()) landedShort = 0;
      switch (flight.tick(player, module.arriveWithin.getValue())) {
         case ARRIVED -> {
            phases.on(Event.ARRIVED, now);
            ended();
            return;
         }
         case SHORT -> {
            landedShort++;
            int remaining = (int)flight.remaining(player);
            if (!hasStation()) {
               if (landedShort > SHORT_LANDINGS_WITHOUT_STATION) {
                  park("Baritone keeps landing short of the destination.");
                  return;
               }
               module.say("Landed " + remaining + " blocks short; taking off again.");
            } else if (landedShort >= SHORT_LANDINGS_BEFORE_REFILL) {
               if (shortRefillDone) {
                  park("Baritone keeps landing short of the destination, even after a refill.");
                  return;
               }
               shortRefillDone = true;
               startRefill(EnumSet.of(Supply.ELYTRAS), false, now);
               return;
            } else {
               module.say("Landed " + remaining + " blocks short; taking off again.");
            }
            ArgusClient.LOGGER.info("ElytraTrip: landed {} blocks short at {}; short landings in a row {}", remaining, player.blockPosition(), landedShort);
         }
         case NONE -> {
         }
      }
      if (now - lastProgressPost >= module.updateEvery.getValue() * 60_000L) {
         lastProgressPost = now;
         post(TripEmbeds.progress(report(), hide()));
      }
   }

   private void startRefill(EnumSet<Supply> reason, boolean afterDeath, long now) {
      if (!afterDeath) phases.on(Event.LOW_SUPPLY, now);
      refills++;
      survival.cancel();
      flight.hold();
      refill.start(afterDeath, reason);
      post(TripEmbeds.refillStarted(report(), reason, hide()));
      save();
   }

   private void refilling(LocalPlayer player, long now) {
      if (connection.inLobby()) return;
      survival.ensureTotem(player);
      TripRefill.Status status = refill.tick();
      if (phases.phase() == Phase.REFILLING && refill.returning()) {
         phases.on(Event.REFILL_DONE, now);
         post(TripEmbeds.refillDone(report(), refill.detail(), refill.tookMs(), hide()));
         save();
      }
      switch (status) {
         case DONE -> {
            if (phases.phase() == Phase.REFILLING) phases.on(Event.REFILL_DONE, now);
            phases.on(Event.RETURNED, now);
            landedShort = 0;
            flight.release();
            flight.relaunch();
            save();
         }
         case SHORT -> {
            phases.on(Event.REFILL_SHORT, now);
            ended();
         }
         case FAILED -> {
            phases.on(Event.REFILL_FAILED, now);
            parked();
         }
         case TELEPORT_GAVE_UP -> {
            phases.on(Event.TELEPORT_GAVE_UP, now);
            ended();
         }
         case RUNNING -> {
         }
      }
   }

   private void respawning(LocalPlayer player, long now) {
      if (mc.gui.screen() instanceof DeathScreen || player.isDeadOrDying()) {
         if (now - lastRespawnClick >= RESPAWN_CLICK_MS) {
            lastRespawnClick = now;
            player.respawn();
         }
         return;
      }
      phases.on(Event.RESPAWNED, now);
      if (!hasStation()) {
         park("Died, and no station is tagged to refill from.");
         return;
      }
      startRefill(EnumSet.noneOf(Supply.class), true, now);
   }

   // ---- events ----------------------------------------------------------------------------------------------------

   private void onDisconnected(long now) {
      if (phases == null || !phases.reconnectAllowed()) return;
      Phase before = phases.phase();
      boolean wasActive = before.active();
      if (refill.running()) refill.abort();
      survival.cancel();
      checkpoint.reset();
      if (flight != null) flight.relaunch();
      if (inputLock != null) inputLock.unlock();
      Phase after = phases.on(Event.DISCONNECTED, now);
      if (wasActive) disconnects++;
      if (after == Phase.PARKED) parked();
      else if (wasActive) post(TripEmbeds.disconnected(report(), hide()));
      save();
   }

   private void backInWorld(long now) {
      Phase next = phases.on(Event.BACK_IN_WORLD, now);
      post(TripEmbeds.reconnected(report(), hide()));
      enterPhase(next);
      save();
   }

   /** Picks a phase's work back up after a reconnect or Resume. */
   private void enterPhase(Phase next) {
      switch (next) {
         case FLYING -> flight.relaunch();
         case REFILLING -> refill.resume(refill.afterDeath());
         case RETURNING -> refill.resumeReturn();
         default -> {
         }
      }
   }

   private void onDied(String cause) {
      if (phases == null) return;
      Phase phase = phases.phase();
      if (!phase.active() || phase == Phase.RECONNECTING || phase == Phase.RESPAWNING) return;
      long now = System.currentTimeMillis();
      survival.cancel();
      refill.abort();
      flight.release();
      flight.relaunch();
      deaths++;
      Phase next = phases.on(Event.DIED, now);
      post(TripEmbeds.died(report(), cause, hide(), module.ping.getValue()));
      if (next == Phase.PARKED) parked();
      save();
   }

   void onPacket(PacketEvent.Receive event) {
      if (phases == null && !testing) return;
      if (event.getPacket() instanceof ClientboundShowDialogPacket dialog) {
         if (running() && checkpoint.onDialog(dialog)) event.cancel();
      } else if (event.getPacket() instanceof ClientboundSystemChatPacket chat) {
         String text = chat.content().getString();
         mc.execute(() -> {
            long now = System.currentTimeMillis();
            teleports.message(text, now);
            if (!chat.overlay()) {
               checkpoint.onChat(text);
               connection.onChat(text);
            }
         });
      } else if (event.getPacket() instanceof ClientboundSetActionBarTextPacket bar) {
         String text = bar.text().getString();
         mc.execute(() -> teleports.message(text, System.currentTimeMillis()));
      } else if (event.getPacket() instanceof ClientboundPlayerPositionPacket position) {
         LocalPlayer player = mc.player;
         if (player != null && position.relatives().isEmpty()
            && position.change().position().distanceToSqr(player.position()) >= ChatTeleportState.TELEPORT_JUMP_SQ) {
            mc.execute(() -> {
               teleports.longJump(System.currentTimeMillis());
               if (flight != null) flight.relaunch();
            });
         }
      } else if (event.getPacket() instanceof ClientboundLoginPacket) {
         // A login inside a world is 6b6t moving the player between its servers, not a disconnect.
         if (mc.level != null) {
            mc.execute(() -> {
               teleports.transferred(System.currentTimeMillis());
               if (flight != null) flight.relaunch();
            });
         }
      } else if (event.getPacket() instanceof ClientboundPlayerCombatKillPacket death) {
         LocalPlayer player = mc.player;
         if (player != null && death.playerId() == player.getId()) {
            String cause = death.message().getString();
            mc.execute(() -> onDied(cause));
         }
      }
   }

   void onMove(LocalPlayerMoveEvent event) {
      if (flight != null && running()) flight.onMove(event);
   }

   void renderRoute(dev.argus.client.events.WorldRenderEvent event, java.awt.Color color) {
      if (flight != null && running()) flight.renderRoute(event, color);
   }

   void onKeyboard(KeyboardInputTickEvent event) {
      if (inputLock != null) inputLock.onKeyboardInputTick(event);
   }

   // ---- endings ---------------------------------------------------------------------------------------------------

   private void hold() {
      if (flight != null) flight.hold();
      survival.cancel();
      if (inputLock != null) inputLock.unlock();
   }

   private void park(String reason) {
      if (phases == null) return;
      phases.park(reason);
      parked();
   }

   private void parked() {
      hold();
      refill.abort();
      post(TripEmbeds.parked(report(), phases.parkReason(), hide(), module.ping.getValue()));
      save();
   }

   /** A trip that ended: tidy up, report, and for arrival or a failed station, log out for good. */
   private void ended() {
      EndReason reason = phases.endReason();
      TripReport report = report();
      if (flight != null) flight.end();
      flightBegun = false;
      survival.cancel();
      refill.abort();
      if (inputLock != null) inputLock.unlock();
      boolean logOut = reason == EndReason.ARRIVED || reason == EndReason.STATION_SHORT || reason == EndReason.TELEPORT_FAILED;
      // AutoReconnect and AutoLobby stay off until the next join, or they would log straight back in.
      loggedOutSeen = false;
      if (logOut) competing.restoreExcept(KEEP_OFF_UNTIL_JOIN);
      else competing.restore();
      post(TripEmbeds.ended(report, reason, refill.shortSupplies(), hide(), module.ping.getValue()));
      save();
      if (logOut) {
         connection.disable();
         String why = switch (reason) {
            case ARRIVED -> "ElytraTrip arrived at the destination.";
            case STATION_SHORT -> "ElytraTrip stopped: the station is short of " + list(refill.shortSupplies()) + ".";
            default -> "ElytraTrip stopped: /home kept failing.";
         };
         module.say(why);
         var listener = mc.getConnection();
         if (listener != null && listener.getConnection() != null) listener.getConnection().disconnect(Component.literal(why));
      } else {
         module.say("Trip stopped.");
      }
   }

   // ---- reporting -------------------------------------------------------------------------------------------------

   private boolean hide() {
      return module.hideCoords.getValue();
   }

   private void post(JsonObject message) {
      discord.post(message);
   }

   private TripReport report() {
      LocalPlayer player = mc.player;
      SupplyCounts counts = player == null ? null : TripInventory.counts(player);
      double remaining = player == null || flight == null ? 0 : flight.remaining(player);
      long elapsed = Math.max(1, System.currentTimeMillis() - startedAt);
      double avg = startDistance < 0 || player == null ? 0 : Math.max(0, startDistance - remaining) / (elapsed / 1000.0);
      long checkpointAge = checkpoint.lastSuccessAt() <= 0 ? -1 : System.currentTimeMillis() - checkpoint.lastSuccessAt();
      return new TripReport(module.serverKey() == null ? "the server" : module.serverKey(),
         mc.level == null ? "" : mc.level.dimension().identifier().toString(), player == null ? 0 : player.getBlockX(),
         player == null ? 0 : player.getBlockZ(), destX, destZ, remaining, avg, counts == null ? 0 : counts.spares(),
         counts == null ? 0 : counts.totems(), counts == null ? 0 : counts.gapples(), counts == null ? 0 : counts.food(),
         player == null ? 0 : player.getHealth(), checkpointAge, deaths, refills, disconnects);
   }

   private static String list(EnumSet<Supply> supplies) {
      return supplies.isEmpty() ? "supplies" : String.join(", ", supplies.stream().map(Supply::label).toList());
   }

   String info() {
      return switch (phase()) {
         case FLYING -> mc.player == null || flight == null ? "Flying" : String.format(Locale.ROOT, "%,d", Math.round(flight.remaining(mc.player)));
         case REFILLING, RETURNING -> "Refill";
         case RESPAWNING -> "Respawn";
         case RECONNECTING -> "Reconnect";
         case PAUSED -> "Paused";
         case PARKED -> "Parked";
         default -> null;
      };
   }

   String statusLine() {
      long now = System.currentTimeMillis();
      if (testing) return "Test refill: " + refill.step();
      return switch (phase()) {
         case IDLE -> "No trip running.";
         case FLYING -> {
            LocalPlayer player = mc.player;
            if (player == null || flight == null) yield "Flying.";
            if (connection.inLobby()) yield "Getting through the lobby.";
            SupplyCounts counts = TripInventory.counts(player);
            String text = "Flying, " + String.format(Locale.ROOT, "%,d", Math.round(flight.remaining(player))) + " blocks left, "
               + counts.spares() + " spare elytras";
            long next = checkpoint.lastSuccessAt() <= 0 ? 0
               : checkpoint.lastSuccessAt() + module.checkpointEvery.getValue() * 60_000L - now;
            if (next > 0) text += ", next checkpoint in " + ChatTeleportState.remaining(next);
            if (waitingForCooldown) text += ". Refilling once /home is off cooldown";
            if (!flight.problem().isEmpty()) text += ". " + flight.problem();
            yield text + ".";
         }
         case REFILLING, RETURNING -> "Refilling: " + refill.step() + ".";
         case RESPAWNING -> "Respawning after a death.";
         case RECONNECTING -> {
            long in = connection.reconnectInMs(now);
            yield connection.inLobby() ? "Getting through the lobby." : in >= 0 ? "Reconnecting in " + ChatTeleportState.remaining(in) + "." : "Reconnecting.";
         }
         case PAUSED -> "Paused. Press Resume to carry on.";
         case PARKED -> "Parked: " + phases.parkReason() + " Press Resume when it's fixed.";
         case ENDED -> switch (phases.endReason()) {
            case ARRIVED -> "Arrived and logged out.";
            case STATION_SHORT -> "Stopped: the station is short of " + list(refill.shortSupplies()) + ".";
            case TELEPORT_FAILED -> "Stopped: /home kept failing.";
            default -> "Trip stopped.";
         };
      };
   }

   void save() {
      lastSave = System.currentTimeMillis();
      TripState state = new TripState();
      state.phases = phases == null ? null : phases.snapshot();
      state.destX = destX;
      state.destZ = destZ;
      state.server = module.serverKey();
      state.deaths = deaths;
      state.refills = refills;
      state.disconnects = disconnects;
      state.startedAt = startedAt;
      state.afterDeath = refill.afterDeath();
      state.turnedOff = competing.turnedOff();
      state.checkpoint = checkpoint.state();
      state.learned = teleports.learned();
      try {
         state.save(stateFile);
      } catch (IOException | RuntimeException e) {
         ArgusClient.LOGGER.warn("Saving the ElytraTrip state failed", e);
      }
   }
}
