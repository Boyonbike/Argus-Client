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
 * Volytra Fly's liquid takeoff: hold jump in water or lava to swim straight up, get thrown clear when your head
 * reaches the surface, and the glide starts once you are in the air, with an optional firework to carry you away.
 *
 * <p>Three cases, each switched and tuned on its own:
 * <ul>
 *   <li><b>Water</b>: upright in water; rise, then launch.</li>
 *   <li><b>Swim</b>: in the sprint-swim pose. Forcing upward speed on the flat 0.6-block swim hitbox is not a move the
 *   server can replay, so it rubber bands. Instead sprint is dropped first so the player stands back up (one or two
 *   ticks of plain vanilla movement), and only then the Water rise and launch run, with the Swim numbers.</li>
 *   <li><b>Lava</b>: lava halves horizontal speed every tick you are in it, so a push given at launch is gone before
 *   you clear the surface and you drop back in. The push is held back until the first tick in the air, and the launch
 *   speed is kept up for as long as any part of you is still in the lava.</li>
 * </ul>
 */
public final class LiquidTakeoff {
   /** One case's switch and speeds. */
   record Phase(boolean enabled, double rise, double launch) {}

   /** The settings it reads, read fresh each tick. */
   record Options(Phase water, Phase swim, Phase lava, boolean holdJump, double push, int glideDelay, boolean firework) {}

   /** Ticks since the launch threw the player clear of the liquid; -1 while not taking off. */
   private int airTicks = -1;
   private boolean rocketUsed;
   /** This takeoff started in the swim pose, so it keeps the Swim numbers after the player stands up. */
   private boolean fromSwim;
   /** A lava launch whose forward push is still owed, given once the player is out of the lava. */
   private boolean lavaPushPending;

   void reset() {
      this.airTicks = -1;
      this.rocketUsed = false;
      this.fromSwim = false;
      this.lavaPushPending = false;
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

      boolean triggered = !options.holdJump() || player.input.keyPresses.jump();
      boolean inLava = player.isInLava();
      boolean inWater = !inLava && player.isInWater();
      if (inWater || inLava) {
         if (!triggered || player.isFallFlying()) {
            this.fromSwim = false;
            return;
         }
         boolean swimPose = swimPose(player);
         if (inWater && swimPose) this.fromSwim = true;
         Phase phase = inLava ? options.lava() : this.fromSwim ? options.swim() : options.water();
         if (!phase.enabled()) return;

         if (this.fromSwim) {
            // Keep sprint off for the whole takeoff, or holding sprint drops you straight back into the swim pose.
            ClientContext.inputOverrides.blockSprint();
            if (player.isSprinting()) player.setSprinting(false);
            // Still flat: leave this tick to vanilla so the pose change is a move the server agrees with.
            if (swimPose) return;
         }

         this.surface(player, inLava, phase, options.push());
         this.airTicks = 0;
         this.rocketUsed = false;
         this.lavaPushPending = inLava;
         return;
      }

      this.fromSwim = false;
      if (this.airTicks < 0) return;
      if (player.onGround() || this.airTicks > 40) {
         this.reset();
         return;
      }

      this.airTicks++;
      if (this.lavaPushPending) {
         this.lavaPushPending = false;
         Vec3 motion = player.getDeltaMovement();
         Vec3 push = forward(player, options.push());
         player.setDeltaMovement(motion.x + push.x, motion.y, motion.z + push.z);
      }
      if (!player.isFallFlying()) {
         if (this.airTicks >= options.glideDelay() && equipGlider.getAsBoolean()) {
            PacketSender.sendStartFallFlying();
            player.startFallFlying();
         }
         return;
      }
      if (options.firework() && !this.rocketUsed) {
         this.rocketUsed = true;
         useRocket(player);
      }
      // Gliding: the takeoff is done.
      this.airTicks = -1;
   }

   /** In the flat swimming pose, or about to be: the hitbox is the 0.6 block one until the pose catches up. */
   private static boolean swimPose(LocalPlayer player) {
      return player.isSwimming() || player.getPose() == Pose.SWIMMING;
   }

   /** Swims straight up, and throws the player clear once their head is near the surface. */
   private void surface(LocalPlayer player, boolean lava, Phase phase, double push) {
      double depth = player.getFluidHeight(lava ? FluidTags.LAVA : FluidTags.WATER);
      Vec3 motion = player.getDeltaMovement();
      if (depth > player.getEyeHeight() + 0.1) {
         player.setDeltaMovement(motion.x, Math.max(motion.y, phase.rise()), motion.z);
         return;
      }

      double x = motion.x, z = motion.z;
      // In lava the push would be halved away every tick before you clear it; it is given in the air instead.
      if (!lava) {
         Vec3 forward = forward(player, push);
         x += forward.x;
         z += forward.z;
      }
      player.setDeltaMovement(x, Math.max(motion.y, phase.launch()), z);
   }

   private static Vec3 forward(LocalPlayer player, double speed) {
      if (speed <= 0) return Vec3.ZERO;
      double yaw = Math.toRadians(player.getYRot());
      return new Vec3(-Math.sin(yaw) * speed, 0, Math.cos(yaw) * speed);
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
