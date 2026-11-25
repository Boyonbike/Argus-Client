package dev.argus.client.modules.movement.elytratrip;

import dev.argus.client.modules.movement.GameScreens;
import dev.argus.client.ClientContext;
import dev.argus.client.chat.ChatSender;
import dev.argus.client.chat.LobbyAccounts;
import dev.argus.client.chat.LobbyLocation;
import dev.argus.client.chat.LobbyLogin;
import dev.argus.client.chat.LobbyLoginHold;
import dev.argus.client.chat.LobbyPortalFinder;
import dev.argus.client.integrations.baritone.BaritoneBridge;
import dev.argus.client.network.ConnectionProfiles;
import dev.argus.client.network.ProfileRuntime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ElytraTrip's own reconnect and 6b6t lobby: waits the reconnect delay on the disconnected screen, connects to the trip's
 * server, logs in on the cracked lobby and walks into the portal. The lobby walk is AutoLobby's, copied, with its own state.
 */
public final class TripConnection {
   private static final int POST_LOGIN_DELAY = 60;
   private static final int JOIN_DELAY = 40;
   private static final int RESCAN_DELAY = 100;
   private static final int SEARCH_RADIUS = 64;
   private static final int SEARCH_HEIGHT = 32;

   private final Minecraft mc = Minecraft.getInstance();
   private final LongSupplier reconnectDelayMs;
   private final Supplier<String> loginAccount;
   private final Supplier<String> loginPassword;
   private final BooleanSupplier enterPortal;
   private final IntSupplier loginTimeoutTicks;

   private ServerData server;
   private long reconnectAt;

   private final LobbyLoginHold loginHold = new LobbyLoginHold();
   private final Map<BlockPos, Long> failedPortals = new HashMap<>();
   private ClientPacketListener connection;
   private ClientLevel world;
   private String brand;
   private LobbyLocation location = LobbyLocation.NONE;
   private LobbyLogin.Result loginResult = LobbyLogin.Result.IGNORED;
   private boolean loginSent;
   private boolean warnedMissingLogin;
   private int loginTicks;
   private int settleTicks;
   private int scanTicks;
   private BlockPos portal;
   private BlockPos pathGoal;
   private int pathTicks;
   private int stuckTicks;
   private Vec3 lastPosition;
   private boolean holdingMovement;
   /** Ticks in a world outside the lobby, so a rejoin isn't taken as done before the lobby is recognised. */
   private int worldTicks;
   private static final int BACK_IN_WORLD_TICKS = 40;

   private final LobbyPortalFinder.Probe probe = new LobbyPortalFinder.Probe() {
      @Override
      public boolean loaded(int chunkX, int chunkZ) {
         return world.hasChunk(chunkX, chunkZ);
      }

      @Override
      public boolean maybePortal(int chunkX, int sectionY, int chunkZ) {
         var chunk = world.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
         if (chunk == null) return false;
         int index = chunk.getSectionIndexFromSectionY(sectionY);
         var sections = chunk.getSections();
         return index >= 0 && index < sections.length && sections[index].maybeHas(state -> state.is(Blocks.NETHER_PORTAL));
      }

      @Override
      public boolean portal(BlockPos pos) {
         return loaded(pos.getX() >> 4, pos.getZ() >> 4) && world.getBlockState(pos).is(Blocks.NETHER_PORTAL)
            && failedPortals.keySet().stream().noneMatch(failed -> failed.distSqr(pos) <= 64);
      }
   };

   public TripConnection(LongSupplier reconnectDelayMs, Supplier<String> loginAccount, Supplier<String> loginPassword,
                         BooleanSupplier enterPortal, IntSupplier loginTimeoutTicks) {
      this.reconnectDelayMs = reconnectDelayMs;
      this.loginAccount = loginAccount;
      this.loginPassword = loginPassword;
      this.enterPortal = enterPortal;
      this.loginTimeoutTicks = loginTimeoutTicks;
   }

   /** Remembers the server the trip runs on, so a reconnect goes back to it. */
   public void remember(ServerData data) {
      if (data != null) server = data;
   }

   public ServerData server() {
      return server;
   }

   /** Whether the disconnected screen is up. */
   public boolean disconnected() {
      return mc.screen instanceof DisconnectedScreen;
   }

   /** Seconds until the next reconnect, or -1 when none is waiting. */
   public long reconnectInMs(long now) {
      return reconnectAt == 0 ? -1 : Math.max(0, reconnectAt - now);
   }

   /**
    * Runs every client tick, in or out of a world.
    *
    * @param allowed whether a disconnect may be reconnected
    * @param lobby whether to log in and walk through the lobby (only while a trip is running)
    */
   public void tick(long now, boolean allowed, boolean lobby) {
      if (disconnected()) {
         if (!allowed || server == null) {
            reconnectAt = 0;
            return;
         }
         if (reconnectAt == 0) reconnectAt = now + reconnectDelayMs.getAsLong();
         if (now >= reconnectAt) {
            reconnectAt = 0;
            ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, ServerAddress.parseString(server.ip), server, true, null);
         }
         return;
      }
      reconnectAt = 0;
      if (mc.player == null || mc.level == null || mc.getConnection() == null || !lobby) {
         resetLobby();
         worldTicks = 0;
         return;
      }
      tickLobby();
      worldTicks = location.lobby() ? 0 : worldTicks + 1;
   }

   public boolean inLobby() {
      return location.lobby();
   }

   /** In a world past the lobby, settled for a couple of seconds. Any dimension: a respawn can put the player in the Overworld. */
   public boolean backInWorld() {
      return mc.player != null && mc.level != null && !inLobby() && worldTicks >= BACK_IN_WORLD_TICKS;
   }

   /** Stops the lobby walk and any pending reconnect. */
   public void disable() {
      reconnectAt = 0;
      resetLobby();
   }

   private void resetLobby() {
      stopWalking();
      connection = null;
      world = null;
      brand = null;
      location = LobbyLocation.NONE;
      loginHold.clear();
      loginResult = LobbyLogin.Result.IGNORED;
      loginSent = false;
      warnedMissingLogin = false;
      loginTicks = 0;
   }

   private void tickLobby() {
      if (connection != mc.getConnection()) {
         resetLobby();
         connection = mc.getConnection();
      }
      updateLocation();
      if (!location.lobby() || location == LobbyLocation.WAITING_AREA) return;
      if (location.requiresLogin(LobbyAccounts.cracked(mc.getUser().getProfileId(), mc.getUser().getAccessToken())) && !finishLogin()) return;
      if (loginHold.hold(connection)) return;
      if (!enterPortal.getAsBoolean() || !GameScreens.playing(mc) || !mc.player.isAlive() || mc.player.isPassenger() || mc.player.isFallFlying()) {
         stopWalking();
         return;
      }
      if (settleTicks++ < JOIN_DELAY) return;
      if (insidePortal()) {
         stopWalking();
         return;
      }
      if (portal == null && scanTicks-- <= 0) {
         scanTicks = RESCAN_DELAY;
         findPortal();
      }
      if (portal != null) walkToPortal();
   }

   private void updateLocation() {
      String currentBrand = connection.serverBrand();
      var player = mc.player;
      LobbyLocation detected = LobbyLocation.detect(currentBrand, mc.level.dimension().identifier().toString(), player.getX(), player.getY(), player.getZ());
      boolean newWorld = world != mc.level;
      LobbyLocation next = newWorld || !Objects.equals(brand, currentBrand) ? detected : location.afterRescan(detected);
      if (newWorld || next != location) {
         stopWalking();
         failedPortals.clear();
         scanTicks = 0;
         settleTicks = 0;
         if (newWorld) loginHold.worldChanged(connection, POST_LOGIN_DELAY);
      }
      world = mc.level;
      brand = currentBrand;
      location = next;
   }

   private boolean finishLogin() {
      if (loginResult == LobbyLogin.Result.SUCCESS || loginSent && loginResult == LobbyLogin.Result.IGNORED && loginTicks >= loginTimeoutTicks.getAsInt()) {
         if (!loginHold.loggedInOn(connection)) loginHold.loggedIn(connection, POST_LOGIN_DELAY);
         return true;
      }
      if (loginSent) {
         loginTicks++;
         return false;
      }
      String password = passwordFor(mc.getUser().getName());
      String command = password != null && password.chars().noneMatch(Character::isISOControl) ? LobbyLogin.command(password) : null;
      if (command == null) {
         if (!warnedMissingLogin) {
            notice("Log in manually, or give " + mc.getUser().getName() + " a lobby password in Accounts or in ElytraTrip's Connection settings.");
            warnedMissingLogin = true;
         }
         return false;
      }
      if (!GameScreens.playing(mc) || ++loginTicks < Math.max(20, JOIN_DELAY)) return false;
      loginSent = true;
      loginTicks = 0;
      connection.sendCommand(command);
      return false;
   }

   /** The account switcher's saved lobby password first, then ElytraTrip's own Login account and password. */
   private String passwordFor(String username) {
      ConnectionProfiles profiles = ProfileRuntime.profiles();
      String saved = profiles == null ? null : profiles.lobbyPasswordFor(username);
      if (saved != null) return saved;
      String password = loginPassword.get();
      return loginAccount.get().strip().equalsIgnoreCase(username) && !password.isEmpty() ? password : null;
   }

   /** System chat, on the client thread: reads the lobby's answer to /login. */
   public void onChat(String message) {
      ClientPacketListener receivedOn = mc.getConnection();
      if (receivedOn == null || receivedOn != connection || location != LobbyLocation.CRACKED_LOBBY || loginResult == LobbyLogin.Result.SUCCESS) return;
      if (ChatSender.fromPlayer(message, name -> receivedOn.getPlayerInfoIgnoreCase(name) != null)) return;
      LobbyLogin.Result result = LobbyLogin.classify(message);
      if (result == LobbyLogin.Result.SUCCESS || loginSent && loginResult != LobbyLogin.Result.FAILURE && result == LobbyLogin.Result.FAILURE) {
         loginResult = result;
         if (result == LobbyLogin.Result.FAILURE) {
            loginHold.rejected();
            notice("The server rejected /login. Log in manually to continue.");
         } else {
            loginHold.loggedIn(receivedOn, POST_LOGIN_DELAY);
         }
      }
   }

   private void findPortal() {
      failedPortals.entrySet().removeIf(entry -> System.nanoTime() - entry.getValue() >= 0);
      var found = LobbyPortalFinder.nearest(mc.player.position(), SEARCH_RADIUS, SEARCH_HEIGHT, world.getMinY(), world.getMaxY() - 1, probe);
      if (found.isEmpty()) return;
      portal = found.get().pos();
      lastPosition = mc.player.position();
      stuckTicks = 0;
      pathTicks = 0;
      BaritoneBridge bridge = ClientContext.baritoneBridge;
      if (bridge != null && bridge.isAvailable() && !bridge.isPathingOrCalculating() && Vec3.atCenterOf(portal).distanceToSqr(lastPosition) > 16) {
         bridge.pathTo(portal);
         pathGoal = portal;
      }
   }

   private void walkToPortal() {
      var player = mc.player;
      Vec3 center = Vec3.atCenterOf(portal);
      if (pathGoal != null) {
         BaritoneBridge bridge = ClientContext.baritoneBridge;
         if (++pathTicks <= 400 && center.distanceToSqr(player.position()) > 16 && (pathTicks <= 20 || bridge.isPathingOrCalculating())) return;
         stopPathing();
         lastPosition = player.position();
      }
      if (player.position().distanceToSqr(lastPosition) >= 0.0025) {
         lastPosition = player.position();
         stuckTicks = 0;
      } else if (++stuckTicks > 100) {
         if (failedPortals.size() >= 16) failedPortals.clear();
         failedPortals.put(portal, System.nanoTime() + 120_000_000_000L);
         stopWalking();
         scanTicks = 0;
         return;
      }
      double dx = center.x - player.getX();
      double dz = center.z - player.getZ();
      player.setYRot((float)Math.toDegrees(Math.atan2(-dx, dz)));
      mc.options.keyUp.setDown(true);
      mc.options.keyJump.setDown(player.onGround() && (player.horizontalCollision || center.y > player.getY() + 1.1));
      holdingMovement = true;
   }

   private boolean insidePortal() {
      var body = mc.player.getBoundingBox();
      for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY, body.minZ), BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
         if (probe.loaded(pos.getX() >> 4, pos.getZ() >> 4) && world.getBlockState(pos).is(Blocks.NETHER_PORTAL) && body.intersects(new AABB(pos))) {
            return true;
         }
      }
      return false;
   }

   private void stopPathing() {
      if (pathGoal == null) return;
      BaritoneBridge bridge = ClientContext.baritoneBridge;
      if (bridge != null && bridge.isAvailable() && pathGoal.equals(bridge.getGoalPosition())) bridge.cancelPathing();
      pathGoal = null;
   }

   private void stopWalking() {
      stopPathing();
      portal = null;
      if (!holdingMovement) return;
      mc.options.keyUp.setDown(false);
      mc.options.keyJump.setDown(false);
      holdingMovement = false;
   }

   private void notice(String message) {
      if (mc.player != null) mc.gui.getChat().addClientSystemMessage(Component.literal("[ElytraTrip] " + message));
   }
}
