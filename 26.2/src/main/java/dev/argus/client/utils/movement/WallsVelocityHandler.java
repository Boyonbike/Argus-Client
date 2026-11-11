package dev.argus.client.utils.movement;

import dev.argus.client.ClientAccess;
import dev.argus.client.access.LocalPlayerTickAccess;
import dev.argus.client.events.ClientPacketListenerHandleExplosionEvent;
import dev.argus.client.events.ClientPacketListenerHandleSetEntityMotionEvent;
import dev.argus.client.events.EventStage;
import dev.argus.client.events.LocalPlayerSendPositionEvent;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.ModuleRegistry;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.client.AntiCheatModule;
import dev.argus.client.modules.movement.FastWebModule;
import dev.argus.client.modules.movement.SprintModule;
import dev.argus.client.modules.movement.VelocityModule;
import dev.argus.client.utils.ElapsedTimer;
import dev.argus.client.utils.combat.BoundingBoxUtils;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.mixin.LocalPlayerAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WallsVelocityHandler extends VelocityModeHandler {
   private static SprintModule sprintModule;
   private static FastWebModule fastWebModule;
   private final List<Long> recentPositionCorrections = Collections.synchronizedList(new ArrayList<>());
   private final ElapsedTimer correctionCooldown = new ElapsedTimer();
   private boolean groundCollisionPresent;

   private float getAbsolutePositionDeltaSum() {
      double deltaSum = ClientAccess.argusclientMinecraft.player.getX() - ClientAccess.argusclientMinecraft.player.xo;
      deltaSum = deltaSum + ClientAccess.argusclientMinecraft.player.getY() - ClientAccess.argusclientMinecraft.player.yo;
      deltaSum = deltaSum + ClientAccess.argusclientMinecraft.player.getZ() - ClientAccess.argusclientMinecraft.player.zo;
      return (float)Math.abs(deltaSum);
   }

   public static boolean hasRaisedPlayerCollision() {
      return hasPlayerCollisionAtHeight(ClientAccess.argusclientMinecraft.player.getY() + 0.1);
   }

   private static boolean allOccupiedBlocksHaveCollision() {
      Set<BlockPos> positions = PlayerStateUtils.getSurroundPositions(ClientAccess.argusclientMinecraft.player);
      if (positions.size() != 4) {
         return false;
      }

      for (BlockPos position : positions) {
         if (ClientAccess.argusclientMinecraft.level.getBlockState(position).getCollisionShape(ClientAccess.argusclientMinecraft.level, position).isEmpty()) {
            return false;
         }
      }

      return true;
   }

   private boolean canApplyWallVelocity() {
      return this.isStationaryStaticMode() || this.groundCollisionPresent;
   }

   public static boolean hasUsableGroundCollision() {
      return !PlayerStateUtils.isFallFlying() && hasRaisedPlayerCollision();
   }

   private boolean isStationaryStaticMode() {
      return this.velocityModule.staticSetting.getValue() && !PlayerStateUtils.isFallFlying() && this.getAbsolutePositionDeltaSum() < 0.01;
   }

   public WallsVelocityHandler(VelocityModule velocityModule) {
      super(velocityModule);
   }

   private void applyYawAndPitch(LocalPlayerSendPositionEvent event) {
      LocalPlayerAccessor access = (LocalPlayerAccessor)ClientAccess.argusclientMinecraft.player;
      float previousYaw = access.lastYaw();
      boolean moving = MovementPhysics.isLocalPlayerMoving();
      boolean yawWithinThreshold = Mth.degreesDifferenceAbs(event.getYaw(), previousYaw) <= 2.0F;
      if (sprintModule.isToggled() && sprintModule.shouldDeferToVanillaSprinting() && !allOccupiedBlocksHaveCollision() && yawWithinThreshold) {
         moving = false;
      }

      if (!moving) {
         if (this.isStationaryStaticMode()) {
            access.argusclient$setTicksSinceLastPositionPacketSent(Math.min(access.argusclient$getTicksSinceLastPositionPacketSent(), 18));
         }

         event.setYaw(previousYaw);
      }

      event.setPitch(89.0F);
   }

   @Override
   public void onEntityMotion(ClientPacketListenerHandleSetEntityMotionEvent event) {
      int entityId = event.getEntityId();
      LocalPlayer player = ClientAccess.argusclientMinecraft.player;
      if (entityId == player.getId() && this.shouldCancelVelocity()) {
         Vec3 incomingVelocity = event.getVelocity();
         double verticalSpeed = Math.abs(incomingVelocity.y);
         if (verticalSpeed < 0.0788 && incomingVelocity.x == 0.0 && incomingVelocity.z == 0.0) {
            return;
         }

         event.setVelocity(0.0F, 0.0F, 0.0F);
         event.cancel();
      }
   }

   @Override
   public void onExplosion(ClientPacketListenerHandleExplosionEvent event) {
      if (this.shouldCancelVelocity()) {
         event.setVelocityX(0.0F);
         event.setVelocityY(0.0F);
         event.setVelocityZ(0.0F);
      }
   }

   private void applyRandomizedPitch(LocalPlayerSendPositionEvent event) {
      LocalPlayerAccessor access = (LocalPlayerAccessor)ClientAccess.argusclientMinecraft.player;
      access.argusclient$setTicksSinceLastPositionPacketSent(20);
      LocalPlayerTickAccess tickAccess = (LocalPlayerTickAccess)ClientAccess.argusclientMinecraft.player;
      tickAccess.argusclient$updateServer((float)Math.random(), (float)Math.random());
      float jitter = AntiCheatModule.ROTATION_JITTER_STEP * (ClientAccess.argusclientMinecraft.player.tickCount % 6 - 3);
      event.setPitch(Mth.clamp(event.getPitch() + jitter, -90.0F, 90.0F));
   }

   public boolean shouldCancelVelocity() {
      return fastWebModule.isToggled() && fastWebModule.isGrimEnabled() && PlayerStateUtils.isInCobweb(ClientAccess.argusclientMinecraft.player)
         ? true
         : this.correctionCooldown.hasElapsed(150L) && this.canApplyWallVelocity();
   }

   public static boolean hasCollisionAtHeight(Player player, double height) {
      AABB bounds = player.getBoundingBox();
      bounds = bounds.deflate(BoundingBoxUtils.blockCornerInset, 0.0, BoundingBoxUtils.blockCornerInset);
      bounds = bounds.setMinY(height);
      AABB collisionBounds = bounds.setMaxY(height + 0.1);
      return BlockPos.betweenClosedStream(collisionBounds).anyMatch(position -> intersectsCollision(collisionBounds, position));
   }

   private static boolean intersectsCollision(AABB bounds, BlockPos position) {
      BlockStateBase state = ClientAccess.argusclientMinecraft.level.getBlockState(position);
      VoxelShape shape = state.getCollisionShape(ClientAccess.argusclientMinecraft.level, position);
      return !shape.isEmpty() && bounds.intersects(shape.bounds().move(position));
   }

   @Override
   public void onReceivePacket(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         this.recentPositionCorrections.add(System.currentTimeMillis());
      }
   }

   @Override
   public void onSendPosition(LocalPlayerSendPositionEvent event) {
      if (EventStage.Post != event.getStage()
         && !(ClientAccess.argusclientMinecraft.gui.screen() instanceof AbstractContainerScreen)
         && this.canApplyWallVelocity()
         && this.velocityModule.reduceSetting.getValue()) {
         if (RotationManager.isSilentRotationMode()) {
            this.applyRandomizedPitch(event);
         } else {
            this.applyYawAndPitch(event);
         }
      }
   }

   public static boolean hasPlayerCollisionAtHeight(double height) {
      return hasCollisionAtHeight(ClientAccess.argusclientMinecraft.player, height);
   }

   @Override
   public void onTick() {
      this.groundCollisionPresent = hasUsableGroundCollision();
      this.recentPositionCorrections.removeIf(receivedAt -> System.currentTimeMillis() > receivedAt + 500L);
      if (this.recentPositionCorrections.size() > 2) {
         this.correctionCooldown.reset();
      }
   }

   public static void bindModules(ModuleRegistry modules) {
      sprintModule = (SprintModule)modules.getModule(SprintModule.class);
      fastWebModule = (FastWebModule)modules.getModule(FastWebModule.class);
   }
}
