package dev.argus.client.utils.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.utils.entity.SwingAction;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.network.PacketSender;
import java.util.function.BooleanSupplier;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Volytra Fly's liquid takeoff, the timed double jump out of water or lava done for you.
 *
 * <p>Hold jump: while your head is under, you swim straight up at the Rise speed. Once your head is out no speed is
 * forced; vanilla movement carries you up, and at the top of that rise (the tick the next move would no longer go up)
 * you are lifted straight up at the Lift speed, the wings go on (Smart Elytra) and the glide starts.
 *
 * <p>Three cases, each switched and tuned on its own:
 * <ul>
 *   <li><b>Lava</b>: the server lets a glide start in lava, so it starts right at the peak.</li>
 *   <li><b>Water</b>: the server refuses a glide while any part of you touches water, so at the peak the Lift is held
 *   until you are clear, and the glide starts on the first tick out.</li>
 *   <li><b>Swim</b>: in the sprint-swim pose. Forcing upward speed on the flat 0.6-block swim hitbox is not a move the
 *   server can replay, so it rubber bands. Sprint is dropped first so the player stands back up (one or two ticks of
 *   plain vanilla movement), then it runs as Water with the Swim numbers.</li>
 * </ul>
 */
public final class LiquidTakeoff {
   /** One case's switch and speeds (blocks/tick). */
   record Phase(boolean enabled, double rise, double lift) {}

   /** The settings it reads, read fresh each tick. */
   record Options(Phase water, Phase swim, Phase lava, boolean holdJump, int glideDelay, boolean firework) {}

   /** How long after leaving the liquid the top of the rise can still start a takeoff. */
   static final int PEAK_WINDOW = 10;
   /** Upward speed vanilla adds each tick jump is held in water or lava, before the move. */
   static final double LIQUID_JUMP_BOOST = 0.04;
   /** A glide that still has not started this long after the attempt is given up. */
   static final int MAX_AIR_TICKS = 40;

   /** Ticks since the player last touched water or lava; -1 once they are well clear of it. */
   private int ticksOutOfLiquid = -1;
   /** The liquid last touched was lava (otherwise water). */
   private boolean lastLiquidLava;
   /** This takeoff started in the swim pose, so it keeps the Swim numbers after the player stands up. */
   private boolean fromSwim;
   /** Feet height at the previous tick, to tell that the player is rising; NaN when not tracking. */
   private double lastY = Double.NaN;
   /** Past the peak in water: lifting straight up until clear of it, where the glide can start. */
   private boolean lifting;
   /** Ticks since a glide was asked for and not yet confirmed; -1 when none is pending. */
   private int airTicks = -1;
   private boolean rocketUsed;

   void reset() {
      this.ticksOutOfLiquid = -1;
      this.lastLiquidLava = false;
      this.fromSwim = false;
      this.lastY = Double.NaN;
      this.lifting = false;
      this.airTicks = -1;
      this.rocketUsed = false;
   }

   /**
    * Runs once per tick before the player moves.
    *
    * @param equipGlider puts wings on if the player isn't wearing any (Smart Elytra); returns whether they are on
    */
   void tick(LocalPlayer player, Options options, BooleanSupplier equipGlider) {
      if (player.isPassenger() || player.getAbilities().flying) {
         this.reset();
         return;
      }

      boolean inLava = player.isInLava();
      boolean inWater = !inLava && player.isInWater();
      if (inLava || inWater) {
         if (this.ticksOutOfLiquid < 0 || inLava != this.lastLiquidLava) this.fromSwim = false;
         this.ticksOutOfLiquid = 0;
         this.lastLiquidLava = inLava;
      } else if (this.ticksOutOfLiquid >= 0 && (++this.ticksOutOfLiquid > PEAK_WINDOW || player.onGround())) {
         this.ticksOutOfLiquid = -1;
         this.fromSwim = false;
         this.lastY = Double.NaN;
         this.lifting = false;
      }

      if (player.isFallFlying()) {
         this.gliding(player, options, inLava || inWater);
         return;
      }

      // A glide was asked for out of the liquid and the server has not confirmed it (or refused): try again.
      if (this.airTicks >= 0 && !inLava && !inWater) {
         this.airTicks++;
         if (player.onGround() || this.airTicks > MAX_AIR_TICKS) {
            this.airTicks = -1;
         } else if (this.airTicks >= options.glideDelay()) {
            this.startGlide(player, equipGlider);
         }
         return;
      }

      if (this.ticksOutOfLiquid < 0) return;
      this.nearLiquid(player, options, inLava, inWater, equipGlider);
   }

   /** In the liquid or just above it: swim up, find the top of the rise, lift and glide. */
   private void nearLiquid(LocalPlayer player, Options options, boolean inLava, boolean inWater, BooleanSupplier equipGlider) {
      boolean triggered = !options.holdJump() || player.input.keyPresses.jump();
      if (!triggered) {
         this.lastY = Double.NaN;
         this.lifting = false;
         return;
      }

      boolean swimPose = swimPose(player);
      if (inWater && swimPose) this.fromSwim = true;
      Phase phase = this.lastLiquidLava ? options.lava() : this.fromSwim ? options.swim() : options.water();
      if (!phase.enabled()) {
         this.lastY = Double.NaN;
         this.lifting = false;
         return;
      }

      if (this.fromSwim) {
         // Keep sprint off for the whole takeoff, or holding sprint drops you straight back into the swim pose.
         ClientContext.inputOverrides.blockSprint();
         if (player.isSprinting()) player.setSprinting(false);
         // Still flat: leave this tick to vanilla so the pose change is a move the server agrees with.
         if (swimPose) {
            this.lastY = Double.NaN;
            return;
         }
      }

      Vec3 motion = player.getDeltaMovement();
      if (this.lifting) {
         if (inWater) {
            player.setDeltaMovement(motion.x, Math.max(motion.y, phase.lift()), motion.z);
            return;
         }
         this.lifting = false;
         this.startGlide(player, equipGlider);
         return;
      }

      boolean inLiquid = inLava || inWater;
      if (inLiquid && player.getFluidHeight(inLava ? FluidTags.LAVA : FluidTags.WATER) > player.getEyeHeight() + 0.1) {
         // Head under: swim straight up to the surface; the peak is only looked for once the head is out.
         player.setDeltaMovement(motion.x, Math.max(motion.y, phase.rise()), motion.z);
         this.lastY = Double.NaN;
         return;
      }

      double y = player.getY();
      double previousY = this.lastY;
      this.lastY = y;
      // Holding jump in a liquid, vanilla adds its swim-up speed before this tick's move.
      double nextRise = motion.y + (inLiquid ? LIQUID_JUMP_BOOST : 0.0);
      if (!atPeak(previousY, y, nextRise)) return;

      // The top of the rise: straight up from here.
      this.lastY = Double.NaN;
      player.setDeltaMovement(motion.x, Math.max(motion.y, phase.lift()), motion.z);
      if (inWater) {
         // No glide while touching water; keep lifting until clear.
         this.lifting = true;
         return;
      }
      this.startGlide(player, equipGlider);
   }

   /** Puts the wings on and opens them; a failed or refused attempt is retried from the air. */
   private void startGlide(LocalPlayer player, BooleanSupplier equipGlider) {
      if (this.airTicks < 0) {
         this.airTicks = 0;
         this.rocketUsed = false;
      }
      if (!equipGlider.getAsBoolean()) return;
      PacketSender.sendStartFallFlying();
      player.startFallFlying();
   }

   /** Gliding: fire the optional rocket for a takeoff of ours once clear of the liquid, then the takeoff is done. */
   private void gliding(LocalPlayer player, Options options, boolean inLiquid) {
      this.lastY = Double.NaN;
      this.lifting = false;
      if (this.airTicks < 0) return;
      if (inLiquid) return;
      if (options.firework() && !this.rocketUsed) {
         this.rocketUsed = true;
         useRocket(player);
      }
      this.airTicks = -1;
   }

   /**
    * The top of a rise: the player went up on the last tick ({@code previousY} to {@code y}) and this tick's move
    * ({@code nextRise}, blocks/tick) would no longer take them higher.
    */
   static boolean atPeak(double previousY, double y, double nextRise) {
      if (Double.isNaN(previousY)) return false;
      return y > previousY + 1.0E-4 && nextRise <= 0.0;
   }

   /** In the flat swimming pose, or about to be: the hitbox is the 0.6 block one until the pose catches up. */
   private static boolean swimPose(LocalPlayer player) {
      return player.isSwimming() || player.getPose() == Pose.SWIMMING;
   }

   private static void useRocket(LocalPlayer player) {
      if (player.isUsingItem()) return;
      int selected = player.getInventory().getSelectedSlot();
      InventoryUtils.SwapSlot rocket = InventoryUtils.findSwapSlot(Items.FIREWORK_ROCKET, true);
      if (!rocket.isFound()) return;
      InventoryUtils.swapTo(rocket);
      try {
         ClientAccess.argusclientMinecraft.gameMode.useItem(player, rocket.hand());
         ClientContext.swingManager.swingPacket(InteractionHand.MAIN_HAND, SwingAction.INTERACT);
      } finally {
         InventoryUtils.swapBack(selected, rocket);
      }
   }
}
