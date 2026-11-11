package dev.argus.client.managers;

import dev.argus.client.ClientAccess;
import dev.argus.client.ClientContext;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.settings.options.DimensionType;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.world.BlockStateUtils;
import dev.argus.client.utils.world.HoleInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class HoleManager implements ClientAccess {
   private static final Direction[] horizontalDirections;
   /** How long the full nearby-hole scan keeps running after something last asked for the hole list. */
   private static final long SCAN_DEMAND_MILLIS = 2000L;
   private static final ScheduledExecutorService scanExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
      Thread thread = new Thread(task, "Argus hole scanner");
      thread.setDaemon(true);
      thread.setPriority(Thread.MIN_PRIORITY);
      return thread;
   });
   /** Per thread, so the scanner thread and the client thread never share a cursor. */
   private static final ThreadLocal<MutableBlockPos> CLASSIFICATION_CURSOR = ThreadLocal.withInitial(MutableBlockPos::new);
   private volatile List<HoleInfo> holes = new ArrayList<>();
   private volatile long lastDemandMillis = Long.MIN_VALUE / 2;
   private boolean inHole;

   public boolean intersectsHole(BlockPos position) {
      return this.intersectsHole(new AABB(position));
   }

   public boolean intersectsHole(AABB bounds) {
      for (HoleInfo hole : this.demandHoles()) {
         if (hole.bounds().intersects(bounds)) {
            return true;
         }
      }

      return false;
   }

   public List<HoleInfo> getHoles() {
      return this.demandHoles();
   }

   public HoleInfo findIntersectingHole(BlockPos position) {
      AABB bounds = new AABB(position);
      for (HoleInfo hole : this.demandHoles()) {
         if (hole.bounds().intersects(bounds)) {
            return hole;
         }
      }

      return null;
   }

   public boolean isInHole() {
      return this.inHole;
   }

   /** Marks the hole list as wanted, so the scan only runs while a module actually reads it. */
   private List<HoleInfo> demandHoles() {
      this.lastDemandMillis = System.currentTimeMillis();
      return this.holes;
   }

   boolean isScanDemanded() {
      return System.currentTimeMillis() - this.lastDemandMillis < SCAN_DEMAND_MILLIS;
   }

   public HoleManager() {
      ClientAccess.eventBus.subscribe(this);
      ScheduledExecutorService executor = scanExecutor;
      HoleScanTask scanTask = new HoleScanTask(this);
      TimeUnit scanTimeUnit = TimeUnit.MILLISECONDS;
      executor.scheduleWithFixedDelay(scanTask, 0L, 50L, scanTimeUnit);
   }

   static HoleInfo classifyHole(BlockPos position, Direction fromDirection) {
      MutableBlockPos classificationCursor = CLASSIFICATION_CURSOR.get();
      classificationCursor.set(position);
      MutableBlockPos cursorSnapshot = classificationCursor;
      if (BlockStateUtils.isBlockingExceptCarpet(cursorSnapshot)) {
         return null;
      }

      Direction down = Direction.DOWN;
      classificationCursor.move(down);
      MutableBlockPos floorPosition = classificationCursor;
      BlockState floorState = ClientAccess.argusclientMinecraft.level.getBlockState(floorPosition);
      if (!BlockStateUtils.isBlocking(floorState)) {
         return null;
      }

      if (floorState.getBlock() instanceof Portal) {
         return null;
      }

      if (floorState.getBlock() instanceof LiquidBlock) {
         return null;
      }

      cursorSnapshot = classificationCursor;
      Direction up = Direction.UP;
      cursorSnapshot.move(up, 2);
      cursorSnapshot = classificationCursor;
      if (BlockStateUtils.isBlocking(cursorSnapshot)) {
         return null;
      }

      Direction roofDirection = Direction.UP;
      MutableBlockPos roofCursor = classificationCursor;
      cursorSnapshot = roofCursor.move(roofDirection);
      boolean trapped = BlockStateUtils.isBlocking(cursorSnapshot);
      HoleInfo.Safety safety = BlockStateUtils.isBlastResistantUnbreakable(floorState) ? HoleInfo.Safety.SAFE : HoleInfo.Safety.UNSAFE;
      Direction doubleDirection = null;

      for (Direction direction : horizontalDirections) {
         if (fromDirection != direction.getOpposite()) {
            MutableBlockPos sideResetCursor = classificationCursor;
            cursorSnapshot = sideResetCursor.set(position);
            cursorSnapshot.move(direction);
            MutableBlockPos sidePosition = classificationCursor;
            BlockState sideState = ClientAccess.argusclientMinecraft.level.getBlockState(sidePosition);
            cursorSnapshot = classificationCursor;
            if (!BlockStateUtils.isBlocking(cursorSnapshot)) {
               if (doubleDirection != null || fromDirection != null) {
                  return null;
               }

               BlockPos adjacentBlock = position.relative(direction);
               HoleInfo adjacentHole = classifyHole(adjacentBlock, direction);
               if (adjacentHole == null) {
                  return null;
               }

               if (!adjacentHole.trapped()) {
                  trapped = false;
               }

               if (safety == HoleInfo.Safety.SAFE) {
                  safety = adjacentHole.safety();
               }

               doubleDirection = direction;
            } else {
               Block anchorBlock = Blocks.RESPAWN_ANCHOR;
               if (sideState.is(anchorBlock)) {
                  DimensionType anchorPolicy = ClientContext.worldStateManager.getDimension();
                  if (!anchorPolicy.isNether()) {
                     return null;
                  }
               }

               if (BlockStateUtils.isWeakOrInstantlyBreakable(sideState)) {
                  return null;
               }

               if (BlockStateUtils.isBlastResistantBreakable(sideState)) {
                  safety = HoleInfo.Safety.UNSAFE;
               }
            }
         }
      }

      return new HoleInfo(safety, position.immutable(), createHoleBounds(position, doubleDirection), trapped);
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      // Only the player's own block is needed here, so this no longer depends on the full scan.
      BlockPos roundedPosition = PlayerStateUtils.getLocalRoundedBlockPosition();
      this.inHole = roundedPosition != null && argusclientMinecraft.level != null && classifyHole(roundedPosition, null) != null;
   }

   List<HoleInfo> getHolesWithoutDemand() {
      return this.holes;
   }

   public void setHoles(List updatedHoles) {
      this.holes = updatedHoles;
   }

   private static AABB createHoleBounds(BlockPos position, Direction face) {
      if (face == null) {
         return new AABB(position);
      }

      AABB box = new AABB(position);
      Vec3i xVector = face.getUnitVec3i();
      double x = xVector.getX();
      Vec3i zVector = face.getUnitVec3i();
      double z = zVector.getZ();
      return box.expandTowards(x, 0.0, z);
   }

   static {
      Plane horizontalPlane = Plane.HORIZONTAL;
      horizontalDirections = horizontalPlane.stream().toArray(Direction[]::new);
   }
}
