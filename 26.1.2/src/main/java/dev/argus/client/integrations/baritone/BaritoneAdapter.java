package dev.argus.client.integrations.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings.Setting;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.event.events.RotationMoveEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.WorldEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.calc.IPathingControlManager;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.process.IElytraProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.selection.ISelection;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.interfaces.IGoalRenderPos;
import dev.argus.client.ClientAccess;
import dev.argus.client.NamedFeature;
import dev.argus.client.events.bus.EventBus;
import dev.argus.client.utils.chat.ChatUtils;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BaritoneAdapter implements ClientAccess, BaritoneBridge {
   private final List<java.util.function.Predicate<String>> messageListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
   private final ObjectSet<NamedFeature> pauseRequests = ObjectSets.synchronize(new ObjectOpenHashSet());
   /** The height Baritone's own #elytra gives a goal without one; its pathfinder works out the real height. */
   private static final int XZ_GOAL_Y = 64;
   private float[] latestHeading;
   private LocalPlayer headingPlayer;
   private int headingTick = -1;
   private boolean rocketless;
   private int savedMinFireworks;
   private double savedFireworkSpeed;

   @Override
   public void pathElytraTo(BlockPos position) {
      IElytraProcess process = BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess();
      process.pathTo(position);
   }

   @Override
   public void setGoal(BlockPos position) {
      GoalBlock goal = new GoalBlock(position);
      BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(goal);
   }

   @Override
   public boolean isPathingOrCalculating() {
      IPathingBehavior pathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior();
      return pathing.isPathing() || pathing.getInProgress().isPresent();
   }

   @Override
   public boolean isInSelection(BlockPos position) {
      IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      if (baritone.getSelectionManager().getLastSelection() == null) {
         return false;
      }

      ISelection[] selections = baritone.getSelectionManager().getSelections();

      for (ISelection selection : selections) {
         BetterBlockPos minimum = selection.min();
         BetterBlockPos maximum = selection.max();
         AABB bounds = AABB.encapsulatingFullBlocks(minimum, maximum);
         Vec3 center = position.getCenter();
         if (bounds.contains(center)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean isPauseRequested() {
      return !this.pauseRequests.isEmpty();
   }

   @Override
   public BlockPos getGoalPosition() {
      Goal goal = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().getGoal();
      if (goal instanceof IGoalRenderPos renderGoal) {
         return renderGoal.getGoalPos();
      } else if (goal instanceof GoalXZ horizontalGoal) {
         double x = horizontalGoal.getX();
         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         double y = player.getY();
         double z = horizontalGoal.getZ();
         return BlockPos.containing(x, y, z);
      } else {
         return null;
      }
   }

   @Override
   public void pathTo(BlockPos position) {
      GoalBlock goal = new GoalBlock(position);
      BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
   }

   @Override
   public void cancelPathing() {
      IPathingBehavior pathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior();
      pathing.cancelEverything();
      pathing.forceCancel();
   }

   @Override
   public boolean isAvailable() {
      return true;
   }

   public BaritoneAdapter() {
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
      IPathingControlManager controlManager = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingControlManager();
      controlManager.registerProcess(new BaritoneAdapter.PauseProcess());
      this.registerElytraHeadingListener();
      Setting messageTagSetting = BaritoneAPI.getSettings().useMessageTag;
      messageTagSetting.value = false;
      BaritoneAPI.getSettings().logger.value = (Consumer<Component>)message -> {
         String text = message.getString();
         boolean hide = false;
         for (java.util.function.Predicate<String> listener : this.messageListeners) {
            try {
               hide |= listener.test(text);
            } catch (RuntimeException e) {
               dev.argus.client.ArgusClient.LOGGER.error("A Baritone message listener failed", e);
            }
         }
         if (hide) {
            return;
         }
         MutableComponent copiedMessage = message.copy();
         List<Component> siblings = copiedMessage.getSiblings();
         siblings.addFirst(Component.literal(" "));
         siblings = copiedMessage.getSiblings();
         siblings.addFirst(ChatUtils.createPrefix());
         Gui gui = ClientAccess.argusclientMinecraft.gui;
         ChatComponent chat = gui.getChat();
         GuiMessageTag tag = ChatUtils.MESSAGE_TAG;
         chat.addPlayerMessage(copiedMessage, null, tag);
      };
   }

   @Override
   public void requestPause(NamedFeature feature) {
      if (!this.pauseRequests.contains(feature)) {
         this.pauseRequests.add(feature);
      }
   }

   @Override
   public void releasePause(NamedFeature feature) {
      if (this.pauseRequests.contains(feature)) {
         this.pauseRequests.remove(feature);
      }
   }

   @Override
   public void pathForward() {
      Vec3 playerPosition = ClientAccess.argusclientMinecraft.player.position();
      float playerYaw = ClientAccess.argusclientMinecraft.player.getYRot();
      GoalXZ goal = GoalXZ.fromDirection(playerPosition, playerYaw, 5.0);
      BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
   }

   /**
    * Baritone registers its look behaviour first, so this listener sees the rotation it moves the player with. Its event bus
    * has no unregister, so this one listener lives as long as the adapter.
    */
   private void registerElytraHeadingListener() {
      IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      baritone.getGameEventHandler().registerEventListener(new AbstractGameEventListener() {
         @Override
         public void onPlayerRotationMove(RotationMoveEvent event) {
            if (event.getType() != RotationMoveEvent.Type.MOTION_UPDATE) {
               return;
            }
            BaritoneAdapter.this.latestHeading = null;
            LocalPlayer player = baritone.getPlayerContext().player();
            if (!BaritoneAdapter.isSteering(baritone, player)) {
               return;
            }
            float yaw = event.getYaw();
            float pitch = event.getPitch();
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
               return;
            }
            // With no solution yet Baritone leaves the event alone, which is just where the camera points.
            if (yaw == player.getYRot() && pitch == player.getXRot()) {
               return;
            }
            BaritoneAdapter.this.latestHeading = new float[]{yaw, pitch};
            BaritoneAdapter.this.headingPlayer = player;
            BaritoneAdapter.this.headingTick = player.tickCount;
         }

         @Override
         public void onTick(TickEvent event) {
            BaritoneAdapter.this.latestHeading = null;
         }

         @Override
         public void onWorldEvent(WorldEvent event) {
            BaritoneAdapter.this.latestHeading = null;
         }
      });
   }

   private static IBaritone primary() {
      return BaritoneAPI.getProvider().getPrimaryBaritone();
   }

   private static boolean isSteering(IBaritone baritone, LocalPlayer player) {
      // Any gliding moment Baritone's elytra process is in charge of. Its status text isn't checked: it reads "Begin flying"
      // well into a flight and differs between Baritone builds, and every tick it rejected went unpowered.
      if (player == null || !player.isFallFlying()) {
         return false;
      }
      IElytraProcess elytra = baritone.getElytraProcess();
      return elytra.isLoaded() && elytra.isActive() && baritone.getPathingControlManager().mostRecentInControl().orElse(null) == elytra;
   }

   @Override
   public WalkingTrip walkingTrip() {
      IBaritone baritone = primary();
      ICustomGoalProcess process = baritone.getCustomGoalProcess();
      IPathingControlManager control = baritone.getPathingControlManager();
      // #goal on its own also makes the process active, but only shows the goal. Walking means it asked for a path.
      boolean walking = control.mostRecentInControl().orElse(null) == process
         && control.mostRecentCommand()
            .map(command -> command.commandType == PathingCommandType.SET_GOAL_AND_PATH
               || command.commandType == PathingCommandType.REVALIDATE_GOAL_AND_PATH
               || command.commandType == PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH)
            .orElse(false);
      if (!walking) {
         return null;
      }
      Goal goal = process.getGoal();
      if (goal instanceof GoalXZ xz) {
         return new WalkingTrip(goal, new BlockPos(xz.getX(), XZ_GOAL_Y, xz.getZ()));
      } else if (goal instanceof IGoalRenderPos block) {
         return new WalkingTrip(goal, block.getGoalPos().immutable());
      } else {
         return null;
      }
   }

   @Override
   public void walk(WalkingTrip trip) {
      primary().getCustomGoalProcess().setGoalAndPath((Goal)trip.goal());
   }

   @Override
   public boolean isElytraSupported() {
      return primary().getElytraProcess().isLoaded();
   }

   @Override
   public int elytraMinimumDurability() {
      return BaritoneAPI.getSettings().elytraMinimumDurability.value;
   }

   @Override
   public void flyTo(BlockPos destination) {
      primary().getElytraProcess().pathTo(destination);
   }

   @Override
   public boolean isElytraActive() {
      return primary().getElytraProcess().isActive();
   }

   @Override
   public String elytraStatus() {
      IElytraProcess elytra = primary().getElytraProcess();
      return elytra.isActive() ? elytra.displayName() : "";
   }

   @Override
   public boolean isElytraSteering() {
      IBaritone baritone = primary();
      return isSteering(baritone, baritone.getPlayerContext().player());
   }

   @Override
   public float[] elytraHeading() {
      IBaritone baritone = primary();
      LocalPlayer player = baritone.getPlayerContext().player();
      if (this.latestHeading == null || player == null || player != this.headingPlayer || player.tickCount != this.headingTick
         || !isSteering(baritone, player)) {
         return null;
      }
      return this.latestHeading;
   }

   @Override
   public List<? extends BlockPos> elytraRoute() {
      IBaritone baritone = primary();
      if (!isSteering(baritone, baritone.getPlayerContext().player())) {
         return List.of();
      }
      List<? extends BlockPos> path = elytraPathOf(baritone.getElytraProcess());
      return path == null ? List.of() : path;
   }

   @Override
   public void setLavaRouting(boolean enabled, boolean thinOnly) {
      // Not both in one condition: each has to take effect whatever the other did.
      boolean changed = BaritoneElytraOverrides.setLavaPassable(enabled);
      changed |= BaritoneElytraOverrides.setThinLavaOnly(thinOnly) && enabled;
      if (!changed) {
         return;
      }
      IBaritone baritone = primary();
      IElytraProcess elytra = baritone.getElytraProcess();
      // Rebuilds the terrain Baritone knows about and plans again. It needs a world to check the goal against.
      if (!elytra.isActive() || baritone.getPlayerContext().player() == null || baritone.getPlayerContext().world() == null) {
         return;
      }
      try {
         elytra.resetState();
      } catch (IllegalArgumentException e) {
         // Out of Baritone's elytra bounds. The flight ends as it would on any other refused path.
      }
   }

   @Override
   public boolean lavaRoutingWorks() {
      return BaritoneElytraOverrides.lavaHookWorks();
   }

   @Override
   public void flyFresh(BlockPos destination) {
      IElytraProcess elytra = primary().getElytraProcess();
      // Letting go of the flight is what makes Baritone drop its pathfinder; pathTo then builds a new one.
      elytra.onLostControl();
      elytra.pathTo(destination);
   }

   @Override
   public void walkToXZ(int x, int z) {
      primary().getCustomGoalProcess().setGoalAndPath(new GoalXZ(x, z));
   }

   @Override
   public void addMessageListener(java.util.function.Predicate<String> listener) {
      this.messageListeners.add(listener);
   }

   @Override
   public boolean isElytraReadyForTakeoff() {
      IBaritone baritone = primary();
      IElytraProcess elytra = baritone.getElytraProcess();
      return elytra.isActive() && !elytra.displayName().contains("Waiting for elytra path") && !baritone.getPathingBehavior().isPathing();
   }

   @Override
   public boolean elytraAutoJump() {
      return BaritoneAPI.getSettings().elytraAutoJump.value;
   }

   @Override
   public List<? extends BlockPos> elytraPath() {
      IElytraProcess elytra = primary().getElytraProcess();
      if (!elytra.isActive()) {
         return List.of();
      }
      List<? extends BlockPos> path = elytraPathOf(elytra);
      return path == null ? List.of() : path;
   }

   @Override
   public void setRocketless(boolean enabled) {
      if (enabled == this.rocketless) {
         return;
      }
      baritone.api.Settings settings = BaritoneAPI.getSettings();
      if (enabled) {
         this.savedMinFireworks = settings.elytraMinFireworksBeforeLanding.value;
         this.savedFireworkSpeed = settings.elytraFireworkSpeed.value;
         // Never land for want of fireworks, and never want one for speed.
         settings.elytraMinFireworksBeforeLanding.value = -1;
         settings.elytraFireworkSpeed.value = 0.0;
      } else {
         settings.elytraMinFireworksBeforeLanding.value = this.savedMinFireworks;
         settings.elytraFireworkSpeed.value = this.savedFireworkSpeed;
      }
      this.rocketless = enabled;
      BaritoneElytraOverrides.setNoFireworks(enabled);
   }

   private boolean freeLookForced;
   private boolean savedFreeLook;
   private boolean tightForced;
   private boolean savedTight;

   @Override
   @SuppressWarnings("unchecked")
   public void setTightSpaces(boolean enabled) {
      // The 26.1 Baritone build has no elytraAllowTightSpaces setting, so it is looked up by name and skipped when absent.
      baritone.api.Settings.Setting<?> found = BaritoneAPI.getSettings().byLowerName.get("elytraallowtightspaces");
      if (found == null || !(found.value instanceof Boolean)) {
         return;
      }
      baritone.api.Settings.Setting<Boolean> tight = (baritone.api.Settings.Setting<Boolean>) found;
      if (enabled && !this.tightForced) {
         this.savedTight = tight.value;
         tight.value = true;
         this.tightForced = true;
      } else if (!enabled && this.tightForced) {
         tight.value = this.savedTight;
         this.tightForced = false;
      }
   }

   private static java.lang.reflect.Field elytraBehaviorField;
   private static java.lang.reflect.Field pathManagerField;
   private static java.lang.reflect.Field netherPathField;
   private static boolean elytraPathUnavailable;

   /**
    * The planned elytra route. The 26.1 Baritone build has no IElytraProcess.getPath(), so this reads what 26.3's
    * getPath() returns (ElytraProcess -> ElytraBehavior -> PathManager -> NetherPath) by field type, since release jars
    * scramble field names.
    */
   @SuppressWarnings("unchecked")
   private static List<? extends BlockPos> elytraPathOf(IElytraProcess elytra) {
      if (elytra == null || elytraPathUnavailable || !elytra.getClass().getName().equals("baritone.process.ElytraProcess")) {
         return List.of();
      }
      try {
         if (netherPathField == null) {
            elytraBehaviorField = fieldOfType(elytra.getClass(), "baritone.process.elytra.ElytraBehavior");
            pathManagerField = fieldOfType(elytraBehaviorField.getType(), "baritone.process.elytra.ElytraBehavior$PathManager");
            netherPathField = fieldOfType(pathManagerField.getType(), "baritone.process.elytra.NetherPath");
         }
         Object behavior = elytraBehaviorField.get(elytra);
         if (behavior == null) {
            return List.of();
         }
         Object manager = pathManagerField.get(behavior);
         Object path = manager == null ? null : netherPathField.get(manager);
         return path instanceof List<?> list ? (List<? extends BlockPos>) list : List.of();
      } catch (ReflectiveOperationException | RuntimeException e) {
         elytraPathUnavailable = true;
         return List.of();
      }
   }

   private static java.lang.reflect.Field fieldOfType(Class<?> owner, String typeName) throws NoSuchFieldException {
      for (java.lang.reflect.Field field : owner.getDeclaredFields()) {
         if (field.getType().getName().equals(typeName)) {
            field.setAccessible(true);
            return field;
         }
      }
      throw new NoSuchFieldException(typeName + " in " + owner.getName());
   }

   @Override
   public void setFreeLook(boolean enabled) {
      baritone.api.Settings settings = BaritoneAPI.getSettings();
      if (enabled && !this.freeLookForced) {
         this.savedFreeLook = settings.freeLook.value;
         settings.freeLook.value = true;
         this.freeLookForced = true;
      } else if (!enabled && this.freeLookForced) {
         settings.freeLook.value = this.savedFreeLook;
         this.freeLookForced = false;
      }
   }

   private class PauseProcess implements IBaritoneProcess {
      private static final String displayName = "Argus Client pause service";

      public double priority() {
         return 999.0;
      }

      public boolean isActive() {
         return !BaritoneAdapter.this.pauseRequests.isEmpty();
      }

      public boolean isTemporary() {
         return true;
      }

      public void onLostControl() {
      }

      public String displayName0() {
         return displayName;
      }

      public PathingCommand onTick(boolean calculationFailed, boolean safeToCancel) {
         return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
      }
   }
}
