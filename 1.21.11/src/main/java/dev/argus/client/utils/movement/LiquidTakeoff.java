package dev.argus.client.utils.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.utils.entity.SwingAction;
import dev.argus.client.utils.inventory.InventoryUtils;
import dev.argus.client.utils.network.PacketSender;
import java.util.function.BooleanSupplier;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Volytra Fly's liquid takeoff: hold jump in water or lava to swim straight up, get thrown clear when your head
 * reaches the surface, and the glide starts once you are in the air, with an optional firework to carry you away.
 */
public final class LiquidTakeoff {
   /** Which liquids the takeoff works in. */
   public enum Liquid implements NamedEntry {
      WATER("Water"),
      LAVA("Lava"),
      BOTH("Both");

      private final String displayName;

      Liquid(String displayName) {
         this.displayName = displayName;
      }

      boolean water() {
         return this != LAVA;
      }

      boolean lava() {
         return this != WATER;
      }

      @Override
      public String getName() {
         return this.displayName;
      }
   }

   /** The settings it reads, read fresh each tick. */
   record Options(Liquid liquid, boolean holdJump, double waterRise, double waterLaunch, double lavaRise, double lavaLaunch,
                  double push, int glideDelay, boolean firework) {}

   /** Ticks since the launch threw the player clear of the liquid; -1 while not taking off. */
   private int airTicks = -1;
   private boolean rocketUsed;

   void reset() {
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
         this.airTicks = -1;
         return;
      }

      boolean triggered = !options.holdJump() || player.input.keyPresses.jump();
      boolean inWater = options.liquid().water() && player.isInWater();
      boolean inLava = options.liquid().lava() && player.isInLava();
      if (inWater || inLava) {
         if (!triggered || player.isFallFlying()) return;
         this.surface(player, inLava, options);
         this.airTicks = 0;
         this.rocketUsed = false;
         return;
      }

      if (this.airTicks < 0) return;
      if (player.onGround() || player.isInWater() || player.isInLava() || this.airTicks > 40) {
         this.airTicks = -1;
         return;
      }

      this.airTicks++;
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

   /** Swims straight up, and throws the player clear once their head is near the surface. */
   private void surface(LocalPlayer player, boolean lava, Options options) {
      double depth = player.getFluidHeight(lava ? FluidTags.LAVA : FluidTags.WATER);
      Vec3 motion = player.getDeltaMovement();
      if (depth > player.getEyeHeight() + 0.1) {
         double rise = lava ? options.lavaRise() : options.waterRise();
         player.setDeltaMovement(motion.x, Math.max(motion.y, rise), motion.z);
         return;
      }

      double launch = lava ? options.lavaLaunch() : options.waterLaunch();
      double x = motion.x, z = motion.z;
      if (options.push() > 0) {
         double yaw = Math.toRadians(player.getYRot());
         x += -Math.sin(yaw) * options.push();
         z += Math.cos(yaw) * options.push();
      }
      player.setDeltaMovement(x, Math.max(motion.y, launch), z);
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
