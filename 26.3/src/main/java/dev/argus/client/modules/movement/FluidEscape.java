package dev.argus.client.modules.movement;

import dev.argus.client.ArgusClient;
import dev.argus.client.ClientAccess;
import dev.argus.client.utils.movement.ElytraEscape;
import dev.argus.client.utils.movement.SetbackPace;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Pushes the player along the shortest open route out of water or lava, to somewhere the wings can open. It starts well
 * above swimming speed, which can't leave open water at all, and slows down whenever the server pulls the player back or
 * the route stops getting shorter. Ported from Comet's FluidEscape.
 */
final class FluidEscape {
   private static final double SPEED = 0.2;
   /** The slowest push is about vanilla swimming speed, which no server should reject. */
   private static final double SLOWEST_SHARE = 0.3;
   private static final int ROUTE_TICKS = 10;
   private static final int LOOKAHEAD = 2;
   private static final double PULLED_BACK = 0.3;
   private static final int PROGRESS_TICKS = 40;
   private static final double PROGRESS_BLOCKS = 1.0;

   private final SetbackPace pace = new SetbackPace(SLOWEST_SHARE);
   private List<BlockPos> route;
   private int routeAge;
   private double lastDistance = Double.NaN;
   private double checkDistance = Double.NaN;
   private int checkTick;

   /** @return false when there's no way out within reach */
   boolean push(LocalPlayer player) {
      Vec3 motion = this.motion(player);
      if (motion == null) {
         return false;
      }
      player.setDeltaMovement(motion);
      return true;
   }

   /** This tick's move along the way out, or null when there's no way out within reach. */
   Vec3 motion(LocalPlayer player) {
      int tick = player.tickCount;
      if (this.route == null || --this.routeAge <= 0) {
         this.routeAge = ROUTE_TICKS;
         this.route = ElytraEscape.route(player.blockPosition(), FluidEscape::cell);
         if (this.route == null) {
            return null;
         }
      }
      BlockPos end = this.route.get(this.route.size() - 1);
      double distance = Math.sqrt(player.distanceToSqr(end.getX() + 0.5, end.getY(), end.getZ() + 0.5));
      if (!Double.isNaN(this.lastDistance) && distance > this.lastDistance + PULLED_BACK) {
         this.slowDown(player, "pulled back");
      }
      this.lastDistance = distance;
      if (Double.isNaN(this.checkDistance) || tick - this.checkTick >= PROGRESS_TICKS) {
         if (!Double.isNaN(this.checkDistance) && distance > this.checkDistance - PROGRESS_BLOCKS) {
            this.slowDown(player, "no progress");
         }
         this.checkDistance = distance;
         this.checkTick = tick;
      }
      BlockPos next = this.route.get(Math.min(LOOKAHEAD, this.route.size() - 1));
      Vec3 toward = new Vec3(next.getX() + 0.5 - player.getX(), next.getY() - player.getY(), next.getZ() + 0.5 - player.getZ());
      // Already at the end but still touching the surface: keep rising until clear of it.
      if (this.route.size() == 1 || toward.lengthSqr() < 1.0E-4) {
         toward = new Vec3(0.0, 1.0, 0.0);
      }
      return toward.normalize().scale(SPEED * this.pace.share(tick));
   }

   void reset() {
      this.route = null;
      this.pace.reset();
      this.lastDistance = this.checkDistance = Double.NaN;
   }

   private void slowDown(LocalPlayer player, String why) {
      if (this.pace.setback(player.tickCount)) {
         ArgusClient.LOGGER.info("Fluid escape: {} at {}, now {} b/s", why, player.blockPosition(), SPEED * this.pace.share(player.tickCount) * 20.0);
      }
   }

   static ElytraEscape.Cell cell(int x, int y, int z) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      if (level == null || y < level.getMinY() || !level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
         return ElytraEscape.Cell.SOLID;
      }
      BlockPos pos = new BlockPos(x, y, z);
      BlockState state = level.getBlockState(pos);
      if (!state.getCollisionShape(level, pos).isEmpty()) {
         return ElytraEscape.Cell.SOLID;
      }
      return state.getFluidState().isEmpty() ? ElytraEscape.Cell.OPEN : ElytraEscape.Cell.FLUID;
   }
}
