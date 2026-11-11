package dev.argus.client.modules.misc;

import dev.argus.client.ClientAccess;
import dev.argus.client.events.EventHandler;
import dev.argus.client.events.PacketEvent;
import dev.argus.client.managers.RotationManager;
import dev.argus.client.modules.Module;
import dev.argus.client.modules.ModuleCategory;
import dev.argus.client.settings.EnumSetting;
import dev.argus.client.settings.NamedEntry;
import dev.argus.client.settings.Setting;
import dev.argus.client.utils.entity.PlayerStateUtils;
import dev.argus.client.utils.movement.MovementPhysics;
import dev.argus.mixin.PositionMoveRotationAccessor;
import dev.argus.mixin.ServerboundMovePlayerPacketAccessor;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public class NoRotateModule extends Module {
   private final Setting<NoRotateModule.InBlocksChoice> inBlocksSetting;
   private float spoofYaw;
   private float spoofPitch;
   private boolean spoofPending;

   private boolean isInsideBlockingShape() {
      LocalPlayer ladderPlayer = ClientAccess.argusclientMinecraft.player;
      if (ladderPlayer.isSuppressingSlidingDownLadder()) {
         return false;
      }

      LocalPlayer cobwebPlayer = ClientAccess.argusclientMinecraft.player;
      if (PlayerStateUtils.isInCobweb(cobwebPlayer)) {
         return false;
      }

      ClientLevel level = ClientAccess.argusclientMinecraft.level;
      LocalPlayer blockPlayer = ClientAccess.argusclientMinecraft.player;
      BlockPos blockPosition = blockPlayer.blockPosition();
      BlockState blockState = level.getBlockState(blockPosition);
      if (blockState.getBlock() instanceof FallingBlock) {
         return false;
      }

      ClientLevel collisionLevel = ClientAccess.argusclientMinecraft.level;
      BlockPos collisionPosition = BlockPos.ZERO;
      VoxelShape collisionShape = blockState.getCollisionShape(collisionLevel, collisionPosition);
      if (collisionShape.isEmpty()) {
         return false;
      }

      LocalPlayer heightPlayer = ClientAccess.argusclientMinecraft.player;
      double playerY = heightPlayer.getY();
      LocalPlayer floorPlayer = ClientAccess.argusclientMinecraft.player;
      double floorY = floorPlayer.getY();
      double heightWithinBlock = playerY - Math.floor(floorY);
      if (heightWithinBlock >= collisionShape.bounds().maxY) {
         return false;
      }

      AABB localShapeBounds = collisionShape.bounds();
      LocalPlayer positionPlayer = ClientAccess.argusclientMinecraft.player;
      BlockPos shapePosition = positionPlayer.blockPosition();
      AABB worldShapeBounds = localShapeBounds.move(shapePosition);
      LocalPlayer boundsPlayer = ClientAccess.argusclientMinecraft.player;
      AABB playerBounds = boundsPlayer.getBoundingBox();
      AABB inflatedPlayerBounds = playerBounds.inflate(-0.05);
      return worldShapeBounds.intersects(inflatedPlayerBounds);
   }

   @EventHandler
   private void onPacketSend(PacketEvent.Send event) {
      if (event.getPacket() instanceof ServerboundMovePlayerPacket movementPacket && this.spoofPending) {
         ServerboundMovePlayerPacketAccessor packetAccessor = (ServerboundMovePlayerPacketAccessor)movementPacket;
         packetAccessor.setYaw(this.spoofYaw);
         packetAccessor.setPitch(this.spoofPitch);
         this.spoofPending = false;
      }

      Setting<NoRotateModule.InBlocksChoice> inBlocksSettingSnapshot = this.inBlocksSetting;
      if (inBlocksSettingSnapshot.getValue() == NoRotateModule.InBlocksChoice.CANCEL
         && !MovementPhysics.isLocalPlayerMoving()
         && event.getPacket() instanceof ServerboundMovePlayerPacket
         && this.isInsideBlockingShape()) {
         event.cancel();
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket positionPacket) {
         if (!this.isWorldUnavailable()) {
            int playerTicks = ClientAccess.argusclientMinecraft.player.tickCount;
            if (playerTicks > 20) {
               Minecraft minecraft = ClientAccess.argusclientMinecraft;
               if (!minecraft.isLocalServer()) {
                  LocalPlayer positionPlayer = ClientAccess.argusclientMinecraft.player;
                  PositionMoveRotation playerPosition = PositionMoveRotation.of(positionPlayer);
                  PositionMoveRotation serverChange = positionPacket.change();
                  Set<Relative> relativeFlags = positionPacket.relatives();
                  PositionMoveRotation absolutePosition = PositionMoveRotation.calculateAbsolute(playerPosition, serverChange, relativeFlags);
                  LocalPlayer yawPlayer = ClientAccess.argusclientMinecraft.player;
                  float preservedYaw = yawPlayer.getYRot();
                  LocalPlayer pitchPlayer = ClientAccess.argusclientMinecraft.player;
                  float preservedPitch = pitchPlayer.getXRot();
                  if (positionPacket.relatives().contains(Relative.Y_ROT)) {
                     preservedYaw = 0.0F;
                  }

                  if (positionPacket.relatives().contains(Relative.X_ROT)) {
                     preservedPitch = 0.0F;
                  }

                  ((PositionMoveRotationAccessor)(Object)positionPacket.change()).argusclient$setYaw(preservedYaw);
                  ((PositionMoveRotationAccessor)(Object)positionPacket.change()).argusclient$setPitch(preservedPitch);
                  Setting<NoRotateModule.InBlocksChoice> inBlocksSettingSnapshot = this.inBlocksSetting;
                  if (inBlocksSettingSnapshot.getValue() == NoRotateModule.InBlocksChoice.SPOOF || RotationManager.isMovementSyncEnabled()) {
                     this.spoofYaw = absolutePosition.yRot();
                     this.spoofPitch = absolutePosition.xRot();
                     this.spoofPending = true;
                  }
               }
            }
         }
      }
   }

   public NoRotateModule() {
      super("NoRotate", "Cancels the rotations server sets for you.", ModuleCategory.MISC);
      NoRotateModule.InBlocksChoice inBlocksDefault = NoRotateModule.InBlocksChoice.OFF;
      this.inBlocksSetting = this.add(new EnumSetting<>("InBlocks", inBlocksDefault));
   }

   enum InBlocksChoice implements NamedEntry {
      OFF("Off"),
      CANCEL("Cancel"),
      SPOOF("Spoof");

      private final String displayName;

      @Override
      public String getName() {
         return this.displayName;
      }

      InBlocksChoice(String displayName) {
         this.displayName = displayName;
      }
   }
}
