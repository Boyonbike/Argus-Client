package dev.argus.client.integrations.baritone;

import dev.argus.client.NamedFeature;
import java.util.List;
import net.minecraft.core.BlockPos;

public interface BaritoneBridge {
   void pathElytraTo(BlockPos var1);

   void setGoal(BlockPos var1);

   boolean isPathingOrCalculating();

   boolean isInSelection(BlockPos var1);

   boolean isPauseRequested();

   BlockPos getGoalPosition();

   void pathTo(BlockPos var1);

   void cancelPathing();

   boolean isAvailable();

   void requestPause(NamedFeature var1);

   void releasePause(NamedFeature var1);

   void pathForward();

   /**
    * The place a #goto, #path or waypoint walk is heading for, or null when Baritone is walking nowhere or towards something
    * that isn't one place, such as a Y level. Two trips are the same only when {@code goal} is the same object.
    */
   default WalkingTrip walkingTrip() {
      return null;
   }

   /** Walks to this trip's goal again, as the #goto that started it did. */
   default void walk(WalkingTrip trip) {
   }

   /** False when Baritone ships no native elytra pathfinder for this CPU and operating system. */
   default boolean isElytraSupported() {
      return false;
   }

   /** Baritone's elytraMinimumDurability. */
   default int elytraMinimumDurability() {
      return 0;
   }

   /** Hands the destination to Baritone's elytra process. Throws IllegalArgumentException with Baritone's reason when it refuses. */
   default void flyTo(BlockPos destination) {
   }

   /** True while Baritone's elytra process is running a flight, from waiting for its path to touching down. */
   default boolean isElytraActive() {
      return false;
   }

   /** Baritone's own name for what its elytra process is doing, such as "Elytra - Flying", or an empty string. */
   default String elytraStatus() {
      return "";
   }

   /** True while the elytra process is in control and the player is gliding through its cruise or landing states. */
   default boolean isElytraSteering() {
      return false;
   }

   /**
    * The rotation Baritone moved the player with this tick, or null when it gave none. This follows its elytra route even
    * when FreeLook leaves the camera elsewhere.
    */
   default float[] elytraHeading() {
      return null;
   }

   /** Baritone's current elytra route while it is steering, otherwise empty. */
   default List<? extends BlockPos> elytraRoute() {
      return List.of();
   }

   /** While enabled, Baritone never asks for or fires a firework; false restores the user's Baritone settings. */
   default void setRocketless(boolean enabled) {
   }

   /**
    * Lets Baritone's elytra pathfinder use gaps narrower than four blocks, for getting out of a congested area it otherwise
    * finds no path from; false restores the user's own setting. Baritone starts its route over when this changes.
    */
   default void setTightSpaces(boolean enabled) {
   }

   /** Turns Baritone's freeLook on or off; false restores the user's own setting. */
   default void setFreeLook(boolean enabled) {
   }

   /**
    * True once the elytra process has its path and is waiting for the player to get airborne. Baritone doesn't expose its
    * state, so this reads its status text: it stays on "Waiting for elytra path" until the pathfinder has one.
    */
   default boolean isElytraReadyForTakeoff() {
      return false;
   }

   /** Baritone's elytraAutoJump: Baritone finds a drop and takes off by itself. */
   default boolean elytraAutoJump() {
      return false;
   }

   /**
    * Lets Baritone's elytra routes go through lava; {@code thinOnly} keeps lava seas solid. Needs Meteor's baritone-meteor
    * build, whose elytra classes can be patched: see {@link #lavaRoutingWorks}.
    */
   default void setLavaRouting(boolean enabled, boolean thinOnly) {
   }

   /** False when this Baritone build can't be patched to route through lava, once it has had the chance to run. */
   default boolean lavaRoutingWorks() {
      return false;
   }

   /** Drops Baritone's elytra pathfinder and starts a new one to this destination, for a route that keeps failing. */
   default void flyFresh(BlockPos destination) {
      flyTo(destination);
   }

   /** Walks towards this X/Z at whatever height gets there. */
   default void walkToXZ(int x, int z) {
   }

   /**
    * Hears every message Baritone writes to chat, as plain text, before it's shown. Returning true hides that message.
    * Baritone's own event bus has no unregister, so neither has this: listeners stay for the game.
    */
   default void addMessageListener(java.util.function.Predicate<String> listener) {
   }

   /** Baritone's current elytra route while its elytra process is active, gliding or not; otherwise empty. For drawing. */
   default List<? extends BlockPos> elytraPath() {
      return List.of();
   }

   /** @param goal Baritone's goal object; {@code target} is the block handed to the elytra process */
   record WalkingTrip(Object goal, BlockPos target) {
   }
}
