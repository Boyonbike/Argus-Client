package dev.argus.client.modules.movement;

import dev.argus.client.ArgusClient;
import dev.argus.client.integrations.baritone.BaritoneBridge;
import dev.argus.client.utils.network.PacketSender;
import java.util.function.Consumer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

/**
 * Gets the player into the air for a Baritone elytra flight and climbs clear of the floor, shared by BaritoneElytra and
 * ElytraTrip. Without fireworks Baritone never takes off from flat ground, and once gliding its pitch dives back into the
 * floor, so this jumps, opens the wings near the top of the jump and holds a climb until there's room below. Ported from
 * Comet's Baritone Elytra.
 */
public final class BaritoneTakeoff {
   /** Ticks Baritone gets to find a flight path before the take-off counts as failed. */
   public static final int PATH_TICKS = 200;
   /** Rest after a round of failed tries before starting over. */
   private static final int PAUSE_TICKS = 100;
   private static final double HEADROOM = 3.0;
   /** Upward speed the wings open at from the lava surface, as ElytraFly's lava take-off lifts by default. */
   private static final double LAVA_LIFT = 0.2;
   /** Ticks before trying the wings again from lava after the server dropped them. */
   private static final int LAVA_OPEN_RETRY_TICKS = 10;

   public enum Result { WAITING, TIMED_OUT, NO_WAY_OUT }

   private final ElytraTakeoff takeoff = new ElytraTakeoff();
   private final FluidEscape escape = new FluidEscape();
   private final String name;
   private int pathWait;
   private boolean glided;
   private boolean warned;
   private int nextGlideLog;
   private int nextLavaLog;
   private int lavaOpenWait;
   private static final int GLIDE_LOG_TICKS = 100;

   /** @param name how the log names the module doing the take-off */
   public BaritoneTakeoff(String name) {
      this.name = name;
   }

   /** A new flight. @param airborne true when it starts in the air, so a later landing isn't taken for a take-off */
   public void reset(boolean airborne) {
      this.takeoff.reset(airborne);
      this.takeoff.forgetRounds();
      this.pathWait = 0;
      this.glided = airborne;
      this.warned = false;
      this.escape.reset();
      this.lavaOpenWait = 0;
   }

   /**
    * Once per tick while Baritone's elytra process runs this flight. Jumps and opens the wings once Baritone has its path,
    * unless Baritone's own elytraAutoJump is doing that. In water, or lava without {@code flyThroughLava}, it first swims
    * the player out to somewhere the wings can open; NO_WAY_OUT says there's nowhere within reach.
    */
   public Result tick(LocalPlayer player, BaritoneBridge baritone, boolean enabled, boolean flyThroughLava, Consumer<String> say) {
      boolean fallFlying = player.isFallFlying();
      this.glided |= fallFlying;
      if (this.takeoff.stable()) {
         this.warned = false;
      }
      String status = baritone.elytraStatus();
      if (!enabled || baritone.elytraAutoJump() || status.contains("Landing")) {
         return Result.WAITING;
      }
      boolean inWater = player.isInWater();
      boolean inLava = player.isInLava();
      // Gliding keeps going inside lava under power. Anything else in a liquid has to get out before the wings are any use.
      boolean powered = fallFlying && inLava && flyThroughLava;
      if ((inWater || inLava) && !powered) {
         if (inLava && !fallFlying && this.openInLava(player)) {
            return Result.WAITING;
         }
         if (!this.escape.push(player)) {
            ArgusClient.LOGGER.info("{}: no way out of the {} near {}", this.name, inLava ? "lava" : "water", player.blockPosition());
            return Result.NO_WAY_OUT;
         }
         // The push sets the player's motion itself; nothing else this tick.
         return Result.WAITING;
      }
      this.escape.reset();
      if (!baritone.isElytraReadyForTakeoff()) {
         if (!this.glided && ++this.pathWait > PATH_TICKS) {
            ArgusClient.LOGGER.warn("{}: take-off timed out waiting for Baritone's path; Baritone's status was \"{}\"", this.name, status);
            this.pathWait = 0;
            return Result.TIMED_OUT;
         }
         return Result.WAITING;
      }
      boolean openAbove = player.level().noCollision(player, player.getBoundingBox().expandTowards(0.0, HEADROOM, 0.0));
      switch (this.takeoff.next(player.onGround(), fallFlying, player.getDeltaMovement().y, openAbove)) {
         case JUMP -> {
            ArgusClient.LOGGER.info("{}: jumping to take off", this.name);
            player.jumpFromGround();
         }
         case OPEN -> {
            // What the client does for a second press of the jump key in the air.
            boolean opened = player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA) && player.tryToStartFallFlying();
            ArgusClient.LOGGER.info("{}: opening wings, opened {}, chest {}", this.name, opened, player.getItemBySlot(EquipmentSlot.CHEST).getItem());
            if (opened) {
               PacketSender.sendStartFallFlying();
            }
         }
         case COVERED -> this.retry(say, "Something is above you, so you can't take off.");
         case GAVE_UP -> {
            ArgusClient.LOGGER.info("{}: wings wouldn't open at {}; on ground {}, vertical speed {}, room above {}, status {}", this.name,
               player.blockPosition(), player.onGround(), player.getDeltaMovement().y, openAbove, status);
            this.retry(say, "Couldn't take off: the wings wouldn't open.");
         }
         case WAIT -> {
         }
      }
      return Result.WAITING;
   }

   /**
    * ElytraFly's lava take-off: with the head out of the lava and open sky above, lift and open the wings right there. The
    * server lets a glide start in lava, but Minecraft's own wing opening refuses in any liquid, so the wings are opened
    * directly and the packet sent, as ElytraFly does. Without this the player has to get clear of the lava first, which an
    * open lava sea never allows: they bob at the surface and sink back in.
    */
   private boolean openInLava(LocalPlayer player) {
      if (this.lavaOpenWait > 0) {
         this.lavaOpenWait--;
         return false;
      }
      boolean headUnder = player.getFluidHeight(net.minecraft.tags.FluidTags.LAVA) > player.getEyeHeight() + 0.1;
      boolean openSky = player.level().noCollision(player, player.getBoundingBox().expandTowards(0.0, HEADROOM, 0.0));
      if (headUnder || !openSky || !player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA) || player.isPassenger()) {
         return false;
      }
      net.minecraft.world.phys.Vec3 motion = player.getDeltaMovement();
      player.setDeltaMovement(motion.x, Math.max(motion.y, LAVA_LIFT), motion.z);
      player.startFallFlying();
      PacketSender.sendStartFallFlying();
      this.lavaOpenWait = LAVA_OPEN_RETRY_TICKS;
      ArgusClient.LOGGER.info("{}: opening wings at the lava surface at {}", this.name, player.blockPosition());
      return true;
   }

   /**
    * For a glide under lava that has nowhere to go: the way out to open air the take-off would swim, so it doesn't hold
    * still in the lava. Null when not in lava or there's no way out within reach.
    */
   public net.minecraft.world.phys.Vec3 lavaWayOut(LocalPlayer player) {
      if (!player.isInLava()) {
         return null;
      }
      net.minecraft.world.phys.Vec3 motion = this.escape.motion(player);
      if (motion != null && player.tickCount >= this.nextLavaLog) {
         this.nextLavaLog = player.tickCount + GLIDE_LOG_TICKS;
         ArgusClient.LOGGER.info("{}: blocked under lava at {}; swimming out to open air", this.name, player.blockPosition());
      }
      return motion;
   }

   private void retry(Consumer<String> say, String reason) {
      if (!this.warned) {
         say.accept(reason + " Trying again; #stop to cancel.");
      }
      this.warned = true;
      this.takeoff.pause(PAUSE_TICKS);
   }

   /**
    * The heading for a powered glide this tick: Baritone's or the route's, held up for the take-off climb, and with neither,
    * level towards the destination so the glide never goes unpowered. Logs what it's flying along every few seconds.
    */
   public float[] glideHeading(LocalPlayer player, float[] steering, net.minecraft.core.BlockPos destination, String status) {
      float[] heading = this.climb(player, steering);
      String source = this.climbing() ? "take-off climb" : steering != null ? "Baritone or its route" : "destination, no route yet";
      if (heading == null) {
         double dx = destination.getX() + 0.5 - player.getX();
         double dz = destination.getZ() + 0.5 - player.getZ();
         heading = new float[]{(float)Math.toDegrees(Math.atan2(-dx, dz)), 0.0F};
      }
      if (player.tickCount >= this.nextGlideLog) {
         this.nextGlideLog = player.tickCount + GLIDE_LOG_TICKS;
         ArgusClient.LOGGER.info("{}: powered glide along {}, yaw {}, pitch {}, at {}, Baritone status \"{}\"", this.name, source,
            Math.round(heading[0]), Math.round(heading[1]), player.blockPosition(), status);
      }
      return heading;
   }

   /** True once the wings have stayed open long enough to count as a real take-off. */
   public boolean stable() {
      return this.takeoff.stable();
   }

   /** True from a take-off until the climb is finished. */
   public boolean climbing() {
      return this.takeoff.climbing();
   }

   /**
    * The heading to glide along while climbing away from a take-off: Baritone's, or the way the player faces when Baritone
    * has none yet, pitched up until the floor is far enough below. Outside a climb the heading is returned as it is.
    */
   public float[] climb(LocalPlayer player, float[] heading) {
      if (!this.takeoff.climbing()) {
         return heading;
      }
      float yaw = heading != null ? heading[0] : player.getYRot();
      float pitch = heading != null ? heading[1] : player.getXRot();
      boolean clearBelow = player.level().noCollision(player, player.getBoundingBox().expandTowards(0.0, -ElytraTakeoff.CLIMB_CLEARANCE, 0.0));
      float climbed = this.takeoff.climbPitch(pitch, player.tickCount, clearBelow ? ElytraTakeoff.CLIMB_CLEARANCE : 0.0);
      if (!this.takeoff.climbing() && heading == null) {
         return null;
      }
      return new float[]{yaw, climbed};
   }
}
