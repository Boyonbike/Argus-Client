package dev.argus.client.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.LocalPlayerTickEvent;
import dev.argus.client.events.WorldRenderEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.render.WorldBoxRenderer;
import dev.argus.client.settings.BooleanSetting;
import dev.argus.client.settings.ColorSetting;
import dev.argus.client.settings.NumberSetting;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.world.ChunkScanner;
import dev.argus.client.utils.world.WorldUtils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Plane;
import net.minecraft.util.Mth;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class TunnelsModule extends Module {
   private final Setting<Boolean> verticalsSetting = this.add(new BooleanSetting(new String("Verticals"), true));
   private final Setting<Float> lineWidthSetting = this.add(new NumberSetting<>(new String("LineWidth"), 1.0F, 0.1F, 3.0F));
   private final Setting<Float> heightSetting = this.add(new NumberSetting<>(new String("Height"), 0.1F, -1.0F, 2.0F).withUnit(new String("m")));
   private final Setting<Integer> minLengthSetting;
   private final Setting<Color> fillSetting;
   private final Setting<Color> outlineSetting;
   private static final MutableBlockPos scanPosition = new MutableBlockPos();
   private final ElapsedTimer mergeTimer;
   private final Queue<TunnelsModule.Tunnel> pendingTunnels;
   private volatile List<TunnelsModule.Tunnel> tunnels;
   private final AtomicBoolean mergeInProgress;
   private final ChunkScanner chunkScanner;
   private final TunnelsStairs stairs;

   private boolean isNonSolid(BlockPos position) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      BlockState state = level.getBlockState(position);
      return !state.isSolid();
   }

   private boolean isHorizontalTunnelCell(BlockPos position) {
      if (!this.isNonSolid(position.above()) || this.isNonSolid(position.below()) || this.isNonSolid(position.above().above())) {
         return false;
      } else {
         return this.isNonSolid(position.north())
               && this.isNonSolid(position.south())
               && this.isNonSolid(position.above().north())
               && this.isNonSolid(position.above().south())
            ? !this.isNonSolid(position.east())
               && !this.isNonSolid(position.west())
               && !this.isNonSolid(position.above().east())
               && !this.isNonSolid(position.above().west())
            : this.isNonSolid(position.east())
               && this.isNonSolid(position.west())
               && this.isNonSolid(position.above().east())
               && this.isNonSolid(position.above().west())
               && !this.isNonSolid(position.north())
               && !this.isNonSolid(position.south())
               && !this.isNonSolid(position.above().north())
               && !this.isNonSolid(position.above().south());
      }
   }

   @Override
   public void onEnable() {
      ChunkScanner capturedScanner = this.chunkScanner;
      capturedScanner.start();
      if (!this.isWorldUnavailable()) {
         capturedScanner = this.chunkScanner;
         capturedScanner.scanLoadedChunks();
      }
   }

   @Override
   public void onDisable() {
      this.tunnels.clear();
      this.stairs.clear();
      ChunkScanner scanner = this.chunkScanner;
      scanner.stop();
   }

   private boolean isVerticalTunnelCell(BlockPos position) {
      Boolean scanVerticals = this.verticalsSetting.getValue();
      if (!scanVerticals) {
         return false;
      }

      for (Direction horizontalDirection : Plane.HORIZONTAL) {
         BlockPos sidePosition = position.relative(horizontalDirection);
         ClientLevel level = ClientAccess.argusclientMinecraft.level;
         BlockState sideState = level.getBlockState(sidePosition);
         if (!sideState.isSolid()) {
            return false;
         }

         Block bambooBlock = Blocks.BAMBOO_BLOCK;
         if (sideState.is(bambooBlock)) {
            return false;
         }

         Block basaltBlock = Blocks.BASALT;
         if (sideState.is(basaltBlock)) {
            return false;
         }
      }

      for (Direction verticalDirection : Plane.VERTICAL) {
         BlockPos firstVerticalPosition = position.relative(verticalDirection);
         if (!this.isNonSolid(firstVerticalPosition)) {
            return false;
         }

         BlockPos secondVerticalPosition = firstVerticalPosition.relative(verticalDirection);
         if (!this.isNonSolid(secondVerticalPosition)) {
            return false;
         }
      }

      return true;
   }

   private boolean isTunnelCenterInChunk(TunnelsModule.Tunnel tunnel, ChunkPos chunkPosition) {
      int maxX = tunnel.maxX;
      int minX = tunnel.minX;
      float centerX = Mth.lerpInt(0.5F, minX, maxX);
      int maxZ = tunnel.maxZ;
      int minZ = tunnel.minZ;
      float centerZ = Mth.lerpInt(0.5F, minZ, maxZ);
      return centerX >= chunkPosition.getMinBlockX()
         && centerX <= chunkPosition.getMaxBlockX()
         && centerZ >= chunkPosition.getMinBlockZ()
         && centerZ <= chunkPosition.getMaxBlockZ();
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      if (!this.isWorldUnavailable()) {
         AtomicBoolean mergeFlag = this.mergeInProgress;
         if (!mergeFlag.get()) {
            ElapsedTimer capturedTimer = this.mergeTimer;
            ElapsedTimer timerForElapsedCheck = capturedTimer;
            if (timerForElapsedCheck.hasElapsed(75L)) {
               capturedTimer = this.mergeTimer;
               capturedTimer.reset();
               this.stairs.pruneFarFrom(ClientAccess.argusclientMinecraft.player.getEyePosition());
               boolean busy = true;
               AtomicBoolean busyFlag = this.mergeInProgress;
               busyFlag.set(busy);
               ArrayList<TunnelsModule.Tunnel> snapshot = new ArrayList<>(this.tunnels);
               ClientAccess.executorService.submit(() -> this.mergePendingTunnels(snapshot));
            }
         }
      }
   }

   @EventHandler
   private void onRenderWorld(WorldRenderEvent.Main event) {
      if (!this.isWorldUnavailable()) {
         for (TunnelsModule.Tunnel tunnel : this.tunnels) {
            AABB bounds = tunnel.getBounds();
            double horizontalLength = Math.max(bounds.getXsize(), bounds.getZsize());
            double length = Math.max(horizontalLength, bounds.getYsize());
            Integer minimumLength = this.minLengthSetting.getValue();
            if (!(length < minimumLength.intValue())) {
               float renderHeight = this.heightSetting.getValue();
               if (tunnel.vertical) {
                  renderHeight = (float)bounds.getYsize();
               }

               double renderTop = bounds.minY + renderHeight;
               AABB renderBounds = bounds.setMaxY(renderTop);
               if (RotationManager.isVisible(renderBounds)) {
                  PoseStack fillPoses = event.getPoseStack();
                  Setting<Color> fillOption = this.fillSetting;
                  WorldBoxRenderer.fillBox(fillPoses, renderBounds, fillOption.getValue());
                  PoseStack outlinePoses = event.getPoseStack();
                  Color outlineColor = this.outlineSetting.getValue();
                  Float lineWidth = this.lineWidthSetting.getValue();
                  WorldBoxRenderer.outlineBox(outlinePoses, renderBounds, outlineColor, lineWidth);
               }
            }
         }
      }
      if (!this.isWorldUnavailable()) {
         this.stairs.render(event, this.lineWidthSetting.getValue());
      }
   }

   private void scanChunk(ChunkPos chunkPosition, LevelChunk unusedChunk) {
      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      int firstY = level.getMinY() + 1;
      int topY = (int)WorldUtils.getTopY();
      int endY = topY - 2;

      for (int localX = 0; localX < 16; localX++) {
         for (int localZ = 0; localZ < 16; localZ++) {
            int x = chunkPosition.getMinBlockX() + localX;
            int z = chunkPosition.getMinBlockZ() + localZ;

            for (int y = firstY; y < endY; y++) {
               int blockY = y;
               MutableBlockPos positionToSet = scanPosition;
               positionToSet.set(x, blockY, z);
               MutableBlockPos nonSolidPosition = scanPosition;
               if (this.isNonSolid(nonSolidPosition)) {
                  this.stairs.scan(nonSolidPosition.immutable());
                  MutableBlockPos verticalPosition = scanPosition;
                  if (!this.isVerticalTunnelCell(verticalPosition)) {
                     MutableBlockPos horizontalPosition = scanPosition;
                     if (this.isHorizontalTunnelCell(horizontalPosition)) {
                        this.pendingTunnels.add(new TunnelsModule.Tunnel(x, y, z, x + 1, y + 1, z + 1, false));
                     }
                  } else {
                     int verticalStartY = y;

                     while (y < endY) {
                        int nextY = y + 1;
                        MutableBlockPos verticalPositionToSet = scanPosition;
                        MutableBlockPos nextVerticalPosition = verticalPositionToSet.set(x, nextY, z);
                        if (!this.isVerticalTunnelCell(nextVerticalPosition)) {
                           break;
                        }

                        y++;
                     }

                     this.pendingTunnels.add(new TunnelsModule.Tunnel(x, verticalStartY, z, x + 1, y + 1, z + 1, true));
                  }
               }
            }
         }
      }
   }

   @NotNull
   private static Set<TunnelsModule.Tunnel> collectMergedTunnels(List<TunnelsModule.Tunnel> tunnels) {
      HashSet<TunnelsModule.Tunnel> merged = new HashSet<>();

      for (int index = 0; index < tunnels.size(); index++) {
         TunnelsModule.Tunnel tunnel = tunnels.get(index);
         if (!merged.contains(tunnel)) {
            for (int candidateIndex = index + 1; candidateIndex < tunnels.size(); candidateIndex++) {
               TunnelsModule.Tunnel candidate = tunnels.get(candidateIndex);
               if (!merged.contains(candidate) && tunnel.canMergeWith(candidate)) {
                  tunnel.merge(candidate);
                  merged.add(candidate);
               }
            }
         }
      }

      return merged;
   }

   private void mergePendingTunnels(List<TunnelsModule.Tunnel> snapshot) {
      try {
         boolean changed = false;

         TunnelsModule.Tunnel pending;
         while ((pending = this.pendingTunnels.poll()) != null) {
            boolean mergedPending = false;
            Iterator<TunnelsModule.Tunnel> iterator = snapshot.iterator();

            while (true) {
               if (iterator.hasNext()) {
                  TunnelsModule.Tunnel existing = iterator.next();
                  if (!existing.canMergeWith(pending)) {
                     continue;
                  }

                  existing.merge(pending);
                  mergedPending = true;
                  changed = true;
               }

               if (!mergedPending) {
                  snapshot.add(pending);
                  changed = true;
               }
               break;
            }
         }

         if (changed) {
            Set<TunnelsModule.Tunnel> merged = collectMergedTunnels(snapshot);
            snapshot.removeAll(merged);
         }

         LocalPlayer player = ClientAccess.argusclientMinecraft.player;
         Vec3 eyePosition = player.getEyePosition();
         snapshot.removeIf(tunnel -> {
            double distanceSquared = tunnel.distanceToCenterSquared(eyePosition);
            return distanceSquared > 65536.0;
         });
         this.tunnels = snapshot;
      } catch (Exception var12) {
      } finally {
         boolean idle = false;
         AtomicBoolean busyFlag = this.mergeInProgress;
         busyFlag.set(idle);
      }
   }

   public TunnelsModule() {
      super("Tunnels", "Highlights dug-out tunnels.", ModuleCategory.BASE_HUNTING);
      String minimumLengthKey = new String("MinLength");
      this.minLengthSetting = this.add(new NumberSetting<>(minimumLengthKey, 4, 1, 16).withUnit(new String("m")));
      this.fillSetting = this.add(new ColorSetting(new String("Fill"), new Color(1358888960, true)));
      this.outlineSetting = this.add(new ColorSetting(new String("Outline"), new Color(-65536, true)));
      this.mergeTimer = new ElapsedTimer();
      this.pendingTunnels = new ConcurrentLinkedQueue<>();
      this.tunnels = Collections.emptyList();
      this.mergeInProgress = new AtomicBoolean(false);
      this.stairs = new TunnelsStairs(this, this::isNonSolid);
      this.chunkScanner = new ChunkScanner(1, this::scanChunk, (unloadedChunkPosition, unusedChunk) -> {
         BlockableEventLoop<?> client = ClientAccess.argusclientMinecraft;
         Runnable removeUnloadedTunnels = () -> {
            this.tunnels.removeIf(tunnel -> this.isTunnelCenterInChunk(tunnel, unloadedChunkPosition));
            this.stairs.forgetChunk(unloadedChunkPosition);
         };
         client.executeIfPossible(removeUnloadedTunnels);
      }, null);
   }

   private static class Tunnel {
      public int minX;
      public int minY;
      public int minZ;
      public int maxX;
      public int maxY;
      public int maxZ;
      public final boolean vertical;
      private AABB cachedBounds = null;

      public Tunnel(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean vertical) {
         this.minX = minX;
         this.minY = minY;
         this.minZ = minZ;
         this.maxX = maxX;
         this.maxY = maxY;
         this.maxZ = maxZ;
         this.vertical = vertical;
      }

      public AABB getBounds() {
         if (this.cachedBounds == null) {
            this.cachedBounds = new AABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
         }

         return this.cachedBounds;
      }

      public double distanceToCenterSquared(Vec3 position) {
         double coordinateMinimum = this.minX;
         double coordinateSpan = this.maxX - this.minX;
         double centerX = coordinateMinimum + coordinateSpan * 0.5;
         coordinateMinimum = this.minY;
         coordinateSpan = this.maxY - this.minY;
         double centerY = coordinateMinimum + coordinateSpan * 0.5;
         coordinateMinimum = this.minZ;
         coordinateSpan = this.maxZ - this.minZ;
         double centerZ = coordinateMinimum + coordinateSpan * 0.5;
         return position.distanceToSqr(centerX, centerY, centerZ);
      }

      public boolean canMergeWith(TunnelsModule.Tunnel other) {
         if (this.vertical != other.vertical) {
            return false;
         } else if (this.vertical) {
            return this.minX == other.minX && this.maxX == other.maxX
               ? this.minZ == other.minZ && this.maxZ == other.maxZ && (this.minY == other.maxY || this.maxY == other.minY)
               : false;
         } else if (this.minY == other.minY && this.maxY == other.maxY) {
            boolean touchesAlongX = (this.minX == other.maxX || this.maxX == other.minX) && this.minZ == other.minZ && this.maxZ == other.maxZ;
            return touchesAlongX || (this.minZ == other.maxZ || this.maxZ == other.minZ) && this.minX == other.minX && this.maxX == other.maxX;
         } else {
            return false;
         }
      }

      public void merge(TunnelsModule.Tunnel other) {
         int otherMinX = other.minX;
         int currentMinX = this.minX;
         this.minX = Math.min(currentMinX, otherMinX);
         int otherMinY = other.minY;
         int currentMinY = this.minY;
         this.minY = Math.min(currentMinY, otherMinY);
         int otherMinZ = other.minZ;
         int currentMinZ = this.minZ;
         this.minZ = Math.min(currentMinZ, otherMinZ);
         int otherMaxX = other.maxX;
         int currentMaxX = this.maxX;
         this.maxX = Math.max(currentMaxX, otherMaxX);
         int otherMaxY = other.maxY;
         int currentMaxY = this.maxY;
         this.maxY = Math.max(currentMaxY, otherMaxY);
         int otherMaxZ = other.maxZ;
         int currentMaxZ = this.maxZ;
         this.maxZ = Math.max(currentMaxZ, otherMaxZ);
         this.cachedBounds = null;
      }
   }
}
