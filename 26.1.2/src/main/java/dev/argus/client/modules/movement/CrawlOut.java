package dev.argus.client.modules.movement;

import dev.argus.client.ArgusClient;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.LocalPlayerMoveEvent;
import dev.argus.client.utils.movement.CrawlRoute;
import dev.argus.client.utils.movement.ElytraEscape;
import dev.argus.client.utils.movement.SetbackPace;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Gets a player stuck crawling under a ceiling to somewhere they can stand, for BaritoneElytra and ElytraTrip. A flight
 * that ends under a low ceiling leaves the player crawling, and Baritone can't walk from there: it only knows a
 * two-block-tall player. This crawls along {@link CrawlRoute} instead, slowing down when the server pulls the player back.
 */
public final class CrawlOut {
   public enum State {
      /** Not crawling: carry on as usual. */
      NONE,
      /** Crawling out; Baritone should be paused meanwhile. */
      CRAWLING,
      /** Crawling with nowhere to stand within reach. */
      NO_WAY
   }

   /** About vanilla sprint-crawling pace at the start; setbacks bring it down to plain crawling. */
   private static final double SPEED = 0.12;
   private static final double SLOWEST_SHARE = 0.5;
   private static final int ROUTE_TICKS = 10;
   /** Ticks between looking for a way out again after finding none. */
   private static final int RETRY_TICKS = 100;
   private static final double PULLED_BACK = 0.3;

   private final String name;
   private final SetbackPace pace = new SetbackPace(SLOWEST_SHARE);
   private List<BlockPos> route;
   private int routeAge;
   private int retryAt;
   private boolean active;
   private double lastDistance = Double.NaN;

   /** @param name how the log names the module this works for */
   public CrawlOut(String name) {
      this.name = name;
   }

   /** Whether the player is on the ground crawling because there's no room to stand, outside water. */
   public static boolean stuckCrawling(LocalPlayer player) {
      if (player.isFallFlying() || player.isInWater() || player.isInLava() || player.isPassenger() || !player.onGround()) {
         return false;
      }
      return !player.level().noCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(player.position()));
   }

   /** Once per tick during a trip. */
   public State tick(LocalPlayer player) {
      int tick = player.tickCount;
      if (!stuckCrawling(player)) {
         if (this.active) {
            ArgusClient.LOGGER.info("{}: crawled out to {}", this.name, player.blockPosition());
         }
         this.reset();
         return State.NONE;
      }
      if (this.route == null && tick < this.retryAt && this.retryAt - tick <= RETRY_TICKS) {
         return State.NO_WAY;
      }
      if (this.route == null || --this.routeAge <= 0) {
         this.routeAge = ROUTE_TICKS;
         this.route = CrawlRoute.route(player.blockPosition(), CrawlOut::cell);
         if (this.route == null) {
            this.retryAt = tick + RETRY_TICKS;
            this.active = false;
            ArgusClient.LOGGER.info("{}: crawling at {} with nowhere to stand within reach", this.name, player.blockPosition());
            return State.NO_WAY;
         }
         if (!this.active) {
            ArgusClient.LOGGER.info("{}: crawling at {}; crawling {} blocks to room to stand", this.name, player.blockPosition(), this.route.size() - 1);
         }
         this.active = true;
      }
      BlockPos end = this.route.get(this.route.size() - 1);
      double distance = Math.sqrt(player.distanceToSqr(end.getX() + 0.5, end.getY(), end.getZ() + 0.5));
      if (!Double.isNaN(this.lastDistance) && distance > this.lastDistance + PULLED_BACK) {
         this.pace.setback(tick);
      }
      this.lastDistance = distance;
      return State.CRAWLING;
   }

   /** Pushes the body along the route while {@link #tick} says CRAWLING; gravity is left as it is. */
   public void onMove(LocalPlayerMoveEvent event, LocalPlayer player) {
      if (!this.active || this.route == null || event.getMoverType() != MoverType.SELF) {
         return;
      }
      BlockPos next = this.nextStep(player);
      double dx = next.getX() + 0.5 - player.getX();
      double dz = next.getZ() + 0.5 - player.getZ();
      double length = Math.hypot(dx, dz);
      if (length < 1.0E-4) {
         return;
      }
      double speed = Math.min(length, SPEED * this.pace.share(player.tickCount));
      event.setHorizontalVelocity(dx / length * speed, dz / length * speed);
      Vec3 motion = player.getDeltaMovement();
      player.setDeltaMovement(dx / length * speed, motion.y, dz / length * speed);
   }

   /** The first route block after the one the body is in. Corners are taken one block at a time, so the body never cuts one. */
   private BlockPos nextStep(LocalPlayer player) {
      BlockPos here = player.blockPosition();
      for (int i = 0; i < this.route.size(); i++) {
         if (this.route.get(i).equals(here)) {
            return this.route.get(Math.min(i + 1, this.route.size() - 1));
         }
      }
      return this.route.get(Math.min(1, this.route.size() - 1));
   }

   public void reset() {
      this.route = null;
      this.active = false;
      this.retryAt = 0;
      this.pace.reset();
      this.lastDistance = Double.NaN;
   }

   /** Lava, and anything unloaded, count as solid. */
   static ElytraEscape.Cell cell(int x, int y, int z) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      if (level == null || y < level.getMinY() || !level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
         return ElytraEscape.Cell.SOLID;
      }
      BlockPos pos = new BlockPos(x, y, z);
      BlockState state = level.getBlockState(pos);
      if (!state.getCollisionShape(level, pos).isEmpty() || state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
         return ElytraEscape.Cell.SOLID;
      }
      return state.getFluidState().isEmpty() ? ElytraEscape.Cell.OPEN : ElytraEscape.Cell.FLUID;
   }
}
